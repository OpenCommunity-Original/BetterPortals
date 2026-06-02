package com.lauriethefish.betterportals.bukkit.command.framework;

import com.lauriethefish.betterportals.bukkit.command.framework.annotations.*;
import com.lauriethefish.betterportals.bukkit.config.MessageConfig;
import com.lauriethefish.betterportals.bukkit.player.IPlayerData;
import com.lauriethefish.betterportals.bukkit.player.IPlayerDataManager;
import com.lauriethefish.betterportals.shared.logging.Logger;
import com.lauriethefish.betterportals.shared.util.ReflectionUtil;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

public class SubCommand implements ICommand {
    private final Object instance;
    private final Method method;
    private final MessageConfig messageConfig;
    private final Logger logger;
    private final IPlayerDataManager playerDataManager;

    private static final Map<Class<?>, Method> valueOfCache = new HashMap<>();

    private boolean requiresPlayer = false;
    private boolean usePlayerData; // Whether or not we'll automatically fetch the IPlayerData for the first argument
    private String[] requiredPermissions = new String[0];
    private Argument[] arguments = new Argument[0];
    private Class<?>[] argumentTypes;
    @Getter private String description = ""; // Default is no description

    @Getter private String usage;

    public String getArgumentsUsage() {
        StringBuilder builder = new StringBuilder();
        int argTypeIndex = 0;
        for(Argument argument : arguments) {
            if(argumentTypes[argTypeIndex].equals(Vector.class)) {
                builder.append(String.format(" <%sX> <%sY> <%sZ>", argument.name(), argument.name(), argument.name()));
            }   else    {
                boolean required = argument.defaultValue().equals("");
                if(required) {
                    builder.append(String.format(" <%s>", argument.name()));
                }   else    {
                    builder.append(String.format(" [%s]", argument.name()));
                }
            }
            argTypeIndex++;
        }
        return builder.toString().trim();
    }

    SubCommand(Object instance, Method method, MessageConfig messageConfig, Logger logger, IPlayerDataManager playerDataManager) {
        this.instance = instance;
        this.method = method;
        this.messageConfig = messageConfig;
        this.logger = logger;
        this.playerDataManager = playerDataManager;

        if(method.getReturnType() != boolean.class) {
            throw new InvalidCommandException("Command annotated methods must return a boolean");
        }

        loadFromMethod();
        checkArgTypes();
        generateUsage();
    }

    // Uses the command annotations to load the command from this method
    private void loadFromMethod() {
        // The first parameter is the command sender, so we skip it
        argumentTypes = Arrays.copyOfRange(method.getParameterTypes(), 1, method.getParameterCount());

        boolean commandAnnotationFound = false;
        for (Annotation annotation : method.getAnnotations()) {
            if (annotation instanceof Command) {
                commandAnnotationFound = true;
            } else if (annotation instanceof RequiresPlayer) {
                requiresPlayer = true;
            } else if (annotation instanceof RequiresPermissions) {
                requiredPermissions = ((RequiresPermissions) annotation).value();
            } else if (annotation instanceof Arguments) { // Otherwise, multiple arguments are wrapped in here
                arguments = ((Arguments) annotation).value();
            } else if(annotation instanceof Argument) { // If there is one argument then it isn't wrapped
                arguments = new Argument[]{(Argument) annotation};
            } else if (annotation instanceof Description) {
                description = ((Description) annotation).value();
            }
        }

        // Sanity checking
        if (!commandAnnotationFound) {
            throw new InvalidCommandException("Command methods require the command annotation");
        }
    }

    // Checks that the method has the correct number of parameters and a CommandSender as its first argument
    private void checkArgTypes() {
        Parameter[] methodParams = method.getParameters();
        if(methodParams.length != arguments.length + 1) {
            throw new InvalidCommandException("Incorrect number of arguments on command method. Commands must have 1 argument for the sender and one argument per annotated argument");
        }

        Class<?> firstParamType = methodParams[0].getType();

        boolean isFirstArgValid = true;
        if(requiresPlayer) {
            if(firstParamType.isAssignableFrom(IPlayerData.class)) {
                usePlayerData = true; // Command methods annotated with a player requirement can also use IPlayerData as their first argument
            }   else if(!firstParamType.isAssignableFrom(Player.class)) {
                isFirstArgValid = false; // Otherwise, set it to invalid if we can't assign it from a player
            }
        }   else if(!firstParamType.isAssignableFrom(CommandSender.class)) { // Non player requirement annotated commands must just take a CommandSender
            isFirstArgValid = false;
        }

        if(!isFirstArgValid) {
            throw new InvalidCommandException("The first argument for a command must be a CommandSender. (or a Player/IPlayerData if annotated with a player requirement)");
        }
    }

    private void generateUsage() {
        StringBuilder builder = new StringBuilder();
        int argTypeIndex = 0;
        for(Argument argument : arguments) {
            if(argumentTypes[argTypeIndex].equals(Vector.class)) {
                builder.append(String.format(" <%sX> <%sY> <%sZ>", argument.name(), argument.name(), argument.name()));
            }   else    {
                boolean required = argument.defaultValue().equals("");
                // Use square brackets for optional arguments, comparator signs for required ones
                if(required) {
                    builder.append(String.format(" <%s>", argument.name()));
                }   else    {
                    builder.append(String.format(" [%s]", argument.name()));
                }
            }
            argTypeIndex++;
        }
        // Avoid adding the colon for blank descriptions
        if(!description.isEmpty()) {
            builder.append(": ");
            builder.append(description);
        }
        usage = builder.toString().trim();
    }

    private Vector parseVector(CommandSender sender, String x, String y, String z) throws CommandException {
        logger.fine("Attempting to parse vector from %s, %s, %s", x, y, z);
        try {
            return normalizeLocalCoordinates(x, y, z, (sender instanceof Player player) ? player.getLocation() : null);
        }   catch(NumberFormatException ignored) {
            throw new CommandException(String.format(messageConfig.getErrorMessage("invalidCoordinates"), x, y, z));
        }
    }

    private World parseWorld(CommandSender sender, String worldName) throws CommandException {
        World world;
        if(sender instanceof Player player && "<local>".equals(worldName)) {
            world = player.getWorld();
        }   else    {
            world = Bukkit.getWorld(worldName);
        }

        if(world == null) {
            throw new CommandException(messageConfig.getErrorMessage("noWorldExistsWithGivenName").replace("{name}", worldName));
        }

        return world;
    }

    private Vector normalizeLocalCoordinates(
            @NotNull String x,
            @NotNull String y,
            @NotNull String z,
            @Nullable Location playerLocation)
        throws CommandException,
            NumberFormatException {
        int coordinateType = 0; // 0: X, 1: Y, 2: Z
        Vector normalizedLocation = new Vector(0, 0, 0); // Will store the converted location

        // Iterate through coordinates (X, Y, Z)
        for (String coordinateToConvert : new String[] {x, y, z}) {

            // If it is just a ~ or a ^, then give it a zero at the end to fix conversions
            if (coordinateToConvert.replace("~", "").replace("^", "").length() == 0) {
                coordinateToConvert += '0';
            }

            // Local Coordinate
            if (coordinateToConvert.charAt(0) == '~') {
                if(playerLocation == null) {
                    throw new CommandException(messageConfig.getErrorMessage("cannotUseRelativeCoordinatesWithoutPlayer"));
                }

                // Basically just add the coordinate to the respective player location depending on whether it is X, Y or Z
                switch (coordinateType) {
                    case 0 -> normalizedLocation.setX(playerLocation.getBlockX() + Integer.parseInt(coordinateToConvert.replace("~", "")));
                    case 1 -> normalizedLocation.setY(playerLocation.getBlockY() + Integer.parseInt(coordinateToConvert.replace("~", "")));
                    case 2 -> normalizedLocation.setZ(playerLocation.getBlockZ() + Integer.parseInt(coordinateToConvert.replace("~", "")));
                }

                // Caret Notation Local Coordinate
            } else if (coordinateToConvert.charAt(0) == '^') {
                if(playerLocation == null) {
                    throw new CommandException(messageConfig.getErrorMessage("cannotUseRelativeCoordinatesWithoutPlayer"));
                }

                // Y is handled like standard local coordinate
                if (coordinateType == 1) {
                    normalizedLocation.setY( playerLocation.getBlockY() + Integer.parseInt(coordinateToConvert.replace("^", "")) );
                    continue;
                }

                // Get the direction the player is facing
                float playerYaw = Location.normalizeYaw(playerLocation.getYaw());

                // This code basically:
                // - Checks the direction the player is facing
                // - Adds to the corresponding coordinate depending on that
                //
                // Note that Y is omitted as it is handled above
                if (playerYaw >= -45 && playerYaw < 45) {
                    // Facing +z
                    switch (coordinateType) {
                        case 0 -> normalizedLocation.setX(playerLocation.getBlockX() + Integer.parseInt(coordinateToConvert.replace("^", "")));
                        case 2 -> normalizedLocation.setZ(playerLocation.getBlockZ() + Integer.parseInt(coordinateToConvert.replace("^", "")));
                    }
                } else if (playerYaw >= 45 && playerYaw < 135) {
                    // Facing -x
                    switch (coordinateType) {
                        case 0 -> normalizedLocation.setZ(playerLocation.getBlockZ() + Integer.parseInt(coordinateToConvert.replace("^", "")));
                        case 2 -> normalizedLocation.setX(playerLocation.getBlockX() - Integer.parseInt(coordinateToConvert.replace("^", "")));
                    }
                } else if ((playerYaw >= 135 && playerYaw <= 180) || (playerYaw >= -180 && playerYaw < -135)) {
                    // Facing -z
                    switch (coordinateType) {
                        case 0 -> normalizedLocation.setX(playerLocation.getBlockX() - Integer.parseInt(coordinateToConvert.replace("^", "")));
                        case 2 -> normalizedLocation.setZ(playerLocation.getBlockZ() - Integer.parseInt(coordinateToConvert.replace("^", "")));
                    }
                } else if (playerYaw >= -135 && playerYaw < -45) {
                    // Facing +x
                    switch (coordinateType) {
                        case 0 -> normalizedLocation.setZ(playerLocation.getBlockZ() - Integer.parseInt(coordinateToConvert.replace("^", "")));
                        case 2 -> normalizedLocation.setX(playerLocation.getBlockX() + Integer.parseInt(coordinateToConvert.replace("^", "")));
                    }
                }

                // Absolute coordinates
            } else {
                // Set the location to the coordinate
                switch (coordinateType) {
                    case 0 -> normalizedLocation.setX(Integer.parseInt(coordinateToConvert));
                    case 1 -> normalizedLocation.setY(Integer.parseInt(coordinateToConvert));
                    case 2 -> normalizedLocation.setZ(Integer.parseInt(coordinateToConvert));
                }
            }

            // Increment the coordinate type in the order: X, Y, Z
            coordinateType++;
        }

        // Return the new location
        return normalizedLocation;
    }

    // Attempts to parse an argument as various primitive types before attempting to call a static valueOf method on its class
    private Object parseArgument(CommandSender sender, Class<?> type, String argument) throws CommandException {
        logger.fine("Attempting to parse string \"%s\" as type %s", argument, type.getName());
        try {
            if(type == String.class) {
                return argument;
            }   else if(type == World.class) {
                return parseWorld(sender, argument);
            }   else if(type == int.class) {
                return Integer.parseInt(argument);
            }   else if(type == short.class) {
                return Short.parseShort(argument);
            }   else if(type == long.class) {
                return Long.parseLong(argument);
            }   else if(type == byte.class) {
                return Byte.parseByte(argument);
            }   else if(type == boolean.class) {
                if(argument.equalsIgnoreCase("true") || argument.equalsIgnoreCase("yes")) {
                    return true;
                }
                if(argument.equalsIgnoreCase("false") || argument.equalsIgnoreCase("no")) {
                    return false;
                }
                throw new CommandException(messageConfig.getErrorMessage("invalidBoolean").replace("{arg}", argument));
            }   else if(type.isPrimitive()) {
                throw new InvalidCommandException("Unknown primitive type on command argument");
            }   else    {
                return runValueOfMethod(type, argument);
            }

        }   catch(IllegalArgumentException ex) {
            throw new CommandException(messageConfig.getErrorMessage("invalidArgs"), ex);
        }
    }

    /**
     * Caches and runs a parsing method for the type, taking the given string argument.
     * @param type Type to parse
     * @param argument Argument to pass to the <code>valueOf</code> method
     * @return The parsed object
     * @throws CommandException If parsing throws an {@link IllegalArgumentException}
     */
    private Object runValueOfMethod(Class<?> type, String argument) throws CommandException {
        Method method = valueOfCache.get(type);
        if(method == null) {
            method = ReflectionUtil.findMethod(type, "valueOf", String.class);
            valueOfCache.put(type, method);
        }

        try {
            return ReflectionUtil.invokeMethod(null, method, argument);
        }   catch(IllegalArgumentException ex) {
            throw new CommandException(messageConfig.getErrorMessage("invalidArgs"), ex);
        }
    }

    private void displayUsage(String pathToCall) throws CommandException {
        throw new CommandException("Usage: " + pathToCall + usage);
    }

    public boolean hasPermissions(CommandSender sender) {
        for (String permission : requiredPermissions) {
            if (!sender.hasPermission(permission)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean execute(CommandSender sender, String pathToCall, String[] args) throws CommandException {
        if(!hasPermissions(sender)) {
            throw new CommandException(messageConfig.getErrorMessage("notEnoughPerms"));
        }

        if(requiresPlayer && !(sender instanceof Player)) {
            throw new CommandException(messageConfig.getErrorMessage("mustBePlayer"));
        }

        // Fetch the IPlayerData if required, or just add the sender
        List<Object> parsedArgs = new ArrayList<>();
        if(usePlayerData) {
            IPlayerData playerData = playerDataManager.getPlayerData((Player) sender);
            if(playerData == null) {
                throw new IllegalStateException("Player called command without registered player data");
            }
            parsedArgs.add(playerData);
        }   else {
            parsedArgs.add(sender);
        }


        int argumentIdx = 0;
        int argumentTypesIdx = 0;
        for(Argument argument : arguments) {
            Class<?> argumentType = argumentTypes[argumentTypesIdx];
            boolean isVector = argumentType.equals(Vector.class);
            int argsRequired = isVector ? 3 : 1;

            boolean wasEntered = argumentIdx + argsRequired - 1 < args.length;
            boolean isRequired = isVector || argument.defaultValue().equals("");
            if(isRequired && !wasEntered) {
                displayUsage(pathToCall);
            }

            // Attempt to parse each argument
            if(isVector) {
                parsedArgs.add(parseVector(
                        sender,
                        args[argumentIdx],
                        args[argumentIdx + 1],
                        args[argumentIdx + 2])
                );
            }   else    {
                String givenValue = wasEntered ? args[argumentIdx] : argument.defaultValue();
                parsedArgs.add(parseArgument(sender, argumentType, givenValue));
            }

            argumentIdx += argsRequired;
            argumentTypesIdx++;
        }

        // Call the method, making sure to rethrow CommandExceptions
        try {
            return (boolean) method.invoke(instance, parsedArgs.toArray());
        }   catch(IllegalAccessException ex) {
            throw new InvalidCommandException("Command annotated methods must be public");
        }   catch(InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if(cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }   else if(cause instanceof CommandException){
                throw (CommandException) cause;
            }   else    {
                // Box it as a RuntimeException, then Bukkit will tell the player what happened via an internal error message
                throw new RuntimeException(cause);
            }
        }
    }
}
