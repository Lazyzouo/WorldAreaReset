package net.lazyz.worldareareset;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.StringUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Level;

public class WorldAreaResetPlugin extends JavaPlugin {

    private static final String HELP_DIVIDER = "<gradient:#FFB7D5:#D7C7FF:#B9E7FF:#D7C7FF:#FFB7D5><bold><strikethrough>---------<bold><strikethrough>---------<bold> ✧ <bold><strikethrough>---------<bold><strikethrough>---------</gradient>";
    private static final int MIN_STARTUP_BANNER_WIDTH = 60;
    private static final String DEFAULT_PREFIX = "<color:#8A2387><bold>[</bold><color:#E62028><bold>WorldAreaReset</bold></color><color:#8A2387><bold>]</bold></color> <color:#555555><bold>»</bold></color> <color:#B9E7FF>";
    private static final String PREFIX_BRACKET_COLOR = "<#8A2387>";
    private static final String PREFIX_NAME_COLOR = "<#E62028>";
    private static final String PREFIX_ARROW_COLOR = "<#555555>";
    private static final String PREFIX_MESSAGE_COLOR = "<#B9E7FF>";
    private static final String CONSOLE_INFO_COLOR = "<#D7C7FF>";
    private static final String CONSOLE_SUCCESS_COLOR = "<#B9E7FF>";
    private static final String CONSOLE_WARNING_COLOR = "<#FFB7D5>";
    private static final String CONSOLE_ERROR_COLOR = "<#E62028>";
    private static final String BANNER_BORDER_COLOR = "<#8A2387>";
    private static final String BANNER_SEPARATOR_COLOR = "<#D7C7FF>";
    private static final String BANNER_TITLE_COLOR = "<#E62028>";
    private static final String BANNER_LABEL_COLOR = "<#D7C7FF>";
    private static final String BANNER_VALUE_COLOR = "<#B9E7FF>";
    private static final String BANNER_NOTICE_COLOR = "<#FF69B4>";
    private static final List<String> DEFAULT_GRADIENT = List.of(
            "#FFB7D5", "#D7C7FF", "#B9E7FF", "#D7C7FF", "#FFB7D5");
    private static final String LEGACY_STATUS_GRADIENT =
            "<gradient:#FFB7D5:#D7C7FF:#B9E7FF:#D7C7FF:#FFB7D5>";
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final ConcurrentHashMap<String, String> COLOR_CACHE = new ConcurrentHashMap<>();
    private static final int MAX_COLOR_CACHE_ENTRIES = 2048;
    private static final String ESCAPED_MARKUP_MARKER = "\uE000";
    private static final String GUI_PINK = "§x§F§F§B§7§D§5";
    private static final String GUI_LIGHT_PURPLE = "§x§D§7§C§7§F§F";
    private static final String GUI_SUCCESS = "§x§5§5§F§F§5§5";
    private static final String GUI_WARNING = "§x§F§F§F§F§5§5";
    private static final String GUI_INFO = "§x§0§0§D§2§F§F";
    private static final String GUI_ERROR = "§x§F§F§5§5§5§5";
    private static final Pattern MINI_MESSAGE_TAG = Pattern.compile(
            "(?i)</?(?:gradient(?::[^>]+)?|color(?::[^>]+)?|bold|b|italic|i|"
                    + "underlined|u|strikethrough|st|obfuscated|obf|reset|r|br)/?>|<#[0-9a-f]{6}>");
    /**
     * Server panels commonly render only the 16 legacy section colors. Keep
     * the exact Hex colors for players, but downgrade console-only components
     * to visible legacy equivalents instead of letting #FFB7D5 become white.
     */
    private static final LegacyComponentSerializer CONSOLE_SERIALIZER = LegacyComponentSerializer.legacySection();
    private static final LegacyComponentSerializer SECTION_SERIALIZER = LegacyComponentSerializer.legacySection();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.builder()
            .character('&').hexColors().build();

    private AreaCleanupTask cleanupTask;
    private LanguageManager languageManager;
    private UpdateChecker updateChecker;
    private int startupBannerWidth = MIN_STARTUP_BANNER_WIDTH;

    @Override
    public void onEnable() {
        INSTANCE = this;
        clearColorCache();
        saveDefaultConfig();
        if (!updateConfiguration(true)) {
            return;
        }
        ensureTemplateDirectory();
        languageManager = new LanguageManager(this);

        cleanupTask = new AreaCleanupTask(this);
        cleanupTask.start();

        if (getCommand("war") != null) {
            getCommand("war").setExecutor(this);
        }

        printStartupBanner();
        logLocalized("plugin_enabled", "WorldAreaReset v{version} by {author} started successfully on Paper/Folia.");
        updateChecker = new UpdateChecker(this);
        updateChecker.checkOnStartup();
    }

    private boolean updateConfiguration(boolean disableOnFailure) {
        try {
            File configFile = new File(getDataFolder(), "config.yml");
            if (!configFile.isFile()) {
                saveDefaultConfig();
                if (!configFile.isFile()) {
                    throw new IOException("Configuration file could not be created: "
                            + configFile.getAbsolutePath());
                }
                getLogger().warning("config.yml was missing and has been recreated from bundled defaults.");
            }

            YamlConfiguration current = loadConfiguration(configFile);
            String language = normalizeLanguage(current.getString("language", "en_US"));
            String resourceName = configurationResource(language);
            boolean refreshComments = shouldRefreshLocalizedConfig(current, resourceName, language);

            try (InputStream defaultsStream = getResource(resourceName)) {
                if (defaultsStream == null) {
                    throw new IllegalStateException("Bundled " + resourceName + " is missing");
                }

                try (Reader defaultsReader = new InputStreamReader(defaultsStream, StandardCharsets.UTF_8)) {
                    ConfigurationUpdater.UpdateResult result = ConfigurationUpdater.mergeMissingValues(
                            configFile.toPath(), defaultsReader,
                            "config-version", getPluginMeta().getVersion(), true, refreshComments);
                    if (result.blocked()) {
                        logConfigurationFailure(disableOnFailure,
                                "Configuration update stopped because existing values conflict with the official structure: "
                                        + String.join(", ", result.conflicts()));
                        return false;
                    }

                    reloadConfig();
                    logConfigurationUpdate(resourceName, result);
                    return true;
                }
            }
        } catch (Exception error) {
            logConfigurationFailure(disableOnFailure,
                    "Could not safely update config.yml. The original file was left unchanged.", error);
            return false;
        }
    }

    private YamlConfiguration loadConfiguration(File configFile)
            throws IOException, InvalidConfigurationException {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.options().parseComments(true);
        configuration.load(configFile);
        return configuration;
    }

    private boolean shouldRefreshLocalizedConfig(YamlConfiguration current, String resourceName, String language)
            throws IOException, InvalidConfigurationException {
        String applied = normalizeLanguage(current.getString("language-applied", ""));
        if (!language.equals(applied)) {
            return true;
        }

        try (InputStream defaultsStream = getResource(resourceName)) {
            if (defaultsStream == null) {
                return false;
            }
            YamlConfiguration defaults = new YamlConfiguration();
            defaults.options().parseComments(true);
            try (Reader reader = new InputStreamReader(defaultsStream, StandardCharsets.UTF_8)) {
                defaults.load(reader);
            }
            List<String> currentHeader = configurationHeader(current);
            List<String> defaultHeader = configurationHeader(defaults);
            return !defaultHeader.isEmpty() && !defaultHeader.equals(currentHeader);
        }
    }

    private static List<String> configurationHeader(YamlConfiguration configuration) {
        List<String> header = configuration.options().getHeader();
        if (header != null && !header.isEmpty()) {
            return header;
        }
        List<String> comments = configuration.getComments("config-version");
        return comments.isEmpty() ? configuration.getComments("config_version") : comments;
    }

    static String configurationResource(String language) {
        return "zh_CN".equals(normalizeLanguage(language)) ? "config-zh_CN.yml" : "config.yml";
    }

    private static String normalizeLanguage(String value) {
        String normalized = value == null ? "en_US" : value.trim().replace('-', '_');
        if (normalized.equalsIgnoreCase("zh_CN") || normalized.toLowerCase(Locale.ROOT).startsWith("zh")) {
            return "zh_CN";
        }
        return "en_US";
    }

    private void logConfigurationFailure(boolean disablePlugin, String message) {
        logConfigurationFailure(disablePlugin, message, null);
    }

    private void logConfigurationFailure(boolean disablePlugin, String message, Throwable error) {
        Level level = disablePlugin ? Level.SEVERE : Level.WARNING;
        if (error == null) {
            getLogger().log(level, message + (disablePlugin
                    ? " Plugin startup is stopped."
                    : " Keeping the current plugin state."));
        } else {
            getLogger().log(level, message + (disablePlugin
                    ? " Plugin startup is stopped."
                    : " Keeping the current plugin state."), error);
        }
        if (disablePlugin) {
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void ensureTemplateDirectory() {
        File templateDirectory = new File(getDataFolder(), "templates");
        if (!templateDirectory.isDirectory() && !templateDirectory.mkdirs()) {
            getLogger().warning("无法创建地形模板目录: " + templateDirectory.getAbsolutePath());
            return;
        }
        getLogger().info("地形模板目录 / Terrain template directory: " + templateDirectory.getAbsolutePath());
    }

    void logConfigurationUpdate(String resourceName, ConfigurationUpdater.UpdateResult result) {
        if (result.blocked()) {
            getLogger().warning("Skipped automatic update for " + resourceName + " because of conflicting values: "
                    + String.join(", ", result.conflicts()));
            return;
        }
        if (!result.changed()) {
            return;
        }

        Path backup = result.backupFile();
        String backupName = backup == null
                ? "disabled"
                : getDataFolder().toPath().toAbsolutePath().normalize()
                .relativize(backup.toAbsolutePath().normalize()).toString();
        getLogger().info("Configuration update / 配置自动更新: " + resourceName
                + ", added missing keys: " + result.addedKeys()
                + ", version marker updated: " + result.versionUpdated()
                + ", backup: " + backupName);
    }

    void saveConfigAtomically() throws IOException {
        Path target = new File(getDataFolder(), "config.yml").toPath().toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null) {
            throw new IOException("Configuration file has no parent directory: " + target);
        }
        Path temporary = Files.createTempFile(parent, ".config.yml.", ".tmp");
        try {
            getConfig().save(temporary.toFile());
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @Override
    public void onDisable() {
        if (cleanupTask != null) {
            cleanupTask.stop();
        }
        if (INSTANCE == this) INSTANCE = null;
    }

    /**
     * Parses MiniMessage (the configuration format) and legacy ampersand/section
     * codes into the section-code string accepted by Bukkit metadata and chat.
     */
    public static String color(String message) {
        if (message == null) return null;
        String original = message;
        if (message.length() <= 512) {
            String cached = COLOR_CACHE.get(message);
            if (cached != null) return cached;
        }
        WorldAreaResetPlugin active = INSTANCE;
        if (active != null && active.languageManager != null) {
            message = active.languageManager.expandConfiguredTokens(active.languageManager.translateInline(message));
        } else {
            message = message.replace("{gradient}", "<gradient:" + String.join(":", DEFAULT_GRADIENT) + ">");
        }
        String protectedMessage = message.replace("\\<", ESCAPED_MARKUP_MARKER);
        if (containsMiniMessageTag(protectedMessage)) {
            try {
                Component component = MINI_MESSAGE.deserialize(legacyToMiniMessageStatic(protectedMessage));
                String result = stripUnparsedMiniMessageTags(SECTION_SERIALIZER.serialize(component))
                        .replace(ESCAPED_MARKUP_MARKER, "<");
                cacheColor(original, result);
                return result;
            } catch (RuntimeException | LinkageError ignored) {
                // Fall through to the legacy parser for malformed custom text.
            }
        }
        String result = stripUnparsedMiniMessageTags(translateLegacyColors(protectedMessage))
                .replace(ESCAPED_MARKUP_MARKER, "<");
        cacheColor(original, result);
        return result;
    }

    private static volatile WorldAreaResetPlugin INSTANCE;

    private static void cacheColor(String source, String result) {
        if (source == null || result == null || source.length() > 512) return;
        if (COLOR_CACHE.size() >= MAX_COLOR_CACHE_ENTRIES) COLOR_CACHE.clear();
        COLOR_CACHE.putIfAbsent(source, result);
    }

    static void clearColorCache() {
        COLOR_CACHE.clear();
    }

    /** Compact GUI text to one semantic RGB color, matching Kitloader's metadata safety rule. */
    public static String colorForGui(String message) {
        return compactGuiColors(color(message));
    }

    /** Compacts an already serialized legacy string without translating user text. */
    static String compactLegacyGuiText(String message) {
        return compactGuiColors(message);
    }

    private static String compactGuiColors(String text) {
        if (text == null || text.isEmpty()) return text;

        String paletteColor = findGuiPaletteColor(text);
        if (paletteColor == null) paletteColor = GUI_PINK;

        StringBuilder compact = new StringBuilder(text.length() + paletteColor.length());
        compact.append(paletteColor);
        boolean bold = false;
        boolean italic = false;
        boolean underlined = false;
        boolean strikethrough = false;
        boolean obfuscated = false;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current != '\u00a7' || index + 1 >= text.length()) {
                compact.append(current);
                continue;
            }

            char code = text.charAt(index + 1);
            if (code == 'x' || code == 'X') {
                int end = index + 14;
                if (end <= text.length() && isLegacyHex(text, index)) {
                    index = end - 1;
                    bold = false;
                    italic = false;
                    underlined = false;
                    strikethrough = false;
                    obfuscated = false;
                    continue;
                }
            }
            if (isLegacyColorCode(code)) {
                index++;
                bold = false;
                italic = false;
                underlined = false;
                strikethrough = false;
                obfuscated = false;
                continue;
            }

            if (code == 'r' || code == 'R') {
                compact.append(current).append(code).append(paletteColor);
                bold = false;
                italic = false;
                underlined = false;
                strikethrough = false;
                obfuscated = false;
                index++;
                continue;
            }

            boolean alreadyActive = switch (Character.toLowerCase(code)) {
                case 'l' -> bold;
                case 'o' -> italic;
                case 'n' -> underlined;
                case 'm' -> strikethrough;
                case 'k' -> obfuscated;
                default -> false;
            };
            if (!alreadyActive) {
                compact.append(current).append(code);
                switch (Character.toLowerCase(code)) {
                    case 'l' -> bold = true;
                    case 'o' -> italic = true;
                    case 'n' -> underlined = true;
                    case 'm' -> strikethrough = true;
                    case 'k' -> obfuscated = true;
                    default -> { }
                }
            }
            index++;
        }
        return compact.toString();
    }

    private static String findGuiPaletteColor(String text) {
        String selected = null;
        int selectedPriority = -1;
        for (int index = 0; index + 1 < text.length(); index++) {
            if (text.charAt(index) != '\u00a7') continue;
            char code = text.charAt(index + 1);
            if (code == 'x' || code == 'X') {
                int end = index + 14;
                if (end <= text.length() && isLegacyHex(text, index)) {
                    String candidate = guiPaletteColor(text.substring(index, end));
                    int priority = guiPalettePriority(candidate);
                    if (priority > selectedPriority) {
                        selected = candidate;
                        selectedPriority = priority;
                    }
                    index = end - 1;
                }
            } else if (isLegacyColorCode(code)) {
                String candidate = guiPaletteColor(text.substring(index, index + 2));
                int priority = guiPalettePriority(candidate);
                if (priority > selectedPriority) {
                    selected = candidate;
                    selectedPriority = priority;
                }
                index++;
            }
        }
        return selected;
    }

    private static int guiPalettePriority(String color) {
        if (GUI_ERROR.equals(color)) return 4;
        if (GUI_SUCCESS.equals(color)) return 3;
        if (GUI_WARNING.equals(color)) return 2;
        if (GUI_INFO.equals(color)) return 1;
        return 0;
    }

    private static String guiPaletteColor(String legacyColor) {
        if (legacyColor == null || legacyColor.length() < 2) return GUI_PINK;
        int red;
        int green;
        int blue;
        if (legacyColor.charAt(1) == 'x' || legacyColor.charAt(1) == 'X') {
            if (legacyColor.length() < 14) return GUI_PINK;
            red = Character.digit(legacyColor.charAt(3), 16) * 16
                    + Character.digit(legacyColor.charAt(5), 16);
            green = Character.digit(legacyColor.charAt(7), 16) * 16
                    + Character.digit(legacyColor.charAt(9), 16);
            blue = Character.digit(legacyColor.charAt(11), 16) * 16
                    + Character.digit(legacyColor.charAt(13), 16);
        } else {
            char code = Character.toLowerCase(legacyColor.charAt(1));
            return switch (code) {
                case 'a' -> GUI_SUCCESS;
                case 'e', '6' -> GUI_WARNING;
                case 'b', '3', '9' -> GUI_INFO;
                case 'c', '4' -> GUI_ERROR;
                default -> GUI_PINK;
            };
        }
        if (green > 150 && green >= blue && green >= red + 48) return GUI_SUCCESS;
        if (red >= 200 && green >= 150 && blue < 150) return GUI_WARNING;
        if (red >= green + 80 && red >= blue + 80) return GUI_ERROR;
        if (blue >= red + 16 || (blue >= red && green >= red)) return GUI_INFO;
        return GUI_PINK;
    }

    private static boolean isLegacyHex(String text, int start) {
        if (start + 14 > text.length() || (text.charAt(start + 1) != 'x'
                && text.charAt(start + 1) != 'X')) return false;
        for (int offset = 2; offset < 14; offset += 2) {
            if (text.charAt(start + offset) != '\u00a7'
                    || Character.digit(text.charAt(start + offset + 1), 16) < 0) return false;
        }
        return true;
    }

    private static boolean isLegacyColorCode(char code) {
        return code >= '0' && code <= '9'
                || code >= 'a' && code <= 'f'
                || code >= 'A' && code <= 'F';
    }

    private static boolean containsMiniMessageTag(String text) {
        return text != null && (text.matches("(?s).*<(?:#[0-9a-fA-F]{6}|gradient(?::[^>]+)?|color(?::[^>]+)?|bold|b|italic|i|underlined|u|strikethrough|st|obfuscated|obf|reset|r|br)(?:/?>|>).*" )
                || text.matches("(?s).*</(?:gradient|color|bold|italic|underlined|strikethrough|obfuscated|b|i|u|st|obf)>.*"));
    }

    private static String stripUnparsedMiniMessageTags(String text) {
        if (text == null || text.isEmpty()) return text;
        Matcher matcher = MINI_MESSAGE_TAG.matcher(text);
        StringBuffer cleaned = new StringBuffer();
        while (matcher.find()) {
            int slashCount = 0;
            for (int index = matcher.start() - 1; index >= 0 && text.charAt(index) == '\\'; index--) slashCount++;
            matcher.appendReplacement(cleaned, (slashCount & 1) == 1
                    ? Matcher.quoteReplacement(matcher.group()) : "");
        }
        matcher.appendTail(cleaned);
        return cleaned.toString();
    }

    private static String translateLegacyColors(String text) {
        Matcher matcher = Pattern.compile("&#[a-fA-F0-9]{6}").matcher(text);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group().substring(2);
            StringBuilder legacy = new StringBuilder("&x");
            for (char digit : hex.toCharArray()) legacy.append('&').append(digit);
            matcher.appendReplacement(output, Matcher.quoteReplacement(legacy.toString()));
        }
        matcher.appendTail(output);
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', output.toString());
    }

    private static String legacyToMiniMessageStatic(String text) {
        StringBuilder result = new StringBuilder(text.length() + 16);
        for (int index = 0; index < text.length();) {
            char marker = text.charAt(index);
            if ((marker == '&' || marker == '\u00a7') && index + 1 < text.length()) {
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
                        int markerIndex = index + 2 + offset * 2;
                        if (markerIndex + 1 >= text.length() || text.charAt(markerIndex) != marker
                                || Character.digit(text.charAt(markerIndex + 1), 16) < 0) {
                            valid = false;
                            break;
                        }
                        hex.append(text.charAt(markerIndex + 1));
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

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("war")) {
            return false;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!hasAdminPermission(sender)) {
                sendPrefixed(sender, "no_permission",
                        "<color:#E62028><bold>Permission denied: you cannot use this command.</bold></color>");
                return true;
            }

            if (!updateConfiguration(false)) {
                return true;
            }
            languageManager.reload();
            clearColorCache();
            cleanupTask.stop();
            cleanupTask.start();

            sendPrefixed(sender, "reload_success",
                    "<color:#B9E7FF><bold>Success: configuration reloaded and cleanup schedule restarted.</bold></color>");
            return true;
        }

        if (args[0].equalsIgnoreCase("cleanup")) {
            if (!hasAdminPermission(sender)) {
                sendPrefixed(sender, "no_permission",
                        "<color:#E62028><bold>Permission denied: you cannot use this command.</bold></color>");
                return true;
            }

            cleanupTask.runManualCleanup();
            String interval = cleanupTask.cleanupIntervalDescription();
            sendPrefixed(sender, "manual_cleanup_started",
                    "<color:#D7C7FF><bold>Cleanup countdown started. The automatic cleanup timer was reset to {interval}.</bold></color>",
                    "{interval}", interval);
            return true;
        }

        if (args[0].equalsIgnoreCase("recreate")) {
            if (!hasAdminPermission(sender)) {
                sendPrefixed(sender, "no_permission",
                        "<color:#E62028><bold>Permission denied: you cannot use this command.</bold></color>");
                return true;
            }

            cleanupTask.runManualRecreate();
            String interval = cleanupTask.recreateIntervalDescription();
            sendPrefixed(sender, "manual_recreate_started",
                    "<color:#D7C7FF><bold>Restoration countdown started. The automatic timer was reset to {interval}.</bold></color>",
                    "{interval}", interval);
            return true;
        }

        sendPrefixed(sender, "wrong_usage",
                "<color:#FFB7D5><bold>Unknown command. Use /war help for help.</bold></color>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("war") || args.length != 1) {
            return Collections.emptyList();
        }

        List<String> options = new ArrayList<>();
        options.add("help");
        if (hasAdminPermission(sender)) {
            options.add("cleanup");
            options.add("recreate");
            options.add("reload");
        }

        List<String> completions = new ArrayList<>();
        StringUtil.copyPartialMatches(args[0], options, completions);
        Collections.sort(completions);
        return completions;
    }

    String message(String key, String fallback, String... replacements) {
        String message = languageManager.text(key, fallback);
        return replaceVariables(message, replacements);
    }

    /** Sends a localized message using the same severity and prefix contract as Kitloader. */
    public void sendMsg(CommandSender sender, String key, String... placeholders) {
        if (sender == null || languageManager == null) return;
        String prefix = languageManager.getMessageString("prefix", DEFAULT_PREFIX);
        String author = getPluginMeta().getAuthors().isEmpty() ? "Unknown" : getPluginMeta().getAuthors().get(0);
        String name = getPluginMeta().getName();
        Object configured = languageManager.getMessage(key);
        if (configured instanceof List<?> list) {
            if (list.isEmpty()) return;
            for (Object value : list) {
                if (value == null || String.valueOf(value).trim().isEmpty()) continue;
                String line = replaceVariables(String.valueOf(value), placeholders)
                        .replace("{prefix}", prefix).replace("{version}", getPluginMeta().getVersion())
                        .replace("{author}", author).replace("{name}", name);
                sendGameMessage(sender, colorLegacyStatusMessage(key, line), key);
            }
            return;
        }
        String line = configured instanceof String value ? value : null;
        if (line == null || line.trim().isEmpty()) return;
        line = replaceVariables(line, placeholders)
                .replace("{prefix}", prefix).replace("{version}", getPluginMeta().getVersion())
                .replace("{author}", author).replace("{name}", name);
        sendGameMessage(sender, colorLegacyStatusMessage(key, line), key);
    }

    public void sendGameMessage(CommandSender sender, String message) {
        sendGameMessage(sender, message, null);
    }

    private void sendGameMessage(CommandSender sender, String message, String severityKey) {
        if (sender == null || message == null) return;
        for (String line : message.split("\\R", -1)) {
            String formatted = isChatDivider(line) ? color(line) : colorSingleTone(line, severityKey);
            sender.sendMessage(sender instanceof org.bukkit.entity.Player ? leftAlignGameText(formatted) : formatted);
        }
    }

    static String leftAlignGameText(String text) {
        if (text == null || text.isEmpty()) return text;
        StringBuilder formatting = new StringBuilder();
        int index = 0;
        while (index < text.length()) {
            if (text.charAt(index) == '\u00a7' && index + 1 < text.length()) {
                formatting.append(text, index, index + 2);
                index += 2;
                continue;
            }
            int codePoint = text.codePointAt(index);
            if (!Character.isWhitespace(codePoint)) break;
            index += Character.charCount(codePoint);
        }
        return formatting.append(text, index, text.length()).toString();
    }

    private static String colorSingleTone(String message, String severityKey) {
        if (message == null || message.isEmpty()) return message;
        String parsed = color(message);
        String plain = org.bukkit.ChatColor.stripColor(parsed);
        if (plain == null) plain = parsed;
        plain = stripUnparsedMiniMessageTags(plain);
        String tone = isYellowHelpSectionLabel(severityKey, plain)
                ? GUI_WARNING
                : isHelpMenu(severityKey)
                ? GUI_LIGHT_PURPLE
                : severityKey == null ? inferGameMessageColor(message, plain)
                : gameMessageColorForKey(severityKey);
        PrefixSplit prefix = splitGamePrefix(plain);
        return prefix == null ? tone + plain : renderGamePrefix() + tone + prefix.body();
    }

    private static boolean isHelpMenu(String severityKey) {
        return "help_menu_admin".equals(severityKey) || "help_menu_player".equals(severityKey);
    }

    private static boolean isYellowHelpSectionLabel(String severityKey, String plain) {
        if (!isHelpMenu(severityKey)) return false;
        String role = helpHeaderRole(plain);
        return "player".equals(role) || "admin".equals(role);
    }

    /** Mirrors Kitloader's role detection so only help section headings stay yellow. */
    private static String helpHeaderRole(String line) {
        if (line == null) return null;
        String plain = line
                .replaceAll("(?i)</?(?:gradient|color)(?::[^>]+)?>", "")
                .replaceAll("(?i)</?(?:bold|italic|underlined|strikethrough|obfuscated|reset)>", "")
                .replaceAll("(?i)<#[0-9a-f]{6}>", "")
                .replaceAll("(?i)[&§][0-9a-fk-or]", "")
                .toLowerCase(Locale.ROOT);
        if (plain.contains("/")) return null;
        if (plain.contains("plugin name") || plain.contains("插件名称")
                || (plain.contains("worldareareset") && plain.contains("{version}"))) return "metadata";
        if (plain.contains("player commands") || plain.contains("玩家可用指令")
                || plain.contains("玩家指令") || plain.contains("basic commands")
                || plain.contains("基础指令")) return "player";
        if (plain.contains("administrator commands") || plain.contains("管理员可用指令")
                || plain.contains("admin commands") || plain.contains("管理员指令")) return "admin";
        return null;
    }

    private static String gameMessageColorForKey(String key) {
        String severity = messageSeverityColor(key);
        if (isHelpMenu(key)) return GUI_LIGHT_PURPLE;
        if (severity == null) return GUI_SUCCESS;
        if ("#55FF55".equalsIgnoreCase(severity)) return GUI_SUCCESS;
        if ("#FFFF55".equalsIgnoreCase(severity)) return GUI_WARNING;
        if ("#00D2FF".equalsIgnoreCase(severity)) return GUI_WARNING;
        return GUI_ERROR;
    }

    private static String messageSeverityColor(String key) {
        return switch (key) {
            case "plugin_enabled", "update_latest", "update_downloaded", "reload_success",
                    "finish_cleanup", "finish_restore" -> "#55FF55";
            case "update_checking", "update_available", "update_manual", "reload_started",
                    "reload_in_progress", "start_cleanup", "start_restore", "manual_cleanup_started",
                    "manual_recreate_started", "warning", "restore_warning", "naming_instructions",
                    "naming_limits" -> "#FFFF55";
            case "prefix", "help_menu_player", "help_menu_admin" -> null;
            case "update_failed", "reload_failed", "no_permission", "wrong_usage" -> "#FF5555";
            default -> "#FF5555";
        };
    }

    static String colorLegacyStatusMessage(String key, String message) {
        if (message == null || key == null || key.equals("naming_instructions")
                || key.startsWith("help_menu_")) return message;
        String severity = messageSeverityColor(key);
        if (severity == null || !message.startsWith(LEGACY_STATUS_GRADIENT)) return message;
        int open = message.indexOf('>');
        int close = message.lastIndexOf("</gradient>");
        if (open < 0 || close <= open || close + "</gradient>".length() != message.length()) return message;
        return "<color:" + severity + ">" + message.substring(open + 1, close) + "</color>";
    }

    private static PrefixSplit splitGamePrefix(String plain) {
        String name = "[WorldAreaReset]";
        if (plain == null || plain.length() < name.length()
                || !plain.regionMatches(true, 0, name, 0, name.length())) return null;
        int index = name.length();
        while (index < plain.length() && Character.isWhitespace(plain.charAt(index))) index++;
        if (index < plain.length() && (plain.charAt(index) == '»' || plain.charAt(index) == '>')) {
            index++;
            while (index < plain.length() && Character.isWhitespace(plain.charAt(index))) index++;
        }
        return new PrefixSplit(plain.substring(index));
    }

    private static String renderGamePrefix() {
        return color(PREFIX_BRACKET_COLOR + "<bold>[</bold>" + PREFIX_NAME_COLOR + "<bold>WorldAreaReset</bold>"
                + PREFIX_BRACKET_COLOR + "<bold>]</bold> " + PREFIX_ARROW_COLOR + "<bold>»</bold> ");
    }

    private record PrefixSplit(String body) {
    }

    private static String inferGameMessageColor(String source, String plain) {
        String value = source == null ? "" : source.toLowerCase(Locale.ROOT);
        if (containsAny(value, "#ff5555", "#ff5e62", "&c", "§c", "失败", "错误", "拒绝",
                "禁止", "没有", "不存在", "无法", "已满", "仅限", "格式错误")) return GUI_ERROR;
        if (containsAny(value, "#ffff55", "#f2c94c", "&e", "§e", "警告", "提示", "等待",
                "限制", "尚未", "只读", "暂时")) return GUI_WARNING;
        if (containsAny(value, "#55ff55", "#a8ff78", "#00b09b", "&a", "§a", "成功", "已成功",
                "完成", "加载", "保存", "发布", "命名", "重命名", "恢复", "公开")) return GUI_SUCCESS;
        return GUI_SUCCESS;
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) if (text.contains(needle)) return true;
        return false;
    }

    private static boolean isChatDivider(String line) {
        if (line == null || line.isBlank()) return false;
        String plain = org.bukkit.ChatColor.stripColor(color(line));
        if (plain == null) plain = line;
        String compact = plain.replaceAll("\\s+", "");
        return compact.length() >= 8 && compact.matches("[-_=✧✦*━]+")
                && (compact.contains("-") || compact.contains("=") || compact.contains("✧") || compact.contains("✦"));
    }

    void logLocalized(String key, String fallback, String... replacements) {
        String body = message(key, fallback, replacements)
                .replace("{version}", getPluginMeta().getVersion())
                .replace("{author}", getPluginMeta().getAuthors().isEmpty()
                        ? "Unknown"
                        : getPluginMeta().getAuthors().get(0))
                .replace("{prefix}", "");
        String statusColor = switch (key) {
            case "update_checking" -> CONSOLE_INFO_COLOR;
            case "update_latest", "update_downloaded", "plugin_enabled" -> CONSOLE_SUCCESS_COLOR;
            case "update_available", "update_manual" -> CONSOLE_WARNING_COLOR;
            case "update_failed" -> CONSOLE_ERROR_COLOR;
            default -> PREFIX_MESSAGE_COLOR;
        };
        boolean emphasize = key.equals("update_available")
                || key.equals("update_manual")
                || key.equals("update_failed");
        logConsole(consoleBodyMarkup(body, statusColor, emphasize));
    }

    void broadcastInfo(String prefix, String message) {
        broadcastToPlayersAndConsole(prefix, message, CONSOLE_INFO_COLOR, false, "start_cleanup");
    }

    void broadcastSuccess(String prefix, String message) {
        broadcastToPlayersAndConsole(prefix, message, CONSOLE_SUCCESS_COLOR, false, "finish_cleanup");
    }

    void broadcastWarning(String prefix, String message) {
        broadcastToPlayersAndConsole(prefix, message, CONSOLE_WARNING_COLOR, true, "warning");
    }

    void broadcastError(String prefix, String message) {
        broadcastToPlayersAndConsole(prefix, message, CONSOLE_ERROR_COLOR, true, "wrong_usage");
    }

    private void broadcastToPlayersAndConsole(String prefix, String message,
                                              String consoleColor, boolean emphasize, String severityKey) {
        String body = message.replace("{prefix}", "");
        String combined = prefix + body;
        Bukkit.getOnlinePlayers().forEach(player -> sendGameMessage(player, combined, severityKey));

        String coloredBody = consoleBodyMarkup(body, consoleColor, emphasize);
        String prefixedBody = InGameTextFormatter.prefixContentLines(consolePrefix(), coloredBody);
        sendConsoleComponent(deserialize(prefixedBody));
    }

    /**
     * Rebuilds a notification for the server console using the startup banner
     * palette. Player-facing gradients and custom message colors must never
     * leak into console output, where they make unrelated content look like a
     * random gradient and are often quantized poorly by terminal panels.
     */
    static String consoleBodyMarkup(String message, String fallbackColor, boolean emphasize) {
        String[] sourceLines = message.split("\\R", -1);
        StringBuilder result = new StringBuilder(message.length() + 64);
        int contentLine = 0;
        for (int index = 0; index < sourceLines.length; index++) {
            if (index > 0) {
                result.append('\n');
            }

            String sourceLine = sourceLines[index];
            String plainLine = PlainTextComponentSerializer.plainText().serialize(deserialize(sourceLine));
            if (isDividerLine(plainLine)) {
                result.append(colorizeConsoleLine(plainLine, BANNER_SEPARATOR_COLOR, true));
                continue;
            }

            String explicitColor = explicitBannerColor(sourceLine);
            boolean title = contentLine == 0 && (sourceLines.length > 1 || splitConsoleLabelValue(plainLine) != null);
            result.append(colorizeConsoleLine(plainLine,
                    explicitColor == null ? (title ? BANNER_TITLE_COLOR : fallbackColor) : explicitColor,
                    emphasize || containsBoldFormatting(sourceLine),
                    true));
            contentLine++;
        }
        return result.toString();
    }

    private static String colorizeConsoleLine(String line, String color, boolean bold) {
        return colorizeConsoleLine(line, color, bold, false);
    }

    private static String colorizeConsoleLine(String line, String color, boolean bold, boolean splitLabelValue) {
        LabelValue parts = splitConsoleLabelValue(line);
        if (!splitLabelValue || parts == null) {
            return color + (bold ? "<bold>" : "") + line + (bold ? "</bold>" : "");
        }
        String labelColor = BANNER_TITLE_COLOR.equals(color) ? BANNER_TITLE_COLOR : BANNER_LABEL_COLOR;
        return labelColor + (bold ? "<bold>" : "") + parts.label() + parts.separator()
                + (bold ? "</bold>" : "") + BANNER_VALUE_COLOR + (bold ? "<bold>" : "")
                + parts.value() + (bold ? "</bold>" : "");
    }

    private static String explicitBannerColor(String sourceLine) {
        String normalized = sourceLine
                .replaceAll("(?i)</?gradient(?::[^>]*)?>", "")
                .toLowerCase(Locale.ROOT);
        if (normalized.contains("#ff69b4")) {
            return BANNER_NOTICE_COLOR;
        }
        if (normalized.contains("#e62028")) {
            return BANNER_TITLE_COLOR;
        }
        if (normalized.contains("#d7c7ff")) {
            return BANNER_LABEL_COLOR;
        }
        if (normalized.contains("#b9e7ff")) {
            return BANNER_VALUE_COLOR;
        }
        return null;
    }

    private static boolean containsBoldFormatting(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        return normalized.contains("<bold>") || normalized.contains("&l") || normalized.contains("§l");
    }

    private static LabelValue splitConsoleLabelValue(String line) {
        int arrow = line.indexOf(" > ");
        if (arrow > 0 && arrow + 3 < line.length()) {
            return new LabelValue(line.substring(0, arrow), line.substring(arrow, arrow + 3),
                    line.substring(arrow + 3));
        }
        int fullWidthColon = line.indexOf('：');
        if (fullWidthColon > 0 && fullWidthColon + 1 < line.length()) {
            return new LabelValue(line.substring(0, fullWidthColon + 1), "",
                    line.substring(fullWidthColon + 1).trim());
        }
        int colon = line.indexOf(": ");
        if (colon > 0 && colon + 2 < line.length()) {
            return new LabelValue(line.substring(0, colon + 1), " ", line.substring(colon + 2));
        }
        return null;
    }

    private record LabelValue(String label, String separator, String value) {
    }

    static Component deserialize(String text) {
        try {
            return SECTION_SERIALIZER.deserialize(color(text));
        } catch (IllegalArgumentException invalidMiniMessage) {
            // Keep old installations readable when a custom message contains an invalid tag.
            return LEGACY_SERIALIZER.deserialize(text.replace('§', '&'));
        }
    }

    Component deserializeInGame(String text) {
        String leftAligned = InGameTextFormatter.leftAlign(text);
        return deserialize(InGameTextFormatter.forceBold(leftAligned));
    }

    Component deserializeInGame(String prefix, String text) {
        String leftAlignedPrefix = InGameTextFormatter.leftAlign(prefix);
        String leftAlignedText = InGameTextFormatter.leftAlign(text);
        return deserialize(InGameTextFormatter.forceBold(
                InGameTextFormatter.prefixContentLines(leftAlignedPrefix, leftAlignedText)));
    }

    private void sendHelp(CommandSender sender) {
        boolean isAdmin = hasAdminPermission(sender);
        List<String> helpMenu = languageManager.list(isAdmin ? "help_menu_admin" : "help_menu_player");

        String pluginName = getPluginMeta().getName();
        String pluginVersion = getPluginMeta().getVersion();
        String author = getPluginMeta().getAuthors().isEmpty() ? "Lazyz" : getPluginMeta().getAuthors().get(0);
        boolean chinese = languageManager.code().equalsIgnoreCase("zh_CN");
        String cleanupInterval = cleanupIntervalForHelp();
        String cleanupIntervalUnit = cleanupIntervalUnitForHelp(chinese);
        String cleanupRemaining = cleanupTask.cleanupRemainingDescription(chinese);
        int cleanupCountdown = Math.max(0, getConfig().getInt("cleanup.countdown_seconds", 10));
        long recreateInterval = Long.parseLong(cleanupTask.recreateIntervalAmount());
        String recreateUnit = cleanupTask.recreateIntervalUnitDescription(chinese);
        String recreateRemaining = cleanupTask.recreateRemainingDescription(chinese);
        int recreateCountdown = Math.max(0, getConfig().getInt("recreate.countdown_seconds", 10));
        boolean hasCleanupHelp = helpMenu.stream().anyMatch(line -> line.contains("/war cleanup"));
        boolean hasRecreateHelp = helpMenu.stream().anyMatch(line -> line.contains("/war recreate"));
        String helpKey = isAdmin ? "help_menu_admin" : "help_menu_player";
        for (String line : helpMenu) {
            if (line == null || line.isBlank()) {
                continue;
            }
            if (isAdmin && !hasCleanupHelp && line.contains("/war reload")) {
                String fallback = chinese
                        ? "  <gradient:#FFB7D5:#D7C7FF:#B9E7FF:#D7C7FF:#FFB7D5><bold>/war cleanup - 立即清理地形并重置自动倒计时</gradient>"
                        : "  <gradient:#FFB7D5:#D7C7FF:#B9E7FF:#D7C7FF:#FFB7D5><bold>/war cleanup - Run cleanup and reset the automatic timer</gradient>";
                sendHelpLine(sender, fallback, helpKey,
                        pluginName, pluginVersion, author, cleanupInterval, cleanupIntervalUnit, cleanupRemaining,
                        cleanupCountdown, recreateInterval, recreateUnit, recreateRemaining, recreateCountdown);
            }
            if (isAdmin && !hasRecreateHelp && line.contains("/war reload")) {
                String fallback = chinese
                        ? "  <gradient:#FFB7D5:#D7C7FF:#B9E7FF:#D7C7FF:#FFB7D5><bold>/war recreate - 执行地形热恢复并重置自动倒计时</gradient>"
                        : "  <gradient:#FFB7D5:#D7C7FF:#B9E7FF:#D7C7FF:#FFB7D5><bold>/war recreate - Run hot restoration and reset the automatic timer</gradient>";
                sendHelpLine(sender, fallback, helpKey,
                        pluginName, pluginVersion, author, cleanupInterval, cleanupIntervalUnit, cleanupRemaining,
                        cleanupCountdown, recreateInterval, recreateUnit, recreateRemaining, recreateCountdown);
            }
            sendHelpLine(sender, line, helpKey, pluginName, pluginVersion, author, cleanupInterval, cleanupIntervalUnit, cleanupRemaining,
                    cleanupCountdown, recreateInterval, recreateUnit, recreateRemaining, recreateCountdown);
        }
    }

    private void sendHelpLine(CommandSender sender, String line, String helpKey, String pluginName, String pluginVersion,
                              String author, String cleanupInterval, String cleanupIntervalUnit, String cleanupRemaining,
                              int cleanupCountdown, long recreateInterval, String recreateUnit, String recreateRemaining,
                              int recreateCountdown) {
        if (isDividerLine(line)) {
            line = HELP_DIVIDER;
        } else {
            line = line.replace("&m", "");
        }

        String formattedLine = replaceVariables(line,
                "{name}", pluginName,
                "{version}", pluginVersion,
                "{author}", author,
                "{interval}", cleanupInterval,
                "{countdown}", String.valueOf(cleanupCountdown),
                "{cleanup_interval}", cleanupInterval,
                "{cleanup_interval_unit}", cleanupIntervalUnit,
                "{cleanup_remaining}", cleanupRemaining,
                "{cleanup_countdown}", String.valueOf(cleanupCountdown),
                "{recreate_interval}", String.valueOf(recreateInterval),
                "{recreate_interval_unit}", recreateUnit,
                "{recreate_remaining}", recreateRemaining,
                "{recreate_countdown}", String.valueOf(recreateCountdown));
        if (sender instanceof ConsoleCommandSender) {
            String consoleBody = consoleBodyMarkup(formattedLine, CONSOLE_INFO_COLOR, false);
            String prefixedBody = InGameTextFormatter.prefixContentLines(consolePrefix(), consoleBody);
            sendConsoleComponent(sender, deserialize(prefixedBody));
        } else {
            sendGameMessage(sender, formattedLine, helpKey);
        }
    }

    private static boolean isDividerLine(String line) {
        String plain = line
                .replaceAll("<[^>]*>", "")
                .replaceAll("(?i)(?:&|§)#[0-9a-f]{6}", "")
                .replaceAll("(?i)(?:&|§)x(?:(?:&|§)[0-9a-f]){6}", "")
                .replaceAll("(?i)(?:&|§)[0-9a-fk-or]", "")
                .trim();
        return plain.equals("---------------- * ----------------")
                || plain.matches("-+\\s*[✦✧*]\\s*-+")
                || plain.matches("━+\\s*✦\\s*━+")
                || plain.matches("&m-+");
    }

    private String cleanupIntervalForHelp() {
        return cleanupTask.cleanupIntervalAmount();
    }

    private String cleanupIntervalUnitForHelp(boolean chinese) {
        return cleanupTask.cleanupIntervalUnitDescription(chinese);
    }

    private void sendPrefixed(CommandSender sender, String key, String fallback, String... replacements) {
        String prefix = message("prefix", DEFAULT_PREFIX);
        String body = message(key, fallback, replacements).replace("{prefix}", "");
        if (sender instanceof ConsoleCommandSender) {
            String consoleBody = consoleBodyMarkup(body, consoleColorForKey(key), consoleEmphasizeForKey(key));
            String prefixedBody = InGameTextFormatter.prefixContentLines(consolePrefix(), consoleBody);
            sendConsoleComponent(sender, deserialize(prefixedBody));
        } else {
            sendGameMessage(sender, colorLegacyStatusMessage(key, prefix + body), key);
        }
    }

    private String consoleColorForKey(String key) {
        return switch (key) {
            case "reload_success" -> BANNER_VALUE_COLOR;
            case "manual_cleanup_started", "manual_recreate_started" -> BANNER_SEPARATOR_COLOR;
            case "no_permission" -> BANNER_TITLE_COLOR;
            case "wrong_usage" -> BANNER_NOTICE_COLOR;
            default -> CONSOLE_INFO_COLOR;
        };
    }

    private boolean consoleEmphasizeForKey(String key) {
        return key.equals("no_permission") || key.equals("wrong_usage");
    }

    private boolean hasAdminPermission(CommandSender sender) {
        return sender.hasPermission(getConfig().getString("settings.admin-permission", "worldareareset.admin"));
    }

    boolean isChineseLanguage() {
        return languageManager != null && languageManager.code().equalsIgnoreCase("zh_CN");
    }

    private void printStartupBanner() {
        String version = getPluginMeta().getVersion();
        String author = getPluginMeta().getAuthors().isEmpty() ? "Lazyz" : getPluginMeta().getAuthors().get(0);
        String title = "WORLD AREA RESET SERVICE v" + version;
        String subtitle = "WORLD AREA RESET MANAGEMENT / 区域重置管理";
        String versionDetail = "Version / 版本 : " + version;
        String authorDetail = "Author  / 作者 : " + author;
        String languageDetail = "Language/ 语言 : " + languageManager.code();
        String githubDetail = "GitHub         : " + UpdateChecker.PROJECT_URL;
        String privacy = "★ Open source. No telemetry or server-data upload. ★";
        prepareStartupBannerWidth(title, subtitle,
                versionDetail, authorDetail, languageDetail, githubDetail, privacy);
        logBanner(bannerBorder('='));
        logBanner(centerBannerLine(title, BANNER_TITLE_COLOR));
        logBanner(centerBannerLine(subtitle, BANNER_VALUE_COLOR));
        logBanner(bannerBorder('-'));
        logBanner(bannerDetail("Version / 版本", version));
        logBanner(bannerDetail("Author  / 作者", author));
        logBanner(bannerDetail("Language/ 语言", languageManager.code()));
        logBanner(bannerDetail("GitHub        ", UpdateChecker.PROJECT_URL));
        logBanner(bannerMessage(privacy, BANNER_NOTICE_COLOR));
        logBanner(bannerBorder('='));

    }

    private void logConsole(String text) {
        sendConsoleComponent(deserialize(consolePrefix() + PREFIX_MESSAGE_COLOR + text));
    }

    private String consolePrefix() {
        return PREFIX_BRACKET_COLOR + "<bold>[</bold>"
                + PREFIX_NAME_COLOR + "<bold>WorldAreaReset</bold>"
                + PREFIX_BRACKET_COLOR + "<bold>]</bold> "
                + PREFIX_ARROW_COLOR + "<bold>»</bold> ";
    }

    private void sendConsoleComponent(Component component) {
        Bukkit.getConsoleSender().sendMessage(serializeConsole(component));
    }

    private void sendConsoleComponent(CommandSender sender, Component component) {
        sender.sendMessage(serializeConsole(component));
    }

    static String serializeConsole(Component component) {
        return CONSOLE_SERIALIZER.serialize(consoleCompatible(component));
    }

    private static Component consoleCompatible(Component component) {
        TextColor color = component.color();
        TextColor fallback = consoleFallbackColor(color);
        List<Component> children = component.children();
        List<Component> compatibleChildren = children.stream()
                .map(WorldAreaResetPlugin::consoleCompatible)
                .toList();
        Component result = component;
        if (fallback != null && fallback != color) {
            result = result.color(fallback);
        }
        if (!compatibleChildren.equals(children)) {
            result = result.children(compatibleChildren);
        }
        return result;
    }

    private static TextColor consoleFallbackColor(TextColor color) {
        if (color == null) {
            return null;
        }
        return switch (color.value()) {
            case 0xFFB7D5, 0xFF69B4 -> NamedTextColor.LIGHT_PURPLE;
            case 0xD7C7FF -> NamedTextColor.BLUE;
            case 0xB9E7FF -> NamedTextColor.AQUA;
            case 0x8A2387 -> NamedTextColor.DARK_PURPLE;
            case 0xE62028 -> NamedTextColor.RED;
            case 0x555555 -> NamedTextColor.DARK_GRAY;
            default -> color;
        };
    }

    private void logBanner(String text) {
        sendConsoleComponent(deserialize(text));
    }

    private String bannerBorder(char fill) {
        String color = fill == '-' ? BANNER_SEPARATOR_COLOR : BANNER_BORDER_COLOR;
        return color + "<bold>+" + String.valueOf(fill).repeat(startupBannerWidth)
                + "+</bold><reset>";
    }

    private String centerBannerLine(String text) {
        return centerBannerLine(text, "<color:#B9E7FF>");
    }

    private String centerBannerLine(String text, String textColor) {
        int textWidth = bannerDisplayWidth(text);
        int leftPadding = Math.max(0, (startupBannerWidth - textWidth) / 2);
        int rightPadding = Math.max(0, startupBannerWidth - textWidth - leftPadding);
        return BANNER_BORDER_COLOR + "<bold>|</bold>" + textColor + "<bold>" + " ".repeat(leftPadding) + text
                + " ".repeat(rightPadding) + "</bold>" + BANNER_BORDER_COLOR + "<bold>|</bold><reset>";
    }

    private String bannerMessage(String text, String textColor) {
        int rightPadding = Math.max(0, startupBannerWidth - 1 - bannerDisplayWidth(text));
        return BANNER_BORDER_COLOR + "<bold>|</bold>" + textColor + "<bold> " + text
                + " ".repeat(rightPadding)
                + "</bold>" + BANNER_BORDER_COLOR + "<bold>|</bold><reset>";
    }

    private String bannerDetail(String label, String value) {
        String plainText = label + " : " + value;
        int rightPadding = Math.max(0, startupBannerWidth - 1 - bannerDisplayWidth(plainText));
        return BANNER_BORDER_COLOR + "<bold>|</bold>" + BANNER_LABEL_COLOR + "<bold> " + label + " : </bold>"
                + BANNER_VALUE_COLOR + "<bold>" + value + " ".repeat(rightPadding)
                + "</bold>" + BANNER_BORDER_COLOR + "<bold>|</bold><reset>";
    }

    private int bannerDisplayWidth(String text) {
        String plainText = PlainTextComponentSerializer.plainText().serialize(deserialize(text));
        int width = 0;
        for (int offset = 0; offset < plainText.length();) {
            int codePoint = plainText.codePointAt(offset);
            java.lang.Character.UnicodeScript script = java.lang.Character.UnicodeScript.of(codePoint);
            width += script == java.lang.Character.UnicodeScript.HAN
                    || script == java.lang.Character.UnicodeScript.HIRAGANA
                    || script == java.lang.Character.UnicodeScript.KATAKANA
                    || script == java.lang.Character.UnicodeScript.HANGUL ? 2 : 1;
            offset += Character.charCount(codePoint);
        }
        return width;
    }

    private void prepareStartupBannerWidth(String... lines) {
        int requiredWidth = MIN_STARTUP_BANNER_WIDTH;
        for (String line : lines) {
            requiredWidth = Math.max(requiredWidth, bannerDisplayWidth(line) + 1);
        }
        startupBannerWidth = requiredWidth;
    }

    String replaceVariables(String text, String... replacements) {
        String result = text;
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            String token = replacements[i];
            String value = replacements[i + 1];
            String name = token;
            if (name.startsWith("${") && name.endsWith("}")) {
                name = name.substring(2, name.length() - 1);
            } else if (name.startsWith("{") && name.endsWith("}")) {
                name = name.substring(1, name.length() - 1);
            } else if (name.startsWith("%") && name.endsWith("%")) {
                name = name.substring(1, name.length() - 1);
            }
            values.put("${" + name + "}", value);
            values.put("{" + name + "}", value);
            values.put("%" + name + "%", value);
        }
        List<String[]> pairs = new ArrayList<>();
        values.forEach((token, value) -> pairs.add(new String[]{token, value}));
        pairs.sort((left, right) -> Integer.compare(right[0].length(), left[0].length()));
        for (String[] pair : pairs) {
            result = result.replace(pair[0], pair[1]);
        }
        return result;
    }

    private static String legacyToMiniMessage(String text) {
        StringBuilder result = new StringBuilder(text.length() + 16);
        for (int index = 0; index < text.length();) {
            if ((text.charAt(index) == '&' || text.charAt(index) == '§') && index + 1 < text.length()) {
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
                    char markerPrefix = text.charAt(index);
                    for (int offset = 0; offset < 6; offset++) {
                        int marker = index + 2 + offset * 2;
                        if (marker + 1 >= text.length() || text.charAt(marker) != markerPrefix
                                || Character.digit(text.charAt(marker + 1), 16) < 0) {
                            valid = false;
                            break;
                        }
                        hex.append(text.charAt(marker + 1));
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
            result.append(text.charAt(index++));
        }
        return result.toString();
    }

}
