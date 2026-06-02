package org.envel.betterportals.shared.util;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

public class ReflectionUtil {

    /**
     * Uses reflection to find the class with the given name (equivalent to {@link Class#forName})
     * @param name Fully qualified name of the class
     * @return Class with the given name
     * @throws ReflectionException If the class does not exist
     */
    public static Class<?> findClass(String name) {
        try  {
            return Class.forName(name);
        }   catch(ClassNotFoundException ex) {
            throw new ReflectionException(ex);
        }
    }

    /**
     * Finds a method with the given name and parameter types on the given class. (declared method)
     * @param klass Class to find the method on
     * @param name Name of the method
     * @param paramTypes Types of the parameters of the method
     * @return The found method
     * @exception ReflectionException If the method does not exist
     */
    public static Method findMethod(Class<?> klass, String name, Class<?>... paramTypes) {
        try {
            Method method = klass.getDeclaredMethod(name, paramTypes);
            method.setAccessible(true);
            return method;
        }   catch(NoSuchMethodException ex) {
            throw new ReflectionException(ex);
        }
    }

    /**
     * Finds a field on the given class with the given name
     * @param klass The class to find the method in
     * @param name The name of the field
     * @return The found field
     * @exception ReflectionException If the field does not exist
     */
    public static Field findField(Class<?> klass, String name) {
        return findField(klass, name, null);
    }

    /**
     * Finds a field on the given class with the given name
     * @param klass The class to find the method in
     * @param name The name of the field
     * @param verifyType The type of the field for verification
     * @return The found field
     * @exception ReflectionException If the field does not exist, or its type does not match the type given.
     */
    public static Field findField(Class<?> klass, String name, @Nullable Class<?> verifyType) {
        try {
            Field field = klass.getDeclaredField(name);
            if(verifyType != null && !field.getType().equals(verifyType)) {
                throw new ReflectionException("Field with name " + name + " on class " + klass + " did not match expected type of " + verifyType);
            }

            field.setAccessible(true);
            return field;
        }   catch(NoSuchFieldException ex) {
            throw new ReflectionException(ex);
        }
    }

    /**
     * Finds a field with the given type in the given class.
     * @param klass Class containing the field
     * @param type Type of the field
     * @return The found field
     * @exception ReflectionException If no fields with the given type exist on the class, or if multiple exist with the given type
     */
    public static Field findFieldByType(Class<?> klass, Class<?> type) {
        Field found = null;

        for(Field field : klass.getDeclaredFields()) {
            if(field.getType().equals(type)) {
                if(found != null) {
                    throw new ReflectionException("Multiple instances of field with type " + type + " exist in class " + klass);
                }

                found = field;
            }
        }

        if(found == null) {
            throw new ReflectionException("No field with type " + type + " exists in class " + klass);
        }
        found.setAccessible(true);
        return found;
    }

    /**
     * Finds a method on the given class with the given parameter types
     * @param klass The class containing the method
     * @param paramTypes The types of the method's parameters
     * @return The found method
     * @exception ReflectionException If no method exists with the given types, or multiple methods exist with the given types
     */
    public static Method findMethodByParamTypes(Class<?> klass, Class<?>... paramTypes) {
        return findMethodByTypes(klass, null, 0, 0, paramTypes);
    }

    /**
     * Finds a method on the given class with the given parameter types and return type.
     * @param klass The class containing the method
     * @param paramTypes The types of the method's parameters
     * @param returnType The return type of the method, will be checked if non-null, ignored if null
     * @return The found method
     * @exception ReflectionException If no method exists with the given types, or multiple methods exist with the given types
     */
    public static Method findMethodByTypes(Class<?> klass, @Nullable Class<?> returnType, Class<?>... paramTypes) {
        return findMethodByTypes(klass, returnType, 0, 0, paramTypes);
    }

    /**
     * Finds a method on the given class with no parameters and the given return type and modifiers.
     * @param klass The class containing the method
     * @param returnType The return type of the method
     * @param modifierMask Mask to decide which parts of the modifiers will be checked
     * @param modifiersValue The expected value of the modifiers once AND-ed through the mask
     * @exception ReflectionException If no method exists with the given bounds, or multiple methods exist with the given types
     */
    public static Method findMethodByTypes(Class<?> klass, Class<?> returnType, int modifierMask, int modifiersValue) {
        return findMethodByTypes(klass, returnType, modifierMask, modifiersValue, new Class[0]);
    }

    /**
     * Finds a method on the given class with the given parameter types and other bounds.
     * @param klass The class containing the method
     * @param paramTypes The types of the method's parameters
     * @param returnType The return type of the method, will be checked if non-null, ignored if null
     * @param modifierMask Mask to decide which parts of the modifiers will be checked
     * @param modifiersValue The expected value of the modifiers once AND-ed through the mask
     * @return The found method
     * @exception ReflectionException If no method exists with the given bounds, or multiple methods exist with the given types
     */
    public static Method findMethodByTypes(Class<?> klass, @Nullable Class<?> returnType, int modifierMask, int modifiersValue, Class<?>[] paramTypes) {
        Method found = null;

        for(Method method : klass.getDeclaredMethods()) {

            boolean matchesModifiers = (method.getModifiers() & modifierMask) == modifiersValue;

            if(matchesModifiers && (returnType == null || method.getReturnType().equals(returnType)) && Arrays.equals(method.getParameterTypes(), paramTypes)) {
                if(found != null) {
                    throw new ReflectionException("Multiple instances of method existed with given types in " + klass);
                }

                found = method;
            }
        }

        if(found == null) {
            throw new ReflectionException("No method existed with given types in " + klass);
        }
        found.setAccessible(true);
        return found;
    }

    /**
     * Finds a constructor on the given class with the given parameter types
     * @param klass The class to check for a constructor on
     * @param paramTypes The parameter types of the constructor
     * @return The found constructor
     * @throws ReflectionException If no constructor exists with the given parameter types
     */
    public static Constructor<?> findConstructor(Class<?> klass, Class<?>... paramTypes) {
        try {
            Constructor<?> ctor = klass.getDeclaredConstructor(paramTypes);
            ctor.setAccessible(true);
            return ctor;
        }   catch(NoSuchMethodException ex) {
            throw new ReflectionException(ex);
        }
    }

    /**
     * Invokes the given constructor to give a new object instance
     * @param ctor Constructor to invoke
     * @param args Arguments for the constructor
     * @return The newly created object
     * @throws ReflectionException Wraps any reflection related exceptions
     */
    public static Object invokeConstructor(Constructor<?> ctor, Object... args) {
        try {
            return ctor.newInstance(args);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException ex) {
            throw new ReflectionException(ex);
        }
    }

    /**
     * Gets a field on the given object
     * @param obj The instance to get the field of, can be null for a static field
     * @param field The field to get
     * @return The value of the field
     * @throws ReflectionException Wraps any reflection related exceptions
     */
    public static Object getField(@Nullable Object obj, Field field) {
        try {
            return field.get(obj);
        }   catch(IllegalAccessException ex) {
            throw new ReflectionException(ex);
        }
    }

    /**
     * Sets a field on the given object
     * @param obj The object to set the field of, can be null for a static field
     * @param field The field to set
     * @param value The value to set
     * @throws ReflectionException Wraps any reflection related exceptions
     */
    public static void setField(@Nullable Object obj, Field field, Object value) {
        try {
            field.set(obj, value);
        }   catch(IllegalAccessException ex) {
            throw new ReflectionException(ex);
        }
    }

    /**
     * Invokes a method on the given object with the given parameters
     * @param obj The object to invoke the method on, can be null for a static method
     * @param method The method to invoke
     * @param args The arguments of the method to pass
     * @return The return value of the method, null if a void method
     * @throws ReflectionException Wraps any reflection related exceptions
     */
    public static Object invokeMethod(@Nullable Object obj, Method method, Object... args) {
        try {
            return method.invoke(obj, args);
        }   catch(InvocationTargetException | IllegalAccessException ex) {
            throw new ReflectionException(ex);
        }
    }
}
