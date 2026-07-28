package net.lazyz.worldareareset;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
        this.languageConfig = YamlConfiguration.loadConfiguration(languageFile);
    }

    public String text(String key, String fallback) {
        String legacyPath = "messages." + key;
        if (plugin.getConfig().isString(legacyPath)) {
            return plugin.getConfig().getString(legacyPath, fallback);
        }
        return languageConfig.getString(key, fallback);
    }

    public List<String> list(String key) {
        String legacyPath = "messages." + key;
        if (plugin.getConfig().isList(legacyPath)) {
            return plugin.getConfig().getStringList(legacyPath);
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
            if (!target.exists()) {
                plugin.saveResource("lang/" + language + ".yml", false);
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
