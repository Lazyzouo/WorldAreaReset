package net.lazyz.worldareareset;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Resolves the selected language and the canonical MiniMessage text format. */
public final class LanguageManager {
    public static final String CHINESE = "zh_CN";
    public static final String ENGLISH = "en_US";
    private static final String REPLACEMENT_SEPARATOR = "|||";
    private static final Pattern HEX_TAG = Pattern.compile("<#[0-9a-fA-F]{6}>");

    private final WorldAreaResetPlugin plugin;
    private volatile String language = ENGLISH;
    private volatile YamlConfiguration bundledEnglish = new YamlConfiguration();
    private volatile List<Map.Entry<String, String>> forwardReplacements = List.of();
    private volatile List<Map.Entry<String, String>> reverseReplacements = List.of();

    public LanguageManager(WorldAreaResetPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public synchronized void reload() {
        language = normalize(plugin.getConfig().getString("language", ENGLISH));
        String previousApplied = plugin.getConfig().getString("language-applied", "");
        if (!language.equalsIgnoreCase(previousApplied)) {
            plugin.getConfig().set("language-applied", language);
            try {
                plugin.saveConfigAtomically();
            } catch (IOException exception) {
                plugin.getConfig().set("language-applied", previousApplied);
                plugin.getLogger().warning("Unable to persist language-applied: " + exception.getMessage());
            }
        }
        bundledEnglish = loadBundledEnglish();
        List<Map.Entry<String, String>> configured = new ArrayList<>(replacementsFrom(plugin.getConfig()));
        if (ENGLISH.equals(language)) {
            List<Map.Entry<String, String>> bundled = new ArrayList<>(replacementsFrom(bundledEnglish));
            for (Map.Entry<String, String> entry : loadBundledEnglishReplacements()) {
                if (bundled.stream().noneMatch(existing -> existing.getKey().equals(entry.getKey()))) bundled.add(entry);
            }
            for (Map.Entry<String, String> entry : bundled) {
                if (configured.stream().noneMatch(existing -> existing.getKey().equals(entry.getKey()))) configured.add(entry);
            }
        }
        configured.sort(Comparator.comparingInt((Map.Entry<String, String> entry) -> entry.getKey().length()).reversed());
        forwardReplacements = List.copyOf(configured);
        List<Map.Entry<String, String>> reverse = new ArrayList<>();
        for (Map.Entry<String, String> entry : configured) reverse.add(Map.entry(entry.getValue(), entry.getKey()));
        reverse.sort(Comparator.comparingInt((Map.Entry<String, String> entry) -> entry.getKey().length()).reversed());
        reverseReplacements = List.copyOf(reverse);
        removeLegacyLanguageFiles();
    }

    private YamlConfiguration loadBundledEnglish() {
        for (String resourcePath : List.of("config.yml", "config-en_US.yml")) {
            try (InputStream input = plugin.getResource(resourcePath)) {
                if (input == null) continue;
                YamlConfiguration loaded = new YamlConfiguration();
                loaded.options().parseComments(true);
                loaded.load(new InputStreamReader(input, StandardCharsets.UTF_8));
                if ("config.yml".equals(resourcePath)
                        && !ENGLISH.equalsIgnoreCase(loaded.getString("language", ""))) continue;
                return loaded;
            } catch (Exception exception) {
                plugin.getLogger().fine("Unable to load bundled English language data from "
                        + resourcePath + ": " + exception.getMessage());
            }
        }
        return new YamlConfiguration();
    }

    private List<Map.Entry<String, String>> loadBundledEnglishReplacements() {
        try (InputStream input = plugin.getResource("lang/en_US.yml")) {
            if (input == null) return List.of();
            YamlConfiguration dictionary = new YamlConfiguration();
            dictionary.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            return replacementsFrom(dictionary);
        } catch (Exception exception) {
            return List.of();
        }
    }

    private List<Map.Entry<String, String>> replacementsFrom(ConfigurationSection section) {
        List<Map.Entry<String, String>> result = new ArrayList<>();
        for (String replacement : section.getStringList("inline-replacements")) {
            int separator = replacement.indexOf(REPLACEMENT_SEPARATOR);
            if (separator <= 0 || separator + REPLACEMENT_SEPARATOR.length() >= replacement.length()) continue;
            String source = replacement.substring(0, separator);
            String target = replacement.substring(separator + REPLACEMENT_SEPARATOR.length());
            if (!target.contains("{gradient}") && HEX_TAG.matcher(target).find()) {
                target = HEX_TAG.matcher(target).replaceAll("");
                target = "{gradient}" + target + "</gradient>";
            }
            result.add(Map.entry(source, target));
        }
        return result;
    }

    public Object getMessage(String key) {
        return localizedValue("messages." + key, plugin.getConfig().get("messages." + key));
    }

    public String getMessageString(String key, String fallback) {
        Object value = getMessage(key);
        return value instanceof String text ? text : fallback;
    }

    public List<String> getMessageList(String key) {
        Object value = getMessage(key);
        if (!(value instanceof List<?> list)) return List.of();
        List<String> result = new ArrayList<>(list.size());
        for (Object entry : list) if (entry != null) result.add(String.valueOf(entry));
        return List.copyOf(result);
    }

    public String getGuiString(String key, String fallback) {
        Object value = localizedValue("gui." + key, plugin.getConfig().get("gui." + key));
        return value instanceof String text ? text : fallback;
    }

    public List<String> getGuiStringList(String key) {
        Object value = localizedValue("gui." + key, plugin.getConfig().get("gui." + key));
        if (!(value instanceof List<?> list)) return List.of();
        List<String> result = new ArrayList<>(list.size());
        for (Object entry : list) if (entry != null) result.add(String.valueOf(entry));
        return List.copyOf(result);
    }

    public String text(String key, String fallback) {
        return getMessageString(key, fallback);
    }

    public List<String> list(String key) {
        return getMessageList(key);
    }

    private Object localizedValue(String path, Object configured) {
        if (!ENGLISH.equals(language)) return configured;
        Object bundled = bundledEnglish.get(path);
        if (bundled == null || containsCjk(configured)) return bundled != null ? bundled : configured;
        return configured != null ? configured : bundled;
    }

    private boolean containsCjk(Object value) {
        if (value instanceof String text) {
            return text.codePoints().anyMatch(codePoint -> codePoint >= 0x3400 && codePoint <= 0x9FFF);
        }
        if (value instanceof List<?> list) return list.stream().anyMatch(this::containsCjk);
        if (value instanceof Map<?, ?> map) return map.values().stream().anyMatch(this::containsCjk);
        return false;
    }

    public String translateInline(String text) {
        if (text == null || !ENGLISH.equals(language)) return text;
        String translated = text;
        for (Map.Entry<String, String> replacement : forwardReplacements) {
            translated = translated.replace(replacement.getKey(), replacement.getValue());
            String legacySource = toLegacyAmpersand(replacement.getKey());
            if (!legacySource.equals(replacement.getKey())) translated = translated.replace(legacySource, replacement.getValue());
        }
        return translated;
    }

    public String expandConfiguredTokens(String text) {
        if (text == null) return null;
        return text.replace("{gradient}", "<gradient:#FFB7D5:#D7C7FF:#B9E7FF:#D7C7FF:#FFB7D5>");
    }

    public String canonicalize(String text) {
        if (text == null || !ENGLISH.equals(language)) return text;
        String canonical = text;
        for (Map.Entry<String, String> replacement : reverseReplacements) canonical = canonical.replace(replacement.getKey(), replacement.getValue());
        return canonical;
    }

    public String code() {
        return language;
    }

    private void removeLegacyLanguageFiles() {
        Path directory = new File(plugin.getDataFolder(), "lang").toPath();
        if (!Files.isDirectory(directory)) return;
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    plugin.getLogger().warning("Could not remove legacy language path: " + path);
                }
            });
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not scan legacy language directory: " + directory);
        }
    }

    private String toLegacyAmpersand(String text) {
        Matcher matcher = HEX_TAG.matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group().substring(2, 8);
            matcher.appendReplacement(result, Matcher.quoteReplacement("&#" + hex));
        }
        matcher.appendTail(result);
        return result.toString().replace("<bold>", "&l").replace("<italic>", "&o")
                .replace("<underlined>", "&n").replace("<strikethrough>", "&m")
                .replace("<obfuscated>", "&k").replace("<reset>", "&r");
    }

    private String normalize(String value) {
        if (value == null) return ENGLISH;
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return normalized.equals("zh") || normalized.equals("zh_cn") ? CHINESE : ENGLISH;
    }
}
