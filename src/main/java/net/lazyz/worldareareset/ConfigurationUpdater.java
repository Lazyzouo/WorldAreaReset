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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class ConfigurationUpdater {

    private ConfigurationUpdater() {
    }

    static UpdateResult mergeMissingValues(Path targetFile, Reader defaultsReader,
                                           String versionPath, String releaseVersion)
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
        List<String> defaultPaths = new ArrayList<>(defaults.getKeys(true));
        defaultPaths.sort(Comparator.comparingInt(ConfigurationUpdater::pathDepth));

        List<String> conflicts = findConflicts(current, defaults, defaultPaths, versionPath);
        if (!conflicts.isEmpty()) {
            return new UpdateResult(0, false, null, conflicts);
        }

        int addedKeys = mergeMissingPaths(current, defaults, defaultPaths, versionPath);
        boolean versionUpdated = updateVersion(current, defaults, versionPath);
        if (addedKeys == 0 && !versionUpdated) {
            return new UpdateResult(0, false, null, List.of());
        }

        copyHeaderAndFooterWhenMissing(current, defaults);
        Path backupFile = saveWithBackup(current, normalizedTarget, releaseVersion);
        return new UpdateResult(addedKeys, versionUpdated, backupFile, List.of());
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
                if (current.contains(path, true) && !current.isInt(path)) {
                    conflicts.add(path);
                }
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
            if (path.equals(versionPath) || current.contains(path, true)) {
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

    private static boolean updateVersion(YamlConfiguration current, YamlConfiguration defaults,
                                         String versionPath) {
        if (versionPath == null) {
            return false;
        }
        if (!defaults.isInt(versionPath)) {
            throw new IllegalArgumentException("Default configuration is missing integer " + versionPath);
        }

        int targetVersion = defaults.getInt(versionPath);
        if (current.isInt(versionPath) && current.getInt(versionPath) >= targetVersion) {
            return false;
        }

        boolean wasMissing = !current.contains(versionPath, true);
        current.set(versionPath, targetVersion);
        if (wasMissing) {
            copyComments(current, defaults, versionPath);
        }
        return true;
    }

    private static void copyComments(YamlConfiguration target, YamlConfiguration defaults, String path) {
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

    private static Path saveWithBackup(YamlConfiguration configuration, Path targetFile,
                                       String releaseVersion) throws IOException {
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

            Path backupFile = nextBackupPath(targetFile, releaseVersion);
            Files.copy(targetFile, backupFile);
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

    private static Path nextBackupPath(Path targetFile, String releaseVersion) {
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
