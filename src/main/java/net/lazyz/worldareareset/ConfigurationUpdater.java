package net.lazyz.worldareareset;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.io.Reader;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class ConfigurationUpdater {

    private static final int WORLD_MODULE_CONFIG_VERSION = 15;
    private static final int CLEANUP_INTERVAL_CONFIG_VERSION = 18;
    private static final int VISUAL_CONFIG_VERSION = 22;
    private static final int FLAT_MESSAGES_CONFIG_VERSION = 23;
    private static final int HELP_LAYOUT_CONFIG_VERSION = 24;
    private static final int KITLOADER_PRESENTATION_CONFIG_VERSION = 25;
    private static final String STANDARD_DIVIDER =
            "<gradient:#FFB7D5:#D7C7FF:#B9E7FF:#D7C7FF:#FFB7D5><bold><strikethrough>---------"
                    + "<bold><strikethrough>---------<bold> ✧ <bold><strikethrough>---------"
                    + "<bold><strikethrough>---------</gradient>";

    private ConfigurationUpdater() {
    }

    static UpdateResult mergeMissingValues(Path targetFile, Reader defaultsReader,
                                           String versionPath, String releaseVersion)
            throws IOException, InvalidConfigurationException {
        return mergeMissingValues(targetFile, defaultsReader, versionPath, releaseVersion, true);
    }

    static UpdateResult mergeMissingValues(Path targetFile, Reader defaultsReader,
                                           String versionPath, String releaseVersion,
                                           boolean createBackup)
            throws IOException, InvalidConfigurationException {
        return mergeMissingValues(targetFile, defaultsReader, versionPath, releaseVersion,
                createBackup, false);
    }

    static UpdateResult mergeMissingValues(Path targetFile, Reader defaultsReader,
                                           String versionPath, String releaseVersion,
                                           boolean createBackup, boolean refreshComments)
            throws IOException, InvalidConfigurationException {
        Objects.requireNonNull(targetFile, "targetFile");
        Objects.requireNonNull(defaultsReader, "defaultsReader");
        Objects.requireNonNull(releaseVersion, "releaseVersion");

        Path normalizedTarget = targetFile.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalizedTarget)) {
            throw new IOException("Configuration file does not exist: " + normalizedTarget);
        }

        YamlConfiguration current = load(normalizedTarget);
        YamlConfiguration defaults = load(defaultsReader);
        // Kitloader's canonical marker uses a hyphen. Promote the historical
        // underscore spelling when an upgraded preset uses the canonical key.
        int legacyVersionMarkerChanged = 0;
        if ("config-version".equals(versionPath)
                && !defaults.contains("config-version", true)
                && defaults.contains("config_version", true)) {
            versionPath = "config_version";
        } else if ("config-version".equals(versionPath) && defaults.contains("config-version", true)
                && current.contains("config_version", true)) {
            if (!current.contains("config-version", true)) current.set("config-version", current.get("config_version"));
            current.set("config_version", null);
            legacyVersionMarkerChanged = 1;
        }
        int oldVersion = readVersion(current, versionPath, "Existing configuration");
        int targetVersion = readVersion(defaults, versionPath, "Bundled defaults");
        if (versionPath != null && targetVersion < 0) {
            throw new InvalidConfigurationException(
                    "Bundled defaults must define a non-negative integer " + versionPath);
        }
        if (versionPath != null && oldVersion > targetVersion) {
            throw new InvalidConfigurationException(
                    "Existing configuration uses newer schema v" + oldVersion
                            + "; refusing to downgrade it to v" + targetVersion);
        }
        int migratedKeys = migrateWorldModules(current, defaults, versionPath, oldVersion, targetVersion);
        migratedKeys += legacyVersionMarkerChanged;
        migratedKeys += migrateCleanupInterval(current, defaults, versionPath, oldVersion, targetVersion);
        migratedKeys += migrateLanguageAndVisualStructure(current, defaults, versionPath, oldVersion, targetVersion);
        migratedKeys += migrateKnownLegacyMessages(current, defaults);
        migratedKeys += migrateLegacyDividers(current);
        migratedKeys += migrateLegacyMessageFormatting(current, defaults, versionPath, oldVersion, targetVersion);
        migratedKeys += migrateHelpMenuLayout(current, defaults, versionPath, oldVersion, targetVersion);
        migratedKeys += migrateKitloaderPresentation(current, defaults, versionPath, oldVersion, targetVersion);
        migratedKeys += refreshOfficialHeader(current, defaults, versionPath, oldVersion, targetVersion,
                refreshComments);
        if (refreshComments) {
            migratedKeys += refreshLocalizedComments(current, defaults);
        }
        List<String> defaultPaths = new ArrayList<>(defaults.getKeys(true));
        defaultPaths.sort(Comparator.comparingInt(ConfigurationUpdater::pathDepth));

        List<String> conflicts = findConflicts(current, defaults, defaultPaths, versionPath);
        if (!conflicts.isEmpty()) {
            return new UpdateResult(0, false, null, conflicts);
        }

        int addedKeys = migratedKeys + mergeMissingPaths(current, defaults, defaultPaths, versionPath);
        boolean versionUpdated = updateVersion(current, defaults, targetVersion, versionPath, oldVersion);
        if (addedKeys == 0 && !versionUpdated) {
            return new UpdateResult(0, false, null, List.of());
        }

        copyHeaderAndFooterWhenMissing(current, defaults);
        Path backupFile = save(current, normalizedTarget, releaseVersion, createBackup, versionPath, oldVersion,
                targetVersion);
        return new UpdateResult(addedKeys, versionUpdated, backupFile, List.of());
    }

    /** Aligns legacy updater nesting, option spelling, and status colors with Kitloader. */
    private static int migrateKitloaderPresentation(YamlConfiguration current, YamlConfiguration defaults,
                                                    String versionPath, int currentVersion, int targetVersion) {
        if (versionPath == null || targetVersion < KITLOADER_PRESENTATION_CONFIG_VERSION
                || currentVersion >= KITLOADER_PRESENTATION_CONFIG_VERSION) return 0;
        int changed = 0;
        if (current.contains("updates.auto_download", true)) {
            if (!current.contains("updates.auto-download", true)) current.set("updates.auto-download", current.get("updates.auto_download"));
            current.set("updates.auto_download", null);
            changed++;
        }
        if (current.contains("updates.notify_latest", true)) {
            current.set("updates.notify_latest", null);
            changed++;
        }
        ConfigurationSection legacy = current.getConfigurationSection("messages.updater");
        if (legacy != null) {
            Map<String, String> names = Map.of(
                    "checking", "update_checking", "latest", "update_latest",
                    "available", "update_available", "manual_download", "update_manual",
                    "downloaded", "update_downloaded", "failed", "update_failed");
            for (Map.Entry<String, String> entry : names.entrySet()) {
                Object value = legacy.get(entry.getKey());
                if (value != null && !current.contains("messages." + entry.getValue(), true)) {
                    String text = String.valueOf(value).replace("{version}",
                            "available".equals(entry.getKey()) ? "{latest}" : "{version}");
                    current.set("messages." + entry.getValue(), text);
                    changed++;
                }
                if (value != null) {
                    legacy.set(entry.getKey(), null);
                    changed++;
                }
            }
            // `disabled` was an official updater message in the legacy
            // nesting, but the Kitloader contract has no equivalent because
            // updates.enabled now controls that state.
            if (legacy.contains("disabled", true)) {
                legacy.set("disabled", null);
                changed++;
            }
            // Retain administrator-defined updater extensions. Remove the
            // retired section only when all of its children were official keys.
            if (legacy.getKeys(false).isEmpty()) {
                current.set("messages.updater", null);
                changed++;
            }
        }
        ConfigurationSection messages = current.getConfigurationSection("messages");
        if (messages != null) {
            for (String key : List.of("reload_success", "manual_cleanup_started", "manual_recreate_started",
                    "no_permission", "wrong_usage", "start_cleanup", "start_restore")) {
                Object value = messages.get(key);
                if (value instanceof String text && text.startsWith("<gradient:")) {
                    String color = switch (key) {
                        case "reload_success" -> "#55FF55";
                        case "manual_cleanup_started", "manual_recreate_started" -> "#FFFF55";
                        case "start_cleanup", "start_restore" -> "#00D2FF";
                        default -> "#FF5555";
                    };
                    int open = text.indexOf('>');
                    int close = text.lastIndexOf("</gradient>");
                    if (open >= 0 && close > open && close + 11 == text.length()) {
                        messages.set(key, "<color:" + color + ">" + text.substring(open + 1, close) + "</color>");
                        changed++;
                    }
                }
            }
        }
        return changed;
    }

    private static int readVersion(YamlConfiguration configuration, String versionPath, String source)
            throws InvalidConfigurationException {
        if (versionPath == null || !configuration.contains(versionPath, true)) {
            if ("config-version".equals(versionPath) && configuration.contains("config_version", true)) {
                versionPath = "config_version";
            } else if ("config_version".equals(versionPath) && configuration.contains("config-version", true)) {
                versionPath = "config-version";
            } else {
                return -1;
            }
        }

        Object value = configuration.get(versionPath);
        long parsed;
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            parsed = ((Number) value).longValue();
        } else if (value instanceof String string && string.trim().matches("\\d+")) {
            try {
                parsed = Long.parseLong(string.trim());
            } catch (NumberFormatException error) {
                throw invalidVersion(source, versionPath, value);
            }
        } else {
            throw invalidVersion(source, versionPath, value);
        }

        if (parsed < 0 || parsed > Integer.MAX_VALUE) {
            throw invalidVersion(source, versionPath, value);
        }
        return (int) parsed;
    }

    private static InvalidConfigurationException invalidVersion(String source, String path, Object value) {
        return new InvalidConfigurationException(
                source + " " + path + " must be a non-negative integer; found "
                        + (value == null ? "null" : value.getClass().getSimpleName()));
    }

    private static int migrateKnownLegacyMessages(YamlConfiguration current, YamlConfiguration defaults) {
        int changed = 0;
        List<String> prefixPaths = List.of("prefix", "messages.en_US.prefix", "messages.zh_CN.prefix");
        for (String path : prefixPaths) {
            if (!current.isString(path) || !defaults.isString(path)) {
                continue;
            }
            String value = current.getString(path, "");
            if (value.equals("&8[&6WorldAreaReset&8] &r")
                    || value.equals("&#8A2387&l[&#E62028&lWorldAreaReset&#8A2387&l] &8&l» &7")
                    || value.equals("§8[§6WorldAreaReset§8] §r")) {
                current.set(path, defaults.getString(path));
                copyComments(current, defaults, path);
                changed++;
            }
        }
        return changed;
    }

    /** Replace only old decorative divider rows; all other administrator text remains untouched. */
    private static int migrateLegacyDividers(YamlConfiguration current) {
        ConfigurationSection messages = current.getConfigurationSection("messages");
        return messages == null ? migrateLegacyDividers((ConfigurationSection) current)
                : migrateLegacyDividers(messages);
    }

    private static int migrateLegacyDividers(ConfigurationSection section) {
        int changed = 0;
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (value instanceof ConfigurationSection child) {
                changed += migrateLegacyDividers(child);
            } else if (value instanceof String text) {
                String migrated = replaceLegacyDividerLines(text);
                if (!migrated.equals(text)) {
                    section.set(key, migrated);
                    changed++;
                }
            } else if (value instanceof List<?> list) {
                List<Object> migrated = new ArrayList<>(list.size());
                boolean listChanged = false;
                for (Object entry : list) {
                    if (entry instanceof String text) {
                        String replacement = replaceLegacyDividerLines(text);
                        migrated.add(replacement);
                        listChanged |= !replacement.equals(text);
                    } else {
                        migrated.add(entry);
                    }
                }
                if (listChanged) {
                    section.set(key, migrated);
                    changed++;
                }
            }
        }
        return changed;
    }

    /** Convert old Bukkit color codes in plugin-owned message values without touching custom settings. */
    private static int migrateLegacyMessageFormatting(YamlConfiguration current, YamlConfiguration defaults,
                                                       String versionPath, int currentVersion, int targetVersion) {
        if (versionPath == null || targetVersion < VISUAL_CONFIG_VERSION
                || currentVersion >= VISUAL_CONFIG_VERSION) {
            return 0;
        }
        ConfigurationSection messages = current.getConfigurationSection("messages");
        ConfigurationSection defaultMessages = defaults.getConfigurationSection("messages");
        return messages == null || defaultMessages == null ? 0
                : migrateLegacyMessageFormatting(messages, defaultMessages, "messages");
    }

    private static int migrateLegacyMessageFormatting(ConfigurationSection section, ConfigurationSection defaults,
                                                       String path) {
        int changed = 0;
        for (String key : section.getKeys(false)) {
            String childPath = path + "." + key;
            Object value = section.get(key);
            if (value instanceof ConfigurationSection child) {
                ConfigurationSection defaultChild = defaults.getConfigurationSection(key);
                if (defaultChild != null) {
                    changed += migrateLegacyMessageFormatting(child, defaultChild, childPath);
                }
            } else if (value instanceof String text && !key.equalsIgnoreCase("prefix")) {
                Object defaultValue = defaults.get(key);
                String migrated = isOfficialLegacyMessage(key, text) && defaultValue instanceof String
                        ? (String) defaultValue
                        : legacyToMiniMessage(text);
                if (!migrated.equals(text)) {
                    section.set(key, migrated);
                    if (defaultValue != null) {
                        copyComments(section, defaults, key);
                    }
                    changed++;
                }
            } else if (value instanceof List<?> list) {
                Object defaultValue = defaults.get(key);
                if (defaultValue instanceof List<?> && isOfficialLegacyMessage(key, list)) {
                    section.set(key, defaultValue);
                    copyComments(section, defaults, key);
                    changed++;
                    continue;
                }
                List<Object> migrated = new ArrayList<>(list.size());
                boolean listChanged = false;
                for (Object entry : list) {
                    if (entry instanceof String text) {
                        String replacement = legacyToMiniMessage(text);
                        migrated.add(replacement);
                        listChanged |= !replacement.equals(text);
                    } else {
                        migrated.add(entry);
                    }
                }
                if (listChanged) {
                    section.set(key, migrated);
                    changed++;
                }
            }
        }
        return changed;
    }

    /** Flatten legacy per-language messages and remove the retired formatting/replacement settings. */
    private static int migrateLanguageAndVisualStructure(YamlConfiguration current, YamlConfiguration defaults,
                                                          String versionPath, int currentVersion, int targetVersion) {
        if (versionPath == null || targetVersion < FLAT_MESSAGES_CONFIG_VERSION) {
            return 0;
        }

        int changed = 0;
        String language = normalizeLanguage(current.getString("language", "en_US"));
        ConfigurationSection messages = current.getConfigurationSection("messages");
        if (messages != null) {
            ConfigurationSection localized = messages.getConfigurationSection(language);
            if (localized != null) {
                for (String key : localized.getKeys(false)) {
                    current.set("messages." + key, localized.get(key));
                    copyComments(current, defaults, "messages." + key);
                    changed++;
                }
            }
            for (String legacyLanguage : List.of("en_US", "zh_CN", "en-US", "zh-CN")) {
                if (messages.get(legacyLanguage) != null) {
                    current.set("messages." + legacyLanguage, null);
                    changed++;
                }
            }
        }

        // A language switch must replace bundled messages so a config never renders both languages.
        String applied = normalizeLanguage(current.getString("language-applied", ""));
        if (currentVersion < FLAT_MESSAGES_CONFIG_VERSION || !language.equals(applied)) {
            ConfigurationSection defaultMessages = defaults.getConfigurationSection("messages");
            if (defaultMessages != null) {
                for (String relativePath : defaultMessages.getKeys(true)) {
                    if (defaultMessages.isConfigurationSection(relativePath)) {
                        continue;
                    }
                    String path = "messages." + relativePath;
                    Object value = defaultMessages.get(relativePath);
                    if (value != null && (currentVersion < FLAT_MESSAGES_CONFIG_VERSION
                            || shouldReplaceLanguageValue(current.get(path), language))) {
                        current.set(path, value);
                        copyComments(current, defaults, path);
                        changed++;
                    }
                }
            }
        }

        for (String path : List.of("formatting", "inline-replacements", "gradient-colors",
                "messages.inline-replacements")) {
            if (current.contains(path, true)) {
                current.set(path, null);
                changed++;
            }
        }
        return changed;
    }

    private static String normalizeLanguage(String value) {
        String normalized = value == null ? "en_US" : value.trim().replace('-', '_');
        return normalized.equalsIgnoreCase("zh_CN") || normalized.toLowerCase(Locale.ROOT).startsWith("zh")
                ? "zh_CN" : "en_US";
    }

    private static boolean shouldReplaceLanguageValue(Object currentValue, String targetLanguage) {
        if (currentValue == null) {
            return true;
        }
        if (currentValue instanceof String text) {
            boolean containsChinese = text.codePoints().anyMatch(codePoint ->
                    Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
            return "zh_CN".equals(targetLanguage) ? !containsChinese : containsChinese;
        }
        if (currentValue instanceof List<?> list) {
            return list.stream().anyMatch(value -> value instanceof String text
                    && shouldReplaceLanguageValue(text, targetLanguage));
        }
        return true;
    }

    private static boolean isOfficialLegacyMessage(String key, String text) {
        if (!isOfficialMessageKey(key)) {
            return false;
        }
        return text.contains("<dark_gray>") || text.contains("<dark_aqua>")
                || text.contains("<gray>") || text.contains("<green>")
                || text.contains("<yellow>") || text.contains("<red>")
                || text.contains("<aqua>") || text.contains("<gold>")
                || text.contains("<gradient>") || text.contains("━━━━")
                || text.contains("§") || text.contains("&");
    }

    private static boolean isOfficialLegacyMessage(String key, List<?> values) {
        if (!isOfficialMessageKey(key)) {
            return false;
        }
        return values.stream().anyMatch(value -> value instanceof String text && isOfficialLegacyMessage(key, text));
    }

    private static boolean isOfficialMessageKey(String key) {
        return switch (key) {
            case "plugin_enabled", "update_checking", "update_latest", "update_available", "update_downloaded",
                    "update_manual", "update_failed", "reload_success", "manual_cleanup_started", "manual_recreate_started", "no_permission",
                    "wrong_usage", "start_cleanup", "start_restore", "warning", "restore_warning",
                    "finish_cleanup", "finish_restore", "help_menu_player", "help_menu_admin", "updater" -> true;
            default -> false;
        };
    }

    /** Split the 1.14.0 help metadata row into the three KitLoader-style rows. */
    private static int migrateHelpMenuLayout(YamlConfiguration current, YamlConfiguration defaults,
                                             String versionPath, int currentVersion, int targetVersion) {
        if (versionPath == null || targetVersion < HELP_LAYOUT_CONFIG_VERSION
                || currentVersion >= HELP_LAYOUT_CONFIG_VERSION) {
            return 0;
        }

        int changed = 0;
        for (String menuKey : List.of("help_menu_player", "help_menu_admin")) {
            String path = "messages." + menuKey;
            List<String> currentMenu = current.getStringList(path);
            List<String> defaultMenu = defaults.getStringList(path);
            List<String> metadataRows = defaultMenu.stream()
                    .filter(ConfigurationUpdater::isHelpMetadataRow)
                    .toList();
            if (currentMenu.isEmpty() || metadataRows.size() < 2) {
                continue;
            }

            List<Object> migrated = new ArrayList<>();
            boolean replaced = false;
            for (String row : currentMenu) {
                if (!replaced && isLegacyHelpMetadataRow(row)) {
                    migrated.addAll(metadataRows);
                    replaced = true;
                } else {
                    migrated.add(row);
                }
            }
            if (replaced) {
                current.set(path, migrated);
                copyComments(current, defaults, path);
                changed++;
            }
        }
        return changed;
    }

    private static boolean isHelpMetadataRow(String row) {
        return row != null && (row.contains("{name}") || row.contains("{author}") || row.contains("{version}"));
    }

    private static boolean isLegacyHelpMetadataRow(String row) {
        if (row == null) {
            return false;
        }
        String plain = row.replaceAll("<[^>]*>", "").trim().replaceAll("\\s+", " ");
        return plain.equals("Plugin name: {name} | Author: {author} | Version: {version}")
                || plain.equals("插件名称：{name} | 作者：{author} | 版本：{version}");
    }

    private static String legacyToMiniMessage(String text) {
        StringBuilder result = new StringBuilder(text.length() + 16);
        for (int index = 0; index < text.length();) {
            char marker = text.charAt(index);
            if ((marker == '&' || marker == '§') && index + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(index + 1));
                if (code == '#' && index + 7 < text.length()
                        && text.substring(index + 2, index + 8).matches("[0-9a-fA-F]{6}")) {
                    result.append("<color:#").append(text, index + 2, index + 8).append('>');
                    index += 8;
                    continue;
                }
                if (code == 'x' && index + 13 < text.length()) {
                    StringBuilder hex = new StringBuilder(6);
                    boolean valid = true;
                    for (int offset = 0; offset < 6; offset++) {
                        int digit = index + 2 + offset * 2;
                        if (digit + 1 >= text.length() || text.charAt(digit) != marker
                                || Character.digit(text.charAt(digit + 1), 16) < 0) {
                            valid = false;
                            break;
                        }
                        hex.append(text.charAt(digit + 1));
                    }
                    if (valid) {
                        result.append("<color:#").append(hex).append('>');
                        index += 14;
                        continue;
                    }
                }
                String tag = switch (code) {
                    case '0' -> "<black>"; case '1' -> "<dark_blue>"; case '2' -> "<dark_green>";
                    case '3' -> "<dark_aqua>"; case '4' -> "<dark_red>"; case '5' -> "<dark_purple>";
                    case '6' -> "<gold>"; case '7' -> "<gray>"; case '8' -> "<dark_gray>";
                    case '9' -> "<blue>"; case 'a' -> "<green>"; case 'b' -> "<aqua>";
                    case 'c' -> "<red>"; case 'd' -> "<light_purple>"; case 'e' -> "<yellow>";
                    case 'f' -> "<white>"; case 'k' -> "<obfuscated>"; case 'l' -> "<bold>";
                    case 'm' -> "<strikethrough>"; case 'n' -> "<underlined>";
                    case 'o' -> "<italic>"; case 'r' -> "<reset>"; default -> null;
                };
                if (tag != null) {
                    result.append(tag);
                    index += 2;
                    continue;
                }
            }
            result.append(marker);
            index++;
        }
        return result.toString();
    }

    private static int refreshOfficialHeader(YamlConfiguration current, YamlConfiguration defaults,
                                             String versionPath, int currentVersion, int targetVersion,
                                             boolean forceRefresh) {
        if (versionPath == null || targetVersion < VISUAL_CONFIG_VERSION
                || (!forceRefresh && currentVersion >= VISUAL_CONFIG_VERSION)) {
            return 0;
        }
        List<String> header = configurationHeader(current);
        List<String> defaultsHeader = configurationHeader(defaults);
        if (header == null || defaultsHeader == null || defaultsHeader.isEmpty()
                || !looksLikeOfficialHeader(String.join("\n", header))) {
            return 0;
        }
        current.setComments(versionPath == null ? "config-version" : versionPath, new ArrayList<>(defaultsHeader));
        if (!defaults.options().getHeader().isEmpty()) {
            current.options().setHeader(new ArrayList<>(defaults.options().getHeader()));
        }
        return 1;
    }

    /** Refresh comments owned by the plugin when the administrator changes the config language. */
    private static int refreshLocalizedComments(YamlConfiguration current, YamlConfiguration defaults) {
        List<String> header = configurationHeader(current);
        if (header == null || !looksLikeOfficialHeader(String.join("\n", header))) {
            return 0;
        }

        int changed = 0;
        for (String path : defaults.getKeys(true)) {
            if (!current.contains(path, true)) {
                continue;
            }
            List<String> comments = defaults.getComments(path);
            List<String> inlineComments = defaults.getInlineComments(path);
            if (comments.isEmpty() && inlineComments.isEmpty()) {
                continue;
            }
            if (!comments.equals(current.getComments(path))) {
                current.setComments(path, new ArrayList<>(comments));
                changed++;
            }
            if (!inlineComments.equals(current.getInlineComments(path))) {
                current.setInlineComments(path, new ArrayList<>(inlineComments));
                changed++;
            }
        }
        return changed;
    }

    private static List<String> configurationHeader(YamlConfiguration configuration) {
        List<String> header = configuration.options().getHeader();
        if (header != null && !header.isEmpty()) {
            return header;
        }
        List<String> comments = configuration.getComments("config-version");
        return comments.isEmpty() ? configuration.getComments("config_version") : comments;
    }

    private static boolean looksLikeOfficialHeader(String header) {
        return header.contains("WorldAreaReset") || header.contains("WorldAreaReset English defaults")
                || header.contains("插件作者") || header.contains("After editing this file")
                || header.contains("修改本文件后");
    }

    private static String replaceLegacyDividerLines(String text) {
        String[] lines = text.split("\\R", -1);
        boolean changed = false;
        for (int index = 0; index < lines.length; index++) {
            if (isLegacyDividerLine(lines[index])) {
                lines[index] = STANDARD_DIVIDER;
                changed = true;
            }
        }
        return changed ? String.join("\n", lines) : text;
    }

    private static boolean isLegacyDividerLine(String line) {
        String plain = line
                .replaceAll("<[^>]*>", "")
                .replaceAll("(?i)(?:&|§)#[0-9a-f]{6}", "")
                .replaceAll("(?i)(?:&|§)x(?:(?:&|§)[0-9a-f]){6}", "")
                .replaceAll("(?i)(?:&|§)[0-9a-fk-or]", "")
                .trim();
        if (plain.isEmpty()) {
            return false;
        }
        if (plain.matches("-{3,}")) {
            return true;
        }
        if (!plain.contains("✦") && !plain.contains("✧")) {
            return false;
        }
        for (int offset = 0; offset < plain.length();) {
            int codePoint = plain.codePointAt(offset);
            if (codePoint != '━' && codePoint != '✦' && codePoint != '✧' && !Character.isWhitespace(codePoint)) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }
        return true;
    }

    private static int migrateCleanupInterval(YamlConfiguration current, YamlConfiguration defaults,
                                              String versionPath, int currentVersion, int targetVersion) {
        if (versionPath == null || targetVersion < CLEANUP_INTERVAL_CONFIG_VERSION
                || currentVersion >= CLEANUP_INTERVAL_CONFIG_VERSION
                || current.contains("cleanup.interval", true)
                || !current.contains("cleanup.interval_minutes", true)) {
            return 0;
        }

        long minutes = Math.max(1, current.getLong("cleanup.interval_minutes", 180));
        String unit = "minutes";
        long amount = minutes;
        if (minutes % (24L * 60L) == 0) {
            unit = "days";
            amount = minutes / (24L * 60L);
        } else if (minutes % 60L == 0) {
            unit = "hours";
            amount = minutes / 60L;
        }
        current.set("cleanup.interval", amount);
        current.set("cleanup.interval_unit", unit);
        current.set("cleanup.interval_minutes", null);
        copyComments(current, defaults, "cleanup.interval");
        copyComments(current, defaults, "cleanup.interval_unit");
        return 2;
    }

    private static int migrateWorldModules(YamlConfiguration current, YamlConfiguration defaults,
                                           String versionPath, int currentVersion, int targetVersion) {
        if (versionPath == null || targetVersion < WORLD_MODULE_CONFIG_VERSION
                || currentVersion >= WORLD_MODULE_CONFIG_VERSION) {
            return 0;
        }

        current.set("cleanup.worlds", migrateCleanupWorlds(current));
        current.set("recreate.worlds", migrateRecreateWorlds(current));
        removeLegacyPaths(current, List.of(
                "cleanup.world", "cleanup.world_bounds_enabled", "cleanup.world_bounds",
                "cleanup.world_regions", "cleanup.min_x", "cleanup.max_x", "cleanup.min_y",
                "cleanup.max_y", "cleanup.min_z", "cleanup.max_z",
                "recreate.world", "recreate.region_enabled", "recreate.regions", "recreate.whole_world",
                "recreate.min_x", "recreate.max_x", "recreate.min_y", "recreate.max_y",
                "recreate.min_z", "recreate.max_z", "recreate.template_folder"));
        copyComments(current, defaults, "cleanup.worlds");
        copyComments(current, defaults, "recreate.worlds");
        return 2;
    }

    private static List<Map<String, Object>> migrateCleanupWorlds(YamlConfiguration current) {
        Object configuredWorlds = current.get("cleanup.worlds");
        if (isWorldModuleList(configuredWorlds)) {
            return copyWorldModules((List<?>) configuredWorlds);
        }

        List<String> worldNames = stringList(configuredWorlds);
        if (worldNames.isEmpty()) {
            String world = current.getString("cleanup.world", "world_nether");
            if (world != null && !world.isBlank()) {
                worldNames.add(world.trim());
            }
        }

        List<Map<String, Object>> modules = new ArrayList<>();
        boolean perWorldBounds = current.getBoolean("cleanup.world_bounds_enabled", false);
        for (String worldName : distinct(worldNames)) {
            List<Map<String, Object>> regions = new ArrayList<>();
            if (perWorldBounds) {
                for (Map<?, ?> region : current.getMapList("cleanup.world_regions." + worldName)) {
                    regions.add(copyRegion(region));
                }
            }
            if (regions.isEmpty()) {
                String boundsPath = perWorldBounds
                        && current.isConfigurationSection("cleanup.world_bounds." + worldName)
                        ? "cleanup.world_bounds." + worldName : "cleanup";
                regions.add(boundsFromPaths(current, boundsPath, "cleanup"));
            }
            modules.add(worldModule(worldName, regions));
        }
        return modules;
    }

    private static List<Map<String, Object>> migrateRecreateWorlds(YamlConfiguration current) {
        Object configuredWorlds = current.get("recreate.worlds");
        if (isWorldModuleList(configuredWorlds)) {
            return copyWorldModules((List<?>) configuredWorlds);
        }

        List<Map<?, ?>> configuredRegions = current.getMapList("recreate.regions");
        List<String> worldNames = stringList(configuredWorlds);
        if (worldNames.isEmpty()) {
            for (Map<?, ?> region : configuredRegions) {
                Object world = region.get("world");
                if (world != null && !String.valueOf(world).isBlank()) {
                    worldNames.add(String.valueOf(world).trim());
                }
            }
        }
        if (worldNames.isEmpty()) {
            String world = current.getString("recreate.world", "");
            if (world != null && !world.isBlank()) {
                worldNames.add(world.trim());
            }
        }

        boolean regionEnabled = current.getBoolean("recreate.region_enabled", false);
        List<Map<String, Object>> modules = new ArrayList<>();
        for (String worldName : distinct(worldNames)) {
            List<Map<String, Object>> regions = new ArrayList<>();
            if (regionEnabled) {
                for (Map<?, ?> region : configuredRegions) {
                    Object regionWorld = region.get("world");
                    if (regionWorld == null || worldName.equals(String.valueOf(regionWorld))) {
                        regions.add(copyRegion(region));
                    }
                }
                if (regions.isEmpty() && hasBounds(current, "recreate")) {
                    regions.add(boundsFromPaths(current, "recreate", "recreate"));
                }
            }
            modules.add(worldModule(worldName, regions));
        }
        return modules;
    }

    private static boolean isWorldModuleList(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return false;
        }
        return list.stream().allMatch(entry -> entry instanceof Map<?, ?> map && map.containsKey("name"));
    }

    private static List<Map<String, Object>> copyWorldModules(List<?> configuredModules) {
        List<Map<String, Object>> modules = new ArrayList<>();
        for (Object configuredModule : configuredModules) {
            if (!(configuredModule instanceof Map<?, ?> module)) {
                continue;
            }
            Object name = module.get("name");
            if (name == null || String.valueOf(name).isBlank()) {
                continue;
            }
            List<Map<String, Object>> regions = new ArrayList<>();
            if (module.get("regions") instanceof List<?> configuredRegions) {
                for (Object configuredRegion : configuredRegions) {
                    if (configuredRegion instanceof Map<?, ?> region) {
                        regions.add(copyRegion(region));
                    }
                }
            }
            modules.add(worldModule(String.valueOf(name).trim(), regions));
        }
        return modules;
    }

    private static Map<String, Object> worldModule(String worldName, List<Map<String, Object>> regions) {
        Map<String, Object> module = new LinkedHashMap<>();
        module.put("name", worldName);
        module.put("regions", regions);
        return module;
    }

    private static Map<String, Object> copyRegion(Map<?, ?> configuredRegion) {
        Map<String, Object> region = new LinkedHashMap<>();
        for (String coordinate : coordinateNames()) {
            Object value = configuredRegion.get(coordinate);
            if (value != null) {
                region.put(coordinate, value);
            }
        }
        return region;
    }

    private static Map<String, Object> boundsFromPaths(YamlConfiguration current, String path,
                                                        String fallbackPath) {
        Map<String, Object> region = new LinkedHashMap<>();
        int[] fallbacks = {-200, 200, 0, 128, -200, 200};
        List<String> coordinates = coordinateNames();
        for (int index = 0; index < coordinates.size(); index++) {
            String coordinate = coordinates.get(index);
            int fallback = current.getInt(fallbackPath + "." + coordinate, fallbacks[index]);
            region.put(coordinate, current.getInt(path + "." + coordinate, fallback));
        }
        return region;
    }

    private static boolean hasBounds(YamlConfiguration current, String path) {
        return coordinateNames().stream().anyMatch(coordinate -> current.contains(path + "." + coordinate, true));
    }

    private static List<String> coordinateNames() {
        return List.of("min_x", "max_x", "min_y", "max_y", "min_z", "max_z");
    }

    private static List<String> stringList(Object value) {
        List<String> values = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof String string && !string.isBlank()) {
                    values.add(string.trim());
                }
            }
        }
        return values;
    }

    private static List<String> distinct(List<String> values) {
        return new ArrayList<>(new LinkedHashSet<>(values));
    }

    private static void removeLegacyPaths(YamlConfiguration current, List<String> paths) {
        for (String path : paths) {
            current.set(path, null);
        }
    }

    private static YamlConfiguration load(Path file) throws IOException, InvalidConfigurationException {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.options().parseComments(true);
        configuration.load(file.toFile());
        return configuration;
    }

    private static YamlConfiguration load(Reader reader) throws IOException, InvalidConfigurationException {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.options().parseComments(true);
        configuration.load(reader);
        return configuration;
    }

    private static List<String> findConflicts(YamlConfiguration current, YamlConfiguration defaults,
                                              List<String> defaultPaths, String versionPath) {
        Set<String> conflicts = new LinkedHashSet<>();

        for (String path : defaultPaths) {
            String blockingAncestor = findBlockingAncestor(current, path);
            if (blockingAncestor != null) {
                conflicts.add(blockingAncestor);
                continue;
            }

            if (path.equals(versionPath)) {
                continue;
            }
            if ("config-version".equals(versionPath) && "config_version".equals(path)) {
                continue;
            }

            if (!current.contains(path, true)) {
                continue;
            }

            boolean defaultSection = defaults.isConfigurationSection(path);
            boolean currentSection = current.isConfigurationSection(path);
            if (defaultSection != currentSection) {
                conflicts.add(path);
                continue;
            }

            if (!defaultSection && !compatibleValueTypes(current.get(path), defaults.get(path))) {
                conflicts.add(path);
            }
        }

        return List.copyOf(conflicts);
    }

    private static String findBlockingAncestor(YamlConfiguration current, String path) {
        int separator = path.indexOf('.');
        while (separator >= 0) {
            String ancestor = path.substring(0, separator);
            if (current.contains(ancestor, true) && !current.isConfigurationSection(ancestor)) {
                return ancestor;
            }
            separator = path.indexOf('.', separator + 1);
        }
        return null;
    }

    private static boolean compatibleValueTypes(Object currentValue, Object defaultValue) {
        if (currentValue == null || defaultValue == null) {
            return currentValue == defaultValue;
        }
        if (defaultValue instanceof Number) {
            return currentValue instanceof Number;
        }
        if (defaultValue instanceof List<?>) {
            return currentValue instanceof List<?>;
        }
        return defaultValue.getClass().isInstance(currentValue);
    }

    private static int mergeMissingPaths(YamlConfiguration current, YamlConfiguration defaults,
                                         List<String> defaultPaths, String versionPath) {
        int addedKeys = 0;

        for (String path : defaultPaths) {
            if (path.equals(versionPath)
                    || ("config-version".equals(versionPath) && "config_version".equals(path))
                    || current.contains(path, true)) {
                continue;
            }

            if (defaults.isConfigurationSection(path)) {
                current.createSection(path);
                copyComments(current, defaults, path);
                ConfigurationSection defaultSection = defaults.getConfigurationSection(path);
                if (defaultSection != null && defaultSection.getKeys(false).isEmpty()) {
                    addedKeys++;
                }
                continue;
            }

            current.set(path, defaults.get(path));
            copyComments(current, defaults, path);
            addedKeys++;
        }

        return addedKeys;
    }

    private static boolean updateVersion(YamlConfiguration current, YamlConfiguration defaults, int targetVersion,
                                         String versionPath, int currentVersion) {
        if (versionPath == null) {
            return false;
        }
        if (currentVersion >= targetVersion) {
            return false;
        }

        boolean wasMissing = !current.contains(versionPath, true);
        current.set(versionPath, targetVersion);
        if (wasMissing) {
            copyComments(current, defaults, versionPath);
        }
        return true;
    }

    private static void copyComments(ConfigurationSection target, ConfigurationSection defaults, String path) {
        List<String> comments = defaults.getComments(path);
        if (!comments.isEmpty()) {
            target.setComments(path, new ArrayList<>(comments));
        }

        List<String> inlineComments = defaults.getInlineComments(path);
        if (!inlineComments.isEmpty()) {
            target.setInlineComments(path, new ArrayList<>(inlineComments));
        }
    }

    private static void copyHeaderAndFooterWhenMissing(YamlConfiguration target, YamlConfiguration defaults) {
        if (target.options().getHeader().isEmpty() && !defaults.options().getHeader().isEmpty()) {
            target.options().setHeader(new ArrayList<>(defaults.options().getHeader()));
        }
        if (target.options().getFooter().isEmpty() && !defaults.options().getFooter().isEmpty()) {
            target.options().setFooter(new ArrayList<>(defaults.options().getFooter()));
        }
    }

    private static Path save(YamlConfiguration configuration, Path targetFile,
                             String releaseVersion, boolean createBackup, String versionPath,
                             int oldVersion, int newVersion) throws IOException {
        Path parent = targetFile.getParent();
        if (parent == null) {
            throw new IOException("Configuration file has no parent directory: " + targetFile);
        }

        Path temporaryFile = Files.createTempFile(parent, "." + targetFile.getFileName() + ".", ".tmp");
        try {
            configuration.save(temporaryFile.toFile());
            try (FileChannel channel = FileChannel.open(temporaryFile, StandardOpenOption.WRITE)) {
                channel.force(true);
            }

            Path backupFile = null;
            if (createBackup) {
                backupFile = versionPath == null
                        ? nextLegacyBackupPath(targetFile, releaseVersion)
                        : nextVersionBackupPath(targetFile, oldVersion, newVersion);
                Files.createDirectories(backupFile.getParent());
                Files.copy(targetFile, backupFile);
            }
            try {
                Files.move(temporaryFile, targetFile,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporaryFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return backupFile;
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private static Path nextLegacyBackupPath(Path targetFile, String releaseVersion) {
        String safeVersion = releaseVersion.replaceAll("[^A-Za-z0-9._-]", "_");
        String baseName = targetFile.getFileName() + ".before-v" + safeVersion + ".bak";
        Path parent = targetFile.getParent();
        Path candidate = parent.resolve(baseName);
        int suffix = 1;
        while (Files.exists(candidate)) {
            candidate = parent.resolve(baseName + "." + suffix++);
        }
        return candidate;
    }

    private static Path nextVersionBackupPath(Path targetFile, int oldVersion, int newVersion) {
        Path backupDirectory = targetFile.getParent().resolve("config-backups");
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmssSSS")
                .withZone(ZoneOffset.UTC).format(Instant.now());
        String baseName = "config-v" + (oldVersion < 0 ? "unknown" : oldVersion)
                + "-to-v" + newVersion + "-" + timestamp + ".yml";
        Path candidate = backupDirectory.resolve(baseName);
        int suffix = 1;
        while (Files.exists(candidate)) {
            candidate = backupDirectory.resolve(baseName.replace(".yml", "-" + suffix++ + ".yml"));
        }
        return candidate;
    }

    private static int pathDepth(String path) {
        return (int) path.chars().filter(character -> character == '.').count();
    }

    record UpdateResult(int addedKeys, boolean versionUpdated, Path backupFile, List<String> conflicts) {

        UpdateResult {
            conflicts = List.copyOf(conflicts);
        }

        boolean changed() {
            return addedKeys > 0 || versionUpdated;
        }

        boolean blocked() {
            return !conflicts.isEmpty();
        }
    }
}
