package net.lazyz.worldareareset;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

public class WorldAreaResetPlugin extends JavaPlugin {

    private static final String HELP_DIVIDER = "&6━━━━━━&e━━━━━━&a━━━━━━ &f✦ &a━━━━━━&e━━━━━━&6━━━━━━";

    private AreaCleanupTask cleanupTask;
    private LanguageManager languageManager;
    private UpdateChecker updateChecker;

    @Override
    public void onEnable() {
        saveDefaultConfig();
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
                sendPrefixed(sender, "no_permission", "&cYou do not have permission to use this command.");
                return true;
            }

            reloadConfig();
            languageManager.reload();
            cleanupTask.stop();
            cleanupTask.start();

            sendPrefixed(sender, "reload_success", "&aConfiguration reloaded and cleanup schedule restarted.");
            return true;
        }

        if (args[0].equalsIgnoreCase("cleanup")) {
            if (!hasAdminPermission(sender)) {
                sendPrefixed(sender, "no_permission", "&cYou do not have permission to use this command.");
                return true;
            }

            cleanupTask.runManualCleanup();
            long interval = getConfig().getLong("cleanup.interval_minutes", 180);
            sendPrefixed(sender, "manual_cleanup_started",
                    "&aManual cleanup countdown started. The automatic timer was reset to {interval} minutes.",
                    "{interval}", String.valueOf(interval));
            return true;
        }

        sendPrefixed(sender, "wrong_usage", "&eUnknown command. Use &6/war help &efor help.");
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
            options.add("reload");
        }

        List<String> completions = new ArrayList<>();
        StringUtil.copyPartialMatches(args[0], options, completions);
        Collections.sort(completions);
        return completions;
    }

    String message(String key, String fallback, String... replacements) {
        return replaceVariables(languageManager.text(key, fallback), replacements);
    }

    void logLocalized(String key, String fallback, String... replacements) {
        getLogger().info(stripLegacyFormatting(message(key, fallback, replacements)));
    }

    void logLocalized(Level level, String key, String fallback, String... replacements) {
        getLogger().log(level, stripLegacyFormatting(message(key, fallback, replacements)));
    }

    Component deserialize(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }

    private void sendHelp(CommandSender sender) {
        boolean isAdmin = hasAdminPermission(sender);
        List<String> helpMenu = languageManager.list(isAdmin ? "help_menu_admin" : "help_menu_player");

        String pluginName = getPluginMeta().getName();
        String pluginVersion = getPluginMeta().getVersion();
        String author = getPluginMeta().getAuthors().isEmpty() ? "Lazyz" : getPluginMeta().getAuthors().get(0);
        long interval = getConfig().getLong("cleanup.interval_minutes", 180);
        int countdown = getConfig().getInt("cleanup.countdown_seconds", 10);
        boolean hasCleanupHelp = helpMenu.stream().anyMatch(line -> line.contains("/war cleanup"));

        for (String line : helpMenu) {
            if (isAdmin && !hasCleanupHelp && line.contains("/war reload")) {
                sendHelpLine(sender, "  &f/war cleanup &8- &7Run cleanup and reset the automatic timer",
                        pluginName, pluginVersion, author, interval, countdown);
            }
            sendHelpLine(sender, line, pluginName, pluginVersion, author, interval, countdown);
        }
    }

    private void sendHelpLine(CommandSender sender, String line, String pluginName, String pluginVersion,
                              String author, long interval, int countdown) {
        if (line.contains("&m---------")) {
            line = HELP_DIVIDER;
        } else {
            line = line.replace("&m", "");
        }

        String formattedLine = replaceVariables(line,
                "{name}", pluginName,
                "{version}", pluginVersion,
                "{author}", author,
                "{interval}", String.valueOf(interval),
                "{countdown}", String.valueOf(countdown));
        sender.sendMessage(deserialize(formattedLine));
    }

    private void sendPrefixed(CommandSender sender, String key, String fallback, String... replacements) {
        String prefix = message("prefix", "&8[&6WorldAreaReset&8] &r");
        sender.sendMessage(deserialize(prefix + message(key, fallback, replacements)));
    }

    private boolean hasAdminPermission(CommandSender sender) {
        return sender.hasPermission("worldareareset.admin");
    }

    private void printStartupBanner() {
        CommandSender console = Bukkit.getConsoleSender();
        console.sendMessage(deserialize(HELP_DIVIDER));
        console.sendMessage(deserialize("&6&l  WorldAreaReset &fv" + getPluginMeta().getVersion() + " &8| &eLazyz"));
        console.sendMessage(deserialize("&7  Paper/Folia 1.21.x &8| &a" + languageManager.code()));
        console.sendMessage(deserialize("&7  " + UpdateChecker.PROJECT_URL));
        console.sendMessage(deserialize(HELP_DIVIDER));
    }

    private String replaceVariables(String text, String... replacements) {
        String result = text;
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            result = result.replace(replacements[i], replacements[i + 1]);
        }
        return result;
    }

    private String stripLegacyFormatting(String text) {
        return text.replaceAll("&[0-9A-FK-ORa-fk-or]", "");
    }
}
