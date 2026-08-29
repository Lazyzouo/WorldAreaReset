package net.lazyz.worldareareset;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;

public final class LanguageManager {

    private static final String DEFAULT_LANGUAGE = "en_US";
    private static final Set<String> SUPPORTED_LANGUAGES = Set.of(DEFAULT_LANGUAGE, "zh_CN");

    private final WorldAreaResetPlugin plugin;
    private String languageCode;

    public LanguageManager(WorldAreaResetPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        String requested = normalize(plugin.getConfig().getString("language", DEFAULT_LANGUAGE));
        if (!SUPPORTED_LANGUAGES.contains(requested)) {
            plugin.getLogger().warning("Unsupported language '" + requested + "'. Falling back to " + DEFAULT_LANGUAGE + ".");
            requested = DEFAULT_LANGUAGE;
        }

        this.languageCode = requested;
        String previousApplied = plugin.getConfig().getString("language-applied", "");
        boolean configChanged = false;
        if (!requested.equals(previousApplied)) {
            plugin.getConfig().set("language-applied", requested);
            configChanged = true;
        }
        if (refreshConfigHeader()) {
            configChanged = true;
        }
        if (configChanged) {
            try {
                plugin.saveConfigAtomically();
            } catch (Exception error) {
                plugin.getConfig().set("language-applied", previousApplied);
                plugin.getLogger().log(Level.WARNING,
                        "Could not persist language-applied or the localized header; the existing config.yml remains unchanged.", error);
            }
        }
        removeLegacyLanguageFiles();
    }

    private boolean refreshConfigHeader() {
        String resourcePath = WorldAreaResetPlugin.configurationResource(languageCode);
        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStream stream = plugin.getResource(resourcePath)) {
            if (stream == null) {
                return false;
            }
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                defaults.options().parseComments(true);
                defaults.load(reader);
            }
        } catch (Exception error) {
            plugin.getLogger().log(Level.FINE, "Could not load localized configuration header.", error);
            return false;
        }

        List<String> header = configurationHeader(plugin.getConfig());
        List<String> localizedHeader = configurationHeader(defaults);
        if (localizedHeader == null || localizedHeader.isEmpty()
                || header == null || !looksLikeOfficialHeader(String.join("\n", header))
                || localizedHeader.equals(header)) {
            return false;
        }
        plugin.getConfig().setComments("config_version", new java.util.ArrayList<>(localizedHeader));
        if (!defaults.options().getHeader().isEmpty()) {
            plugin.getConfig().options().setHeader(new java.util.ArrayList<>(defaults.options().getHeader()));
        }
        return true;
    }

    private List<String> configurationHeader(FileConfiguration configuration) {
        List<String> header = configuration.options().getHeader();
        if (header != null && !header.isEmpty()) {
            return header;
        }
        return configuration.getComments("config_version");
    }

    private boolean looksLikeOfficialHeader(String header) {
        return header.contains("WorldAreaReset") || header.contains("插件作者")
                || header.contains("After editing this file") || header.contains("修改本文件后");
    }

    public String text(String key, String fallback) {
        return plugin.getConfig().getString("messages." + key, fallback);
    }

    public List<String> list(String key) {
        return plugin.getConfig().getStringList("messages." + key);
    }

    public String code() {
        return languageCode;
    }

    private void removeLegacyLanguageFiles() {
        Path langDirectory = new File(plugin.getDataFolder(), "lang").toPath();
        if (!Files.isDirectory(langDirectory)) {
            return;
        }

        try (var paths = Files.walk(langDirectory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException error) {
                    plugin.getLogger().warning("Could not remove legacy language path: " + path);
                }
            });
        } catch (IOException error) {
            plugin.getLogger().warning("Could not scan legacy language directory: " + langDirectory);
        }

        if (Files.exists(langDirectory)) {
            plugin.getLogger().warning("Legacy language directory remains after cleanup: " + langDirectory);
        } else {
            plugin.getLogger().info("Removed legacy language directory: " + langDirectory);
        }
    }

    private String normalize(String value) {
        String normalized = value == null ? DEFAULT_LANGUAGE : value.trim().replace('-', '_');
        if (normalized.equalsIgnoreCase("en_US")) {
            return "en_US";
        }
        if (normalized.equalsIgnoreCase("zh_CN") || normalized.toLowerCase(Locale.ROOT).startsWith("zh")) {
            return "zh_CN";
        }
        return normalized;
    }
}
