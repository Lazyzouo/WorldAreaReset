package net.lazyz.worldareareset;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;

public final class LanguageManager {

    private static final String DEFAULT_LANGUAGE = "zh_CN";
    private static final Set<String> SUPPORTED_LANGUAGES = Set.of(DEFAULT_LANGUAGE, "en_US");

    private final WorldAreaResetPlugin plugin;
    private FileConfiguration languageConfig;
    private String languageCode;

    public LanguageManager(WorldAreaResetPlugin plugin) {
        this.plugin = plugin;
        saveBundledLanguages();
        reload();
    }

    public void reload() {
        String requested = normalize(plugin.getConfig().getString("language", DEFAULT_LANGUAGE));
        if (!SUPPORTED_LANGUAGES.contains(requested)) {
            plugin.getLogger().warning("Unsupported language '" + requested + "'. Falling back to " + DEFAULT_LANGUAGE + ".");
            requested = DEFAULT_LANGUAGE;
        }

        this.languageCode = requested;
        File languageFile = new File(plugin.getDataFolder(), "lang/" + languageCode + ".yml");
        YamlConfiguration loaded = YamlConfiguration.loadConfiguration(languageFile);
        String resourcePath = "lang/" + languageCode + ".yml";
        try (InputStream defaultsStream = plugin.getResource(resourcePath)) {
            if (defaultsStream != null) {
                try (Reader defaultsReader = new InputStreamReader(defaultsStream, StandardCharsets.UTF_8)) {
                    loaded.setDefaults(YamlConfiguration.loadConfiguration(defaultsReader));
                }
            }
        } catch (Exception error) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not load bundled language defaults for " + languageCode + ".", error);
        }
        this.languageConfig = loaded;
    }

    public String text(String key, String fallback) {
        String legacyPath = "messages." + key;
        if (plugin.getConfig().isString(legacyPath)) {
            return plugin.getConfig().getString(legacyPath, fallback);
        }
        String localizedPath = "messages." + languageCode + "." + key;
        if (plugin.getConfig().isString(localizedPath)) {
            return plugin.getConfig().getString(localizedPath, fallback);
        }
        return languageConfig.getString(key, fallback);
    }

    public List<String> list(String key) {
        String legacyPath = "messages." + key;
        if (plugin.getConfig().isList(legacyPath)) {
            return plugin.getConfig().getStringList(legacyPath);
        }
        String localizedPath = "messages." + languageCode + "." + key;
        if (plugin.getConfig().isList(localizedPath)) {
            return plugin.getConfig().getStringList(localizedPath);
        }
        return languageConfig.getStringList(key);
    }

    public String code() {
        return languageCode;
    }

    private void saveBundledLanguages() {
        File langDirectory = new File(plugin.getDataFolder(), "lang");
        if (!langDirectory.exists() && !langDirectory.mkdirs()) {
            plugin.getLogger().warning("Could not create language directory: " + langDirectory);
        }

        for (String language : SUPPORTED_LANGUAGES) {
            File target = new File(langDirectory, language + ".yml");
            String resourcePath = "lang/" + language + ".yml";
            if (!target.exists()) {
                plugin.saveResource(resourcePath, false);
                continue;
            }

            try (InputStream defaultsStream = plugin.getResource(resourcePath)) {
                if (defaultsStream == null) {
                    plugin.getLogger().warning("Bundled language resource is missing: " + resourcePath);
                    continue;
                }
                try (Reader defaultsReader = new InputStreamReader(defaultsStream, StandardCharsets.UTF_8)) {
                    ConfigurationUpdater.UpdateResult result = ConfigurationUpdater.mergeMissingValues(
                            target.toPath(), defaultsReader, null, plugin.getPluginMeta().getVersion());
                    plugin.logConfigurationUpdate(resourcePath, result);
                }
            } catch (Exception error) {
                plugin.getLogger().log(Level.WARNING,
                        "Could not safely update " + resourcePath + "; the existing file remains in use.", error);
            }
        }
    }

    private String normalize(String value) {
        String normalized = value.trim().replace('-', '_');
        if (normalized.equalsIgnoreCase("en_US")) {
            return "en_US";
        }
        if (normalized.equalsIgnoreCase("zh_CN") || normalized.toLowerCase(Locale.ROOT).startsWith("zh")) {
            return DEFAULT_LANGUAGE;
        }
        return normalized;
    }
}
