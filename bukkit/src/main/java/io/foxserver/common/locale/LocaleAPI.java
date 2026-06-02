package io.foxserver.common.locale;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLocaleChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * LocaleAPI — per-player, per-plugin localisation system for FoxServer plugins.
 *
 * <h3>Design</h3>
 * <ul>
 *   <li>Each plugin creates its own {@code LocaleAPI} instance; state is not shared.</li>
 *   <li>Lang files live in {@code plugins/<Plugin>/lang/<locale>.yml}.</li>
 *   <li>Player locale cache uses {@link UUID} keys — no {@link Player} references stored
 *       to prevent memory leaks on disconnect.</li>
 *   <li>Locale resolution runs only on first join or locale-change event; the result is
 *       cached indefinitely per session (cleared on quit).</li>
 *   <li>Parsed {@link YamlConfiguration} objects are cached with Caffeine (5-min expiry
 *       after last access) to avoid re-parsing on every lookup.</li>
 *   <li>Locale lookup uses a {@link LinkedHashMap} keyed by full tag and language prefix
 *       for O(1) matching instead of linear scan.</li>
 *   <li>Returns {@link Component} via MiniMessage with proper Adventure {@link TagResolver}
 *       support (RGB gradients, clickable text, etc.).</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * localeApi = new LocaleAPI(this, "ru_RU", true);
 * localeApi.load();
 * getServer().getPluginManager().registerEvents(localeApi, this);
 *
 * // In a command/listener:
 * Component msg = localeApi.getComponent(player, "auth.wrong_password");
 * Component msg2 = localeApi.getComponent(player, "auth.banned",
 *     Placeholder.unparsed("time", "5m"));
 * }</pre>
 */
public class LocaleAPI implements Listener {

    public static final String LANG_FOLDER = "lang";

    private static final MiniMessage MM = MiniMessage.miniMessage();

    // ── Core config ───────────────────────────────────────────────────────────

    private final Plugin plugin;
    private final String defaultLocaleTag;
    private final boolean autoTranslate;

    // ── Locale registry ───────────────────────────────────────────────────────

    /**
     * Ordered set of loaded locale tags (e.g. "ru_RU", "en_US").
     * Insertion order is preserved so the first entry is the primary fallback.
     */
    private final Set<String> supportedLocales = new LinkedHashSet<>();

    /**
     * Maps a BCP-47 language prefix (e.g. "ru") → the best available locale tag.
     * Built once during {@link #load()} for O(1) prefix matching.
     */
    private final Map<String, String> prefixIndex = new HashMap<>();

    // ── Player state ──────────────────────────────────────────────────────────

    /**
     * UUID → resolved locale tag for this plugin.
     * All writes happen on the main thread (join / locale-change events);
     * reads may happen from async tasks.  ConcurrentHashMap guarantees
     * safe concurrent access without explicit locking.
     */
    private final Map<UUID, String> playerLocales = new ConcurrentHashMap<>();

    // ── YAML cache ────────────────────────────────────────────────────────────

    /** locale tag → loaded YamlConfiguration; evicted 5 min after last access. */
    private final Cache<String, YamlConfiguration> configCache = Caffeine.newBuilder()
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .build();

    // ── Construction ──────────────────────────────────────────────────────────

    /**
     * @param plugin          the owning plugin
     * @param defaultLocale   BCP-47 locale tag used as fallback (e.g. {@code "ru_RU"})
     * @param autoTranslate   {@code true} = use the player's in-game client language;
     *                        {@code false} = every player gets {@code defaultLocale}
     */
    public LocaleAPI(Plugin plugin, String defaultLocale, boolean autoTranslate) {
        this.plugin = plugin;
        this.defaultLocaleTag = defaultLocale;
        this.autoTranslate = autoTranslate;
    }

    // ── Initialization ────────────────────────────────────────────────────────

    /**
     * Extracts bundled lang files from the jar, discovers available locales, and
     * builds the prefix index.  Call once from {@code onEnable}.
     *
     * @throws IllegalStateException if no lang files could be found
     */
    public void load() {
        File langDir = langDir();
        langDir.mkdirs();

        extractBundledFiles(langDir);
        discoverLocales(langDir);
        buildPrefixIndex();

        if (supportedLocales.isEmpty()) {
            throw new IllegalStateException(
                    "[" + plugin.getName() + "] No lang files found in " + langDir.getPath());
        }

        plugin.getLogger().info("Loaded " + supportedLocales.size()
                + " locale(s): " + String.join(", ", supportedLocales));
    }

    /**
     * Clears YAML cache and re-discovers locales (player assignments survive).
     */
    public void reload() {
        configCache.invalidateAll();
        supportedLocales.clear();
        prefixIndex.clear();
        load();
    }

    // ── Public API: Component (Adventure) ─────────────────────────────────────

    /**
     * Returns a parsed {@link Component} for the given key, resolved to the player's
     * locale.  Placeholders are passed as MiniMessage {@link TagResolver}s, enabling
     * full Adventure support (RGB, gradients, click events, hover text, etc.).
     *
     * <p>Example:
     * <pre>{@code
     * localeApi.getComponent(player, "ban.message",
     *     Placeholder.unparsed("time", "5m"),
     *     Placeholder.component("player", player.displayName()));
     * }</pre>
     *
     * @param player   the receiving player (nullable — uses default locale)
     * @param key      YAML path, e.g. {@code "auth.wrong_password"}
     * @param resolvers zero or more {@link TagResolver}s for placeholder substitution
     * @return parsed component, or {@link Component#empty()} if key is missing
     */
    public Component getComponent(Player player, String key, TagResolver... resolvers) {
        String raw = getRaw(player, key);
        if (raw == null) return Component.empty();
        return resolvers.length == 0
                ? MM.deserialize(raw)
                : MM.deserialize(raw, TagResolver.resolver(resolvers));
    }

    /**
     * Convenience overload using the server's default locale (for console/admin output).
     */
    public Component getComponent(String key, TagResolver... resolvers) {
        return getComponent(null, key, resolvers);
    }

    // ── Public API: raw String ────────────────────────────────────────────────

    /**
     * Returns the raw (unparsed) message string for the given key.
     * Useful when the caller needs to do additional string manipulation before parsing.
     *
     * @param player the player (nullable)
     * @param key    YAML path
     * @return the raw string, or {@code null} if not found anywhere
     */
    public String getRaw(Player player, String key) {
        return fallbackChain(resolveLocaleTag(player), key);
    }

    /**
     * Returns a localized string list.
     */
    public List<String> getStringList(Player player, String key) {
        String tag = resolveLocaleTag(player);
        YamlConfiguration cfg = configCache.get(tag, this::loadConfig);
        if (cfg == null || !cfg.contains(key)) {
            if (!tag.equals(defaultLocaleTag)) {
                cfg = configCache.get(defaultLocaleTag, this::loadConfig);
            }
        }
        return cfg != null ? cfg.getStringList(key) : Collections.emptyList();
    }

    /**
     * Returns the raw string with simple {@code {placeholder} → value} substitution.
     * Prefer {@link #getComponent(Player, String, TagResolver...)} for Adventure-native output.
     *
     * @param player       the player (nullable)
     * @param key          YAML path
     * @param replacements alternating key/value pairs, e.g. {@code "{time}", "5m"}
     * @return substituted string, or {@code null}
     */
    public String getMessage(Player player, String key, Object... replacements) {
        String msg = getRaw(player, key);
        if (msg == null || replacements.length == 0) return msg;
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            msg = msg.replace(String.valueOf(replacements[i]), String.valueOf(replacements[i + 1]));
        }
        return msg;
    }

    // ── Convenience helpers ───────────────────────────────────────────────────

    /**
     * Creates an {@link TagResolver} from a simple string key/value pair,
     * equivalent to {@link Placeholder#unparsed(String, String)}.
     * The tag name should NOT include angle brackets.
     */
    public static TagResolver placeholder(String name, Object value) {
        return Placeholder.unparsed(name, String.valueOf(value));
    }

    /** Returns the resolved locale tag for the given player. */
    public String getLocaleTag(Player player) {
        return resolveLocaleTag(player);
    }

    /** Immutable snapshot of currently supported locale tags. */
    public Set<String> getSupportedLocales() {
        return Collections.unmodifiableSet(supportedLocales);
    }

    // ── Bukkit Event Handlers ─────────────────────────────────────────────────

    /**
     * Fires on the main thread when the player changes their in-game language.
     * Uses {@code .put()} — same operation as computeIfAbsent's write path —
     * safe with ConcurrentHashMap and avoids any theoretical recursion issues.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onLocaleChange(PlayerLocaleChangeEvent event) {
        if (!autoTranslate) return;
        String tag = mapClientLocale(event.locale().toLanguageTag());
        playerLocales.put(event.getPlayer().getUniqueId(), tag);
    }

    /** Cleans up session state on logout to prevent unbounded map growth. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        playerLocales.remove(event.getPlayer().getUniqueId());
    }

    // ── Internal: locale resolution ───────────────────────────────────────────

    private String resolveLocaleTag(Player player) {
        if (player == null || !autoTranslate) return defaultLocaleTag;

        // computeIfAbsent is safe here: all writers use put() on the same
        // ConcurrentHashMap; no recursive updates occur inside the function.
        return playerLocales.computeIfAbsent(player.getUniqueId(), uuid ->
                mapClientLocale(player.locale().toLanguageTag()));
    }

    /**
     * Maps a raw client locale tag (e.g. {@code "ru-RU"}, {@code "ru_ru"}, {@code "en_gb"})
     * to a supported locale tag using the pre-built index for O(1) lookup.
     */
    private String mapClientLocale(String clientTag) {
        String normalised = normalise(clientTag);

        // 1. Exact match
        if (supportedLocales.contains(normalised)) return normalised;

        // 2. Language-prefix match (O(1) via index)
        String lang = normalised.contains("_")
                ? normalised.substring(0, normalised.indexOf('_'))
                : normalised;
        String indexed = prefixIndex.get(lang);
        if (indexed != null) return indexed;

        return defaultLocaleTag;
    }

    /**
     * Normalises a raw locale tag to the form {@code lang_REGION}:
     * hyphens → underscores, language part lower-cased, region upper-cased.
     */
    private static String normalise(String raw) {
        if (raw == null || raw.isEmpty()) return "en_US";
        String s = raw.replace('-', '_');
        int idx = s.indexOf('_');
        if (idx < 0) return s.toLowerCase(Locale.ROOT);
        return s.substring(0, idx).toLowerCase(Locale.ROOT)
                + '_'
                + s.substring(idx + 1).toUpperCase(Locale.ROOT);
    }

    /**
     * Builds a map from language prefix (e.g. "ru") → the first matching locale tag.
     * Called once after {@link #discoverLocales}.
     */
    private void buildPrefixIndex() {
        for (String tag : supportedLocales) {
            String lang = tag.contains("_") ? tag.substring(0, tag.indexOf('_')) : tag;
            // putIfAbsent: first discovered locale wins for each language prefix
            prefixIndex.putIfAbsent(lang, tag);
        }
    }

    // ── Internal: message lookup ──────────────────────────────────────────────

    /**
     * Resolves a key through: player locale → server default → first available.
     */
    private String fallbackChain(String primaryTag, String key) {
        String msg = fromConfig(primaryTag, key);
        if (msg != null) return msg;

        if (!primaryTag.equals(defaultLocaleTag)) {
            msg = fromConfig(defaultLocaleTag, key);
            if (msg != null) return msg;
        }

        for (String locale : supportedLocales) {
            msg = fromConfig(locale, key);
            if (msg != null) return msg;
        }
        return null;
    }

    private String fromConfig(String tag, String key) {
        if (!supportedLocales.contains(tag)) return null;
        YamlConfiguration cfg = configCache.get(tag, this::loadConfig);
        return (cfg != null) ? cfg.getString(key) : null;
    }

    private YamlConfiguration loadConfig(String tag) {
        File file = new File(langDir(), tag + ".yml");
        if (!file.exists()) return null;
        try {
            return YamlConfiguration.loadConfiguration(file);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load lang/" + tag + ".yml", e);
            return null;
        }
    }

    // ── Internal: resource extraction ─────────────────────────────────────────

    /**
     * Extracts all {@code lang/*.yml} entries from the plugin jar.
     *
     * <p>Strategy:
     * <ol>
     *   <li>Try the robust ZipFile approach (supports automatic discovery of all langs).</li>
     *   <li>On failure (unusual ClassLoader environments), log a warning — admins can
     *       place files manually.</li>
     *   <li>Verify file existence to preserve admin edits.</li>
     * </ol>
     */
    private static final List<String> BUNDLED_LOCALES = Arrays.asList("en_US", "ru_RU");

    private void extractBundledFiles(File langDir) {
        for (String locale : BUNDLED_LOCALES) {
            File target = new File(langDir, locale + ".yml");
            if (target.exists()) continue; // preserve admin edits

            try (InputStream in = plugin.getResource("lang/" + locale + ".yml")) {
                if (in != null) {
                    target.getParentFile().mkdirs();
                    Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    plugin.getLogger().info("Extracted lang/" + locale + ".yml");
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to extract bundled resource lang file: " + locale, e);
            }
        }

        try {
            File jarFile = new File(
                    plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());

            if (jarFile.isFile()) {
                try (ZipFile zip = new ZipFile(jarFile)) {
                    String prefix = LANG_FOLDER + "/";
                    Enumeration<? extends ZipEntry> entries = zip.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        String name = entry.getName();
                        if (!name.startsWith(prefix) || entry.isDirectory()) continue;

                        String fileName = name.substring(prefix.length());
                        if (fileName.isEmpty()) continue;

                        File target = new File(langDir, fileName);
                        if (target.exists()) continue; // preserve admin edits

                        target.getParentFile().mkdirs();
                        try (InputStream in = zip.getInputStream(entry)) {
                            Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            plugin.getLogger().info("Extracted lang/" + fileName);
                        }
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not auto-extract lang files from jar (unusual ClassLoader?): "
                            + e.getMessage()
                            + " — place lang/*.yml files manually if needed.");
        }
    }

    private void discoverLocales(File langDir) {
        File[] files = langDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;
        // Sort for deterministic ordering (default locale should be encountered predictably)
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File f : files) {
            supportedLocales.add(f.getName().replace(".yml", ""));
        }
    }

    private File langDir() {
        return new File(plugin.getDataFolder(), LANG_FOLDER);
    }
}
