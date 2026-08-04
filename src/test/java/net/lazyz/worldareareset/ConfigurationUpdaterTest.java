package net.lazyz.worldareareset;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
            config_version: 4
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
                configFile, new StringReader(DEFAULT_CONFIG), "config_version", "1.3.0");

        assertTrue(result.changed());
        assertFalse(result.blocked());
        assertEquals(2, result.addedKeys());
        assertTrue(result.versionUpdated());
        assertEquals(configFile.resolveSibling("config.yml.before-v1.3.0.bak"), result.backupFile());
        assertArrayEquals(originalBytes, Files.readAllBytes(result.backupFile()));

        YamlConfiguration updated = loadWithComments(configFile);
        assertEquals(4, updated.getInt("config_version"));
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
                configFile, new StringReader(DEFAULT_CONFIG), "config_version", "1.3.0");
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
                configFile, new StringReader(DEFAULT_CONFIG), "config_version", "1.3.0");

        assertTrue(result.blocked());
        assertFalse(result.changed());
        assertTrue(result.conflicts().contains("config_version"));
        assertTrue(result.conflicts().contains("cleanup"));
        assertTrue(result.conflicts().contains("updates.enabled"));
        assertArrayEquals(originalBytes, Files.readAllBytes(configFile));
        assertFalse(Files.exists(configFile.resolveSibling("config.yml.before-v1.3.0.bak")));
    }

    @Test
    void leavesMalformedYamlUntouched() throws Exception {
        Path configFile = temporaryDirectory.resolve("config.yml");
        byte[] originalBytes = "cleanup: [unterminated".getBytes(StandardCharsets.UTF_8);
        Files.write(configFile, originalBytes);

        assertThrows(InvalidConfigurationException.class, () -> ConfigurationUpdater.mergeMissingValues(
                configFile, new StringReader(DEFAULT_CONFIG), "config_version", "1.3.0"));

        assertArrayEquals(originalBytes, Files.readAllBytes(configFile));
        assertFalse(Files.exists(configFile.resolveSibling("config.yml.before-v1.3.0.bak")));
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
                languageFile, new StringReader(defaults), null, "1.3.0");

        assertTrue(result.changed());
        assertEquals(1, result.addedKeys());
        assertFalse(result.versionUpdated());
        YamlConfiguration updated = loadWithComments(languageFile);
        assertEquals("&c[Custom] ", updated.getString("prefix"));
        assertEquals("New message", updated.getString("new_notice"));
        assertTrue(Files.exists(languageFile.resolveSibling("en_US.yml.before-v1.3.0.bak")));
    }

    private YamlConfiguration loadWithComments(Path file) throws Exception {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.options().parseComments(true);
        configuration.load(file.toFile());
        return configuration;
    }
}
