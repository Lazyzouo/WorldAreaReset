package net.lazyz.worldareareset;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationUpdaterTest {

    private static final String DEFAULT_CONFIG = """
            # Official header
            # Configuration format marker.
            config_version: 5
            # Language comment.
            language: "zh_CN"
            cleanup:
              # Existing option comment.
              enabled: false
              interval_minutes: 180
              # New option comment.
              countdown_seconds: 10
              keep_blocks:
                - BEDROCK
                - BARRIER
            updates:
              enabled: true
              notify_latest: true
            messages:
              en_US:
                prefix: "&8[WorldAreaReset] &r"
                new_notice: "Official notice"
                """;

    @TempDir
    Path temporaryDirectory;

    @Test
    void preservesUserValuesAndUnknownKeysWhileAddingMissingDefaults() throws Exception {
        Path configFile = temporaryDirectory.resolve("config.yml");
        String existing = """
                # Administrator header
                config_version: 3
                language: "en_US"
                cleanup:
                  enabled: true
                  interval_minutes: 0
                  keep_blocks: []
                updates:
                  enabled: false
                messages:
                  en_US:
                    prefix: "&c[Custom] "
                    new_notice: ""
                custom:
                  local_note: "server-only"
                """;
        byte[] originalBytes = existing.getBytes(StandardCharsets.UTF_8);
        Files.write(configFile, originalBytes);

        ConfigurationUpdater.UpdateResult result = ConfigurationUpdater.mergeMissingValues(
                configFile, new StringReader(DEFAULT_CONFIG), "config_version", "1.3.1", false);

        assertTrue(result.changed());
        assertFalse(result.blocked());
        assertEquals(2, result.addedKeys());
        assertTrue(result.versionUpdated());
        assertNull(result.backupFile());
        assertFalse(Files.exists(configFile.resolveSibling("config.yml.before-v1.3.1.bak")));

        YamlConfiguration updated = loadWithComments(configFile);
        assertEquals(5, updated.getInt("config_version"));
        assertEquals("en_US", updated.getString("language"));
        assertTrue(updated.getBoolean("cleanup.enabled"));
        assertEquals(0, updated.getInt("cleanup.interval_minutes"));
        assertTrue(updated.getList("cleanup.keep_blocks").isEmpty());
        assertFalse(updated.getBoolean("updates.enabled"));
        assertEquals("&c[Custom] ", updated.getString("messages.en_US.prefix"));
        assertEquals("", updated.getString("messages.en_US.new_notice"));
        assertEquals("server-only", updated.getString("custom.local_note"));
        assertEquals(10, updated.getInt("cleanup.countdown_seconds"));
        assertTrue(updated.getBoolean("updates.notify_latest"));
        String savedText = Files.readString(configFile, StandardCharsets.UTF_8);
        assertTrue(savedText.contains("# Administrator header"));
        assertFalse(savedText.contains("# Official header"));
        assertTrue(updated.getComments("cleanup.countdown_seconds").contains("New option comment."));

        ConfigurationUpdater.UpdateResult secondRun = ConfigurationUpdater.mergeMissingValues(
                configFile, new StringReader(DEFAULT_CONFIG), "config_version", "1.3.1");
        assertFalse(secondRun.changed());
        assertFalse(secondRun.blocked());
        assertNull(secondRun.backupFile());
    }

    @Test
    void blocksStructuralAndValueTypeConflictsWithoutChangingTheFile() throws Exception {
        Path configFile = temporaryDirectory.resolve("config.yml");
        String existing = """
                config_version: "3"
                language: "en_US"
                cleanup: disabled
                updates:
                  enabled: "yes"
                """;
        byte[] originalBytes = existing.getBytes(StandardCharsets.UTF_8);
        Files.write(configFile, originalBytes);

        ConfigurationUpdater.UpdateResult result = ConfigurationUpdater.mergeMissingValues(
                configFile, new StringReader(DEFAULT_CONFIG), "config_version", "1.3.1");

        assertTrue(result.blocked());
        assertFalse(result.changed());
        assertFalse(result.conflicts().contains("config_version"));
        assertTrue(result.conflicts().contains("cleanup"));
        assertTrue(result.conflicts().contains("updates.enabled"));
        assertArrayEquals(originalBytes, Files.readAllBytes(configFile));
        assertFalse(Files.exists(configFile.resolveSibling("config.yml.before-v1.3.1.bak")));
    }

    @Test
    void acceptsAParseableNumericStringVersion() throws Exception {
        Path configFile = temporaryDirectory.resolve("config.yml");
        Files.writeString(configFile, "config_version: \"3\"\nlanguage: en_US\ncleanup:\n  enabled: false\nupdates:\n  enabled: true\n", StandardCharsets.UTF_8);

        ConfigurationUpdater.UpdateResult result = ConfigurationUpdater.mergeMissingValues(
                configFile, new StringReader(DEFAULT_CONFIG), "config_version", "1.13.0", false);

        assertTrue(result.changed());
        assertTrue(result.versionUpdated());
        assertEquals(5, loadWithComments(configFile).getInt("config_version"));
    }

    @Test
    void rejectsFloatingPointVersionWithoutChangingTheFile() throws Exception {
        Path configFile = temporaryDirectory.resolve("config.yml");
        byte[] original = "config_version: 3.0\nlanguage: en_US\n".getBytes(StandardCharsets.UTF_8);
        Files.write(configFile, original);

        assertThrows(InvalidConfigurationException.class, () -> ConfigurationUpdater.mergeMissingValues(
                configFile, new StringReader(DEFAULT_CONFIG), "config_version", "1.13.0"));
        assertArrayEquals(original, Files.readAllBytes(configFile));
    }

    @Test
    void removesLegacyGradientListAndFormattingNamespace() throws Exception {
        Path configFile = temporaryDirectory.resolve("config.yml");
        Files.writeString(configFile, "config_version: 22\ngradient-colors: ['#112233', '#445566']\nformatting:\n  gradient-colors: ['#112233']\ninline-replacements: {}\n", StandardCharsets.UTF_8);
        String defaults = "config_version: 23\nmessages:\n  prefix: 'prefix'\n";

        ConfigurationUpdater.UpdateResult result = ConfigurationUpdater.mergeMissingValues(
                configFile, new StringReader(defaults), "config_version", "1.14.0", false);

        assertTrue(result.changed());
        YamlConfiguration updated = loadWithComments(configFile);
        assertFalse(updated.contains("formatting", true));
        assertFalse(updated.contains("inline-replacements", true));
        assertFalse(updated.contains("gradient-colors", true));
        assertEquals(23, updated.getInt("config_version"));
    }

    @Test
    void flattensSelectedLanguageAndRemovesLegacyMessageNamespaces() throws Exception {
        Path configFile = temporaryDirectory.resolve("config.yml");
        Files.writeString(configFile, """
                config_version: 22
                language: zh_CN
                language-applied: en_US
                messages:
                  en_US:
                    reload_success: "English reload"
                  zh_CN:
                    reload_success: "中文重载"
                  custom: "保留自定义消息"
                formatting:
                  gradient-colors: ['#112233']
                inline-replacements: {}
                """, StandardCharsets.UTF_8);
        String defaults = """
                config_version: 23
                language: zh_CN
                messages:
                  reload_success: "官方中文重载"
                  custom_default: "新增消息"
                """;

        ConfigurationUpdater.UpdateResult result = ConfigurationUpdater.mergeMissingValues(
                configFile, new StringReader(defaults), "config_version", "1.14.0", false);

        assertTrue(result.changed());
        YamlConfiguration updated = loadWithComments(configFile);
        assertEquals("官方中文重载", updated.getString("messages.reload_success"));
        assertEquals("保留自定义消息", updated.getString("messages.custom"));
        assertFalse(updated.contains("messages.en_US", true));
        assertFalse(updated.contains("messages.zh_CN", true));
        assertFalse(updated.contains("formatting", true));
        assertFalse(updated.contains("inline-replacements", true));
    }

    @Test
    void migratesOnlyLegacyDividerRowsAndPreservesOtherMessageText() throws Exception {
        Path configFile = temporaryDirectory.resolve("config.yml");
        Files.writeString(configFile, """
                config_version: 20
                messages:
                  zh_CN:
                    warning: |-
                      &6━━━━━━&e━━━━━━ &f✦ &a━━━━━━&6━━━━━━
                      &c自定义警告正文
                    custom: "━━━━━━ ✦ ━━━━━━"
                custom:
                  note: "━━━━━━ ✦ ━━━━━━ should remain outside messages"
                """, StandardCharsets.UTF_8);
        String defaults = """
                config_version: 20
                messages:
                  zh_CN:
                    warning: "默认警告"
                """;

        ConfigurationUpdater.UpdateResult result = ConfigurationUpdater.mergeMissingValues(
                configFile, new StringReader(defaults), "config_version", "1.13.1", false);

        assertTrue(result.changed());
        YamlConfiguration updated = loadWithComments(configFile);
        String warning = updated.getString("messages.zh_CN.warning");
        assertTrue(warning.contains("<gradient:#FFB7D5:#D7C7FF:#B9E7FF:#D7C7FF:#FFB7D5><bold><strikethrough>---------"));
        assertTrue(warning.contains("自定义警告正文"));
        assertEquals("<gradient:#FFB7D5:#D7C7FF:#B9E7FF:#D7C7FF:#FFB7D5><bold><strikethrough>---------<bold><strikethrough>---------<bold> ✧ <bold><strikethrough>---------<bold><strikethrough>---------</gradient>",
                updated.getString("messages.zh_CN.custom"));
        assertEquals("━━━━━━ ✦ ━━━━━━ should remain outside messages",
                updated.getString("custom.note"));
    }

    @Test
    void migratesLegacyDividersInRootLanguageResources() throws Exception {
        Path languageFile = temporaryDirectory.resolve("en_US.yml");
        Files.writeString(languageFile, "warning: |\n  &m---------\n  &cCustom warning\n", StandardCharsets.UTF_8);

        ConfigurationUpdater.UpdateResult result = ConfigurationUpdater.mergeMissingValues(
                languageFile, new StringReader("warning: \"Default warning\"\n"), null, "1.13.1", false);

        assertTrue(result.changed());
        String warning = loadWithComments(languageFile).getString("warning");
        assertTrue(warning.contains("<gradient:#FFB7D5:#D7C7FF:#B9E7FF:#D7C7FF:#FFB7D5><bold><strikethrough>---------"));
        assertTrue(warning.contains("Custom warning"));
    }

    @Test
    void updatesPreviousReleaseMarkerDuringManualMigrationWithoutBackup() throws Exception {
        Path configFile = temporaryDirectory.resolve("config.yml");
        Files.writeString(configFile, """
                config_version: 12
                language: "zh_CN"
                cleanup:
                  enabled: false
                """, StandardCharsets.UTF_8);

        String defaults = """
                config_version: 13
                language: "en_US"
                cleanup:
                  enabled: false
                  countdown_seconds: 10
                """;

        ConfigurationUpdater.UpdateResult result = ConfigurationUpdater.mergeMissingValues(
                configFile, new StringReader(defaults), "config_version", "1.7.3", false);

        assertTrue(result.changed());
        assertTrue(result.versionUpdated());
        assertEquals(1, result.addedKeys());
        assertNull(result.backupFile());
        assertEquals(13, loadWithComments(configFile).getInt("config_version"));
        assertEquals(10, loadWithComments(configFile).getInt("cleanup.countdown_seconds"));
        assertFalse(Files.exists(configFile.resolveSibling("config.yml.before-v1.7.3.bak")));
    }

    @Test
    void migratesLegacyWorldAndRegionSettingsIntoWorldModules() throws Exception {
        Path configFile = temporaryDirectory.resolve("config.yml");
        Files.writeString(configFile, """
                config_version: 14
                cleanup:
                  world: world
                  worlds: [world, world_nether]
                  world_bounds_enabled: true
                  world_bounds:
                    world_nether: {min_x: -20, max_x: 20, min_y: 0, max_y: 100, min_z: -30, max_z: 30}
                  world_regions:
                    world:
                      - {min_x: -5, max_x: 5, min_y: 1, max_y: 50, min_z: -6, max_z: 6}
                  min_x: -200
                  max_x: 200
                  min_y: 0
                  max_y: 128
                  min_z: -200
                  max_z: 200
                recreate:
                  worlds: [arena_world, arena_nether]
                  region_enabled: true
                  regions:
                    - {world: arena_world, min_x: -4, max_x: 4, min_y: 0, max_y: 40, min_z: -4, max_z: 4}
                    - {world: arena_nether, min_x: -8, max_x: 8, min_y: 5, max_y: 60, min_z: -8, max_z: 8}
                """, StandardCharsets.UTF_8);

        String defaults = """
                config_version: 15
                cleanup:
                  worlds:
                    - name: world_nether
                      regions:
                        - {min_x: -200, max_x: 200, min_y: 0, max_y: 128, min_z: -200, max_z: 200}
                recreate:
                  worlds:
                    - name: arena_world
                      regions: []
                """;

        ConfigurationUpdater.UpdateResult result = ConfigurationUpdater.mergeMissingValues(
                configFile, new StringReader(defaults), "config_version", "1.8.0", false);

        assertTrue(result.changed());
        assertFalse(result.blocked());
        YamlConfiguration updated = loadWithComments(configFile);
        assertEquals(15, updated.getInt("config_version"));
        List<Map<?, ?>> cleanupWorlds = updated.getMapList("cleanup.worlds");
        assertEquals(List.of("world", "world_nether"), cleanupWorlds.stream()
                .map(world -> String.valueOf(world.get("name"))).toList());
        assertEquals(1, ((List<?>) cleanupWorlds.get(0).get("regions")).size());
        Map<?, ?> netherBounds = (Map<?, ?>) ((List<?>) cleanupWorlds.get(1).get("regions")).get(0);
        assertEquals(-20, netherBounds.get("min_x"));
        assertEquals(30, netherBounds.get("max_z"));

        List<Map<?, ?>> recreateWorlds = updated.getMapList("recreate.worlds");
        assertEquals(List.of("arena_world", "arena_nether"), recreateWorlds.stream()
                .map(world -> String.valueOf(world.get("name"))).toList());
        assertEquals(1, ((List<?>) recreateWorlds.get(0).get("regions")).size());
        assertFalse(updated.contains("cleanup.world", true));
        assertFalse(updated.contains("cleanup.world_bounds_enabled", true));
        assertFalse(updated.contains("cleanup.world_regions", true));
        assertFalse(updated.contains("recreate.region_enabled", true));
        assertFalse(updated.contains("recreate.regions", true));
    }

    @Test
    void addsRestorePacingWhenUpgradingFormat15() throws Exception {
        Path configFile = temporaryDirectory.resolve("config.yml");
        Files.writeString(configFile, """
                config_version: 15
                recreate:
                  enabled: false
                  player_protection_radius: 2
                """, StandardCharsets.UTF_8);
        String defaults = """
                config_version: 16
                recreate:
                  enabled: false
                  player_protection_radius: 2
                  blocks_per_tick: 4096
                """;

        ConfigurationUpdater.UpdateResult result = ConfigurationUpdater.mergeMissingValues(
                configFile, new StringReader(defaults), "config_version", "1.9.0", false);

        assertTrue(result.changed());
        assertTrue(result.versionUpdated());
        assertEquals(1, result.addedKeys());
        YamlConfiguration updated = loadWithComments(configFile);
        assertEquals(16, updated.getInt("config_version"));
        assertEquals(4096, updated.getInt("recreate.blocks_per_tick"));
    }

    @Test
    void migratesCleanupIntervalMinutesToAmountAndUnit() throws Exception {
        Path configFile = temporaryDirectory.resolve("config.yml");
        Files.writeString(configFile, """
                config_version: 17
                cleanup:
                  interval_minutes: 360
                """, StandardCharsets.UTF_8);
        String defaults = """
                config_version: 18
                cleanup:
                  interval: 3
                  interval_unit: "hours"
                """;

        ConfigurationUpdater.UpdateResult result = ConfigurationUpdater.mergeMissingValues(
                configFile, new StringReader(defaults), "config_version", "1.11.0", false);

        assertTrue(result.changed());
        assertTrue(result.versionUpdated());
        YamlConfiguration updated = loadWithComments(configFile);
        assertEquals(18, updated.getInt("config_version"));
        assertEquals(6, updated.getInt("cleanup.interval"));
        assertEquals("hours", updated.getString("cleanup.interval_unit"));
        assertFalse(updated.contains("cleanup.interval_minutes", true));
    }

    @Test
    void leavesMalformedYamlUntouched() throws Exception {
        Path configFile = temporaryDirectory.resolve("config.yml");
        byte[] originalBytes = "cleanup: [unterminated".getBytes(StandardCharsets.UTF_8);
        Files.write(configFile, originalBytes);

        assertThrows(InvalidConfigurationException.class, () -> ConfigurationUpdater.mergeMissingValues(
                configFile, new StringReader(DEFAULT_CONFIG), "config_version", "1.3.1"));

        assertArrayEquals(originalBytes, Files.readAllBytes(configFile));
        assertFalse(Files.exists(configFile.resolveSibling("config.yml.before-v1.3.1.bak")));
    }

    @Test
    void mergesLanguageDefaultsWithoutAFormatVersion() throws Exception {
        Path languageFile = temporaryDirectory.resolve("en_US.yml");
        Files.writeString(languageFile, "prefix: \"&c[Custom] \"\n", StandardCharsets.UTF_8);
        String defaults = """
                prefix: "&8[WorldAreaReset] &r"
                new_notice: "New message"
                """;

        ConfigurationUpdater.UpdateResult result = ConfigurationUpdater.mergeMissingValues(
                languageFile, new StringReader(defaults), null, "1.3.1");

        assertTrue(result.changed());
        assertEquals(1, result.addedKeys());
        assertFalse(result.versionUpdated());
        YamlConfiguration updated = loadWithComments(languageFile);
        assertEquals("&c[Custom] ", updated.getString("prefix"));
        assertEquals("New message", updated.getString("new_notice"));
        assertTrue(Files.exists(languageFile.resolveSibling("en_US.yml.before-v1.3.1.bak")));
    }

    @Test
    void storesVersionedConfigBackupInDedicatedDirectory() throws Exception {
        Path configFile = temporaryDirectory.resolve("config.yml");
        Files.writeString(configFile, "config_version: 18\ncleanup:\n  enabled: false\n",
                StandardCharsets.UTF_8);
        String defaults = "config_version: 19\ncleanup:\n  enabled: false\n  countdown_seconds: 10\n";

        ConfigurationUpdater.UpdateResult result = ConfigurationUpdater.mergeMissingValues(
                configFile, new StringReader(defaults), "config_version", "1.12.0", true);

        assertTrue(result.changed());
        assertTrue(result.versionUpdated());
        assertTrue(result.backupFile() != null);
        assertEquals(temporaryDirectory.resolve("config-backups"), result.backupFile().getParent());
        assertTrue(result.backupFile().getFileName().toString().startsWith("config-v18-to-v19-"));
        assertTrue(Files.exists(result.backupFile()));
    }

    @Test
    void selectsConfigurationTemplateForTheConfiguredLanguage() {
        assertEquals("config-zh_CN.yml", WorldAreaResetPlugin.configurationResource("zh_CN"));
        assertEquals("config-zh_CN.yml", WorldAreaResetPlugin.configurationResource("zh-cn"));
        assertEquals("config.yml", WorldAreaResetPlugin.configurationResource("en_US"));
        assertEquals("config.yml", WorldAreaResetPlugin.configurationResource("unknown"));
    }

    @Test
    void refreshesOfficialCommentsWhenSwitchingConfigurationLanguage() throws Exception {
        Path configFile = temporaryDirectory.resolve("config.yml");
        Files.writeString(configFile, """
                # WorldAreaReset by Lazyz
                # Author: Lazyz | Version: 1.13.3
                # After editing this file, reload the plugin.
                config_version: 22
                language: zh_CN
                language-applied: en_US
                cleanup:
                  # English cleanup comment.
                  enabled: true
                  interval: 9
                custom:
                  note: keep-me
                """, StandardCharsets.UTF_8);
        String chineseDefaults = """
                # WorldAreaReset by Lazyz
                # 插件作者: Lazyz | 插件版本: 1.13.4
                # 修改本文件后，请在游戏内重载插件。
                config_version: 22
                language: zh_CN
                language-applied: zh_CN
                cleanup:
                  # 中文清理注释。
                  enabled: false
                  interval: 3
                """;

        ConfigurationUpdater.UpdateResult result = ConfigurationUpdater.mergeMissingValues(
                configFile, new StringReader(chineseDefaults), "config_version", "1.13.4", false, true);
        assertTrue(result.changed());
        assertFalse(result.blocked());
        YamlConfiguration updated = loadWithComments(configFile);
        assertTrue(updated.getBoolean("cleanup.enabled"));
        assertEquals(9, updated.getInt("cleanup.interval"));
        assertEquals("keep-me", updated.getString("custom.note"));
        assertTrue(updated.getComments("config_version").stream().anyMatch(comment -> comment.contains("插件作者")));
        assertTrue(updated.getComments("cleanup.enabled").contains("中文清理注释。"));
    }

    @Test
    void replacesAuthorAndVersionPlaceholdersInExistingConfigurationHeader() throws Exception {
        Path configFile = temporaryDirectory.resolve("config.yml");
        Files.writeString(configFile, """
                # WorldAreaReset by Lazyz
                # Author: ${author} | Version: ${version}
                # After editing this file, run /worldareareset reload in game.
                config_version: 22
                language: en_US
                language-applied: en_US
                """, StandardCharsets.UTF_8);
        String defaults = """
                # WorldAreaReset by Lazyz
                # Author: Lazyz | Version: 1.13.5
                # After editing this file, run /war reload in game.
                config_version: 22
                language: en_US
                language-applied: en_US
                """;

        ConfigurationUpdater.UpdateResult result = ConfigurationUpdater.mergeMissingValues(
                configFile, new StringReader(defaults), "config_version", "1.13.5", false, true);

        assertTrue(result.changed());
        YamlConfiguration updated = loadWithComments(configFile);
        List<String> header = updated.getComments("config_version");
        assertTrue(header.contains("# Author: Lazyz | Version: 1.13.5")
                || header.contains("Author: Lazyz | Version: 1.13.5"));
        assertTrue(header.stream().noneMatch(comment -> comment.contains("${author}")
                || comment.contains("${version}")));
    }

    @Test
    void bundledConfigurationsAreValidAndUseTheCurrentFormat() throws Exception {
        List<String> bundledFiles = List.of(
                "src/main/resources/config.yml",
                "src/main/resources/config-zh_CN.yml",
                "src/main/resources/config-en_US.yml",
                "defaults/config.en_US.yml",
                "defaults/config.zh_CN.yml");
        for (String bundledFile : bundledFiles) {
            YamlConfiguration configuration = loadWithComments(Path.of(bundledFile));
            assertEquals(1, configuration.getInt("config-version"), bundledFile);
            assertFalse(configuration.contains("config_version", true), bundledFile);
            assertFalse(configuration.contains("formatting", true), bundledFile);
            assertFalse(configuration.contains("inline-replacements", true), bundledFile);
            assertFalse(configuration.contains("messages.en_US", true), bundledFile);
            assertFalse(configuration.contains("messages.zh_CN", true), bundledFile);
            assertTrue(configuration.isString("messages.reload_success"), bundledFile);
            if (bundledFile.endsWith("config-zh_CN.yml") || bundledFile.endsWith("config.zh_CN.yml")) {
                assertEquals("zh_CN", configuration.getString("language"), bundledFile);
            } else {
                assertEquals("en_US", configuration.getString("language"), bundledFile);
            }
        }
    }

    @Test
    void splitsThePreviousHelpMetadataRowDuringFormat24Migration() throws Exception {
        Path configFile = temporaryDirectory.resolve("config.yml");
        Files.writeString(configFile, """
                config_version: 23
                language: en_US
                messages:
                  help_menu_player:
                    - "divider"
                    - "<#FF69B4><bold>Plugin name: {name} | Author: {author} | Version: {version}</bold>"
                    - "commands"
                """, StandardCharsets.UTF_8);
        String defaults = """
                config_version: 24
                language: en_US
                messages:
                  help_menu_player:
                    - "divider"
                    - "<#FF69B4><bold>Plugin name: {name}</bold>"
                    - "<#D7C7FF><bold>Author: {author}</bold>"
                    - "<#D7C7FF><bold>Version: {version}</bold>"
                    - "commands"
                """;

        ConfigurationUpdater.UpdateResult result = ConfigurationUpdater.mergeMissingValues(
                configFile, new StringReader(defaults), "config_version", "1.14.1", false);

        assertTrue(result.changed());
        YamlConfiguration updated = loadWithComments(configFile);
        assertEquals(List.of("divider",
                        "<#FF69B4><bold>Plugin name: {name}</bold>",
                        "<#D7C7FF><bold>Author: {author}</bold>",
                        "<#D7C7FF><bold>Version: {version}</bold>",
                        "commands"),
                updated.getStringList("messages.help_menu_player"));
        assertEquals(24, updated.getInt("config_version"));
    }

    @Test
    void migratesKitloaderUpdaterContractFromFormat24() throws Exception {
        Path configFile = temporaryDirectory.resolve("config.yml");
        Files.writeString(configFile, """
                config-version: 24
                updates:
                  enabled: true
                  auto_download: false
                  notify_latest: true
                messages:
                  updater:
                    disabled: "legacy disabled"
                    checking: "legacy checking"
                    latest: "legacy latest {version}"
                    available: "legacy available {version}"
                    manual_download: "legacy manual"
                    downloaded: "legacy downloaded"
                    failed: "legacy failed"
                    custom_extension: "retain this"
                  reload_success: "<gradient:#FFB7D5:#D7C7FF:#B9E7FF:#D7C7FF:#FFB7D5>legacy reload</gradient>"
                """, StandardCharsets.UTF_8);
        String defaults = """
                config-version: 25
                updates:
                  enabled: true
                  auto-download: true
                messages:
                  update_checking: "default checking"
                  update_latest: "default latest"
                  update_available: "default available"
                  update_manual: "default manual"
                  update_downloaded: "default downloaded"
                  update_failed: "default failed"
                  reload_success: "default reload"
                """;

        ConfigurationUpdater.UpdateResult result = ConfigurationUpdater.mergeMissingValues(
                configFile, new StringReader(defaults), "config-version", "1.0.0", false);

        assertTrue(result.changed());
        YamlConfiguration updated = loadWithComments(configFile);
        assertEquals(25, updated.getInt("config-version"));
        assertFalse(updated.contains("config_version", true));
        assertFalse(updated.contains("updates.auto_download", true));
        assertFalse(updated.contains("updates.notify_latest", true));
        assertFalse(updated.contains("messages.updater.disabled", true));
        assertTrue(updated.contains("messages.updater", true));
        assertEquals(false, updated.getBoolean("updates.auto-download"));
        assertEquals("legacy checking", updated.getString("messages.update_checking"));
        assertEquals("legacy latest {version}", updated.getString("messages.update_latest"));
        assertEquals("legacy available {latest}", updated.getString("messages.update_available"));
        assertEquals("retain this", updated.getString("messages.updater.custom_extension"));
        assertEquals("<color:#55FF55>legacy reload</color>",
                updated.getString("messages.reload_success"));
    }

    @Test
    void migratesKnownLegacyConfigurationAfterPublicSchemaReset() throws Exception {
        Path configFile = temporaryDirectory.resolve("config.yml");
        Files.writeString(configFile, """
                config-version: 24
                updates:
                  enabled: true
                  auto_download: false
                  notify_latest: true
                messages:
                  updater:
                    checking: "legacy checking"
                """, StandardCharsets.UTF_8);
        String defaults = """
                config-version: 1
                updates:
                  enabled: true
                  auto-download: true
                messages:
                  update_checking: "default checking"
                """;

        ConfigurationUpdater.UpdateResult result = ConfigurationUpdater.mergeMissingValues(
                configFile, new StringReader(defaults), "config-version", "1.0.1", false);

        assertTrue(result.changed());
        assertFalse(result.blocked());
        YamlConfiguration updated = loadWithComments(configFile);
        assertEquals(25, updated.getInt("config-version"));
        assertEquals(false, updated.getBoolean("updates.auto-download"));
        assertFalse(updated.contains("updates.auto_download", true));
        assertEquals("legacy checking", updated.getString("messages.update_checking"));
    }

    @Test
    void stillRejectsUnknownSchemaAfterPublicSchemaReset() throws Exception {
        Path configFile = temporaryDirectory.resolve("config.yml");
        byte[] original = "config-version: 26\n".getBytes(StandardCharsets.UTF_8);
        Files.write(configFile, original);

        assertThrows(InvalidConfigurationException.class, () -> ConfigurationUpdater.mergeMissingValues(
                configFile, new StringReader("config-version: 1\n"), "config-version", "1.0.1", false));
        assertArrayEquals(original, Files.readAllBytes(configFile));
    }

    private YamlConfiguration loadWithComments(Path file) throws Exception {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.options().parseComments(true);
        configuration.load(file.toFile());
        return configuration;
    }
}
