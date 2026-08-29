package net.lazyz.worldareareset;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
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
import java.util.logging.Level;
import java.util.regex.Pattern;

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
    private static final Pattern CONSOLE_COLOR_TAG = Pattern.compile(
            "(?i)</?gradient(?::[^>]+)?>|</?color(?::[^>]+)?>|<#[0-9a-f]{6}>"
                    + "|(?:&|§)(?:#[0-9a-f]{6}|x(?:&?[0-9a-f]){6}|[0-9a-fk-or])");
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer CONSOLE_SERIALIZER = LegacyComponentSerializer.legacySection();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.builder()
            .character('&').hexColors().build();

    private AreaCleanupTask cleanupTask;
    private LanguageManager languageManager;
    private UpdateChecker updateChecker;
    private int startupBannerWidth = MIN_STARTUP_BANNER_WIDTH;

    @Override
    public void onEnable() {
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
                            "config_version", getPluginMeta().getVersion(), true, refreshComments);
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
        return configuration.getComments("config_version");
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

    void logLocalized(String key, String fallback, String... replacements) {
        String body = message(key, fallback, replacements)
                .replace("{version}", getPluginMeta().getVersion())
                .replace("{author}", getPluginMeta().getAuthors().isEmpty()
                        ? "Unknown"
                        : getPluginMeta().getAuthors().get(0))
                .replace("{prefix}", "");
        // Console output has its own severity channel. Strip message-local
        // colors first so legacy custom configurations cannot reintroduce a
        // gradient or override the documented severity color.
        body = stripConsoleColorTags(body);
        String statusColor = switch (key) {
            case "updater.checking" -> CONSOLE_INFO_COLOR;
            case "updater.latest", "updater.downloaded" -> CONSOLE_SUCCESS_COLOR;
            case "updater.available", "updater.manual_download" -> CONSOLE_WARNING_COLOR;
            case "updater.failed" -> CONSOLE_ERROR_COLOR;
            default -> PREFIX_MESSAGE_COLOR;
        };
        boolean emphasize = key.equals("updater.available")
                || key.equals("updater.manual_download")
                || key.equals("updater.failed");
        logConsole(statusColor + (emphasize ? "<bold>" + body + "</bold>" : body));
    }

    void broadcastInfo(String prefix, String message) {
        broadcastToPlayersAndConsole(prefix, message, CONSOLE_INFO_COLOR, false);
    }

    void broadcastSuccess(String prefix, String message) {
        broadcastToPlayersAndConsole(prefix, message, CONSOLE_SUCCESS_COLOR, false);
    }

    void broadcastWarning(String prefix, String message) {
        broadcastToPlayersAndConsole(prefix, message, CONSOLE_WARNING_COLOR, true);
    }

    void broadcastError(String prefix, String message) {
        broadcastToPlayersAndConsole(prefix, message, CONSOLE_ERROR_COLOR, true);
    }

    private void broadcastToPlayersAndConsole(String prefix, String message,
                                              String consoleColor, boolean emphasize) {
        Component playerMessage = deserializeInGame(prefix, message);
        Bukkit.getOnlinePlayers().forEach(player -> player.sendMessage(playerMessage));

        String consoleBody = PlainTextComponentSerializer.plainText().serialize(deserialize(message));
        consoleBody = stripConsoleColorTags(consoleBody);
        StringBuilder coloredBody = new StringBuilder(consoleBody.length() + 32);
        String[] lines = consoleBody.split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            if (index > 0) {
                coloredBody.append('\n');
            }
            coloredBody.append(consoleColor);
            if (emphasize) {
                coloredBody.append("<bold>");
            }
            coloredBody.append(lines[index]);
            if (emphasize) {
                coloredBody.append("</bold>");
            }
        }
        String prefixedBody = InGameTextFormatter.prefixContentLines(consolePrefix(), coloredBody.toString());
        sendConsoleComponent(deserialize(prefixedBody));
    }

    Component deserialize(String text) {
        String source = legacyToMiniMessage(text)
                .replace("{gradient}", "<gradient:" + String.join(":", DEFAULT_GRADIENT) + ">")
                .replace("<gradient>", "<gradient:" + String.join(":", DEFAULT_GRADIENT) + ">");
        try {
            return MINI_MESSAGE.deserialize(source);
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
        for (String line : helpMenu) {
            if (line == null || line.isBlank()) {
                continue;
            }
            if (isAdmin && !hasCleanupHelp && line.contains("/war reload")) {
                String fallback = chinese
                        ? "  <gradient:#FFB7D5:#D7C7FF:#B9E7FF:#D7C7FF:#FFB7D5><bold>/war cleanup - 立即清理地形并重置自动倒计时</gradient>"
                        : "  <gradient:#FFB7D5:#D7C7FF:#B9E7FF:#D7C7FF:#FFB7D5><bold>/war cleanup - Run cleanup and reset the automatic timer</gradient>";
                sendHelpLine(sender, fallback,
                        pluginName, pluginVersion, author, cleanupInterval, cleanupIntervalUnit, cleanupRemaining,
                        cleanupCountdown, recreateInterval, recreateUnit, recreateRemaining, recreateCountdown);
            }
            if (isAdmin && !hasRecreateHelp && line.contains("/war reload")) {
                String fallback = chinese
                        ? "  <gradient:#FFB7D5:#D7C7FF:#B9E7FF:#D7C7FF:#FFB7D5><bold>/war recreate - 执行地形热恢复并重置自动倒计时</gradient>"
                        : "  <gradient:#FFB7D5:#D7C7FF:#B9E7FF:#D7C7FF:#FFB7D5><bold>/war recreate - Run hot restoration and reset the automatic timer</gradient>";
                sendHelpLine(sender, fallback,
                        pluginName, pluginVersion, author, cleanupInterval, cleanupIntervalUnit, cleanupRemaining,
                        cleanupCountdown, recreateInterval, recreateUnit, recreateRemaining, recreateCountdown);
            }
            sendHelpLine(sender, line, pluginName, pluginVersion, author, cleanupInterval, cleanupIntervalUnit, cleanupRemaining,
                    cleanupCountdown, recreateInterval, recreateUnit, recreateRemaining, recreateCountdown);
        }
    }

    private void sendHelpLine(CommandSender sender, String line, String pluginName, String pluginVersion,
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
        sender.sendMessage(deserializeInGame(formattedLine));
    }

    private boolean isDividerLine(String line) {
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
        sender.sendMessage(deserializeInGame(prefix, message(key, fallback, replacements)));
    }

    private boolean hasAdminPermission(CommandSender sender) {
        return sender.hasPermission("worldareareset.admin");
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

        String started = languageManager.code().equalsIgnoreCase("zh_CN")
                ? "WorldAreaReset v" + version + " 作者 " + author + " 已在 Paper/Folia 核心上成功启动。"
                : "WorldAreaReset v" + version + " by " + author + " started successfully on Paper/Folia.";
        started = "<#B9E7FF><bold>" + started + "</bold>";
        logConsole(started);
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
        Bukkit.getConsoleSender().sendMessage(CONSOLE_SERIALIZER.serialize(component));
    }

    private String stripConsoleColorTags(String text) {
        return CONSOLE_COLOR_TAG.matcher(text).replaceAll("");
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

    private String legacyToMiniMessage(String text) {
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
