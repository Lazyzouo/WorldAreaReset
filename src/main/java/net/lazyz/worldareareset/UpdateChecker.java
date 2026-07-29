package net.lazyz.worldareareset;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.CompletionException;

public final class UpdateChecker {

    public static final String PROJECT_URL = "https://github.com/Lazyzouo/WorldAreaReset";
    private static final String ASSET_PREFIX = "WorldAreaReset-";
    private static final String ENGLISH_ASSET_SUFFIX = "-en.us.jar";
    private static final String CHINESE_ASSET_SUFFIX = "-zh.cn.jar";
    private static final URI LATEST_RELEASE_API = URI.create(
            "https://api.github.com/repos/Lazyzouo/WorldAreaReset/releases/latest");

    private final WorldAreaResetPlugin plugin;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public UpdateChecker(WorldAreaResetPlugin plugin) {
        this.plugin = plugin;
    }

    public void checkOnStartup() {
        if (!plugin.getConfig().getBoolean("updates.enabled", true)) {
            plugin.logLocalized("updater.disabled", "Update checks are disabled.");
            return;
        }

        plugin.logLocalized("updater.checking", "Checking GitHub for updates...");
        HttpRequest request = request(LATEST_RELEASE_API);

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(this::requireSuccess)
                .thenApply(JsonParser::parseString)
                .thenApply(JsonElement::getAsJsonObject)
                .thenAccept(this::handleRelease)
                .exceptionally(error -> {
                    reportFailure(rootCause(error).getMessage());
                    return null;
                });
    }

    private void handleRelease(JsonObject release) {
        String latestVersion = release.get("tag_name").getAsString().replaceFirst("^[vV]", "");
        String currentVersion = plugin.getPluginMeta().getVersion();

        if (compareVersions(latestVersion, currentVersion) <= 0) {
            if (plugin.getConfig().getBoolean("updates.notify_latest", true)) {
                plugin.logLocalized("updater.latest",
                        "WorldAreaReset {version} is already the latest version.", "{version}", currentVersion);
            }
            return;
        }

        plugin.logLocalized("updater.available",
                "A new version {version} is available (current: {current}).",
                "{version}", latestVersion, "{current}", currentVersion);

        if (!plugin.getConfig().getBoolean("updates.auto_download", true)) {
            plugin.logLocalized("updater.manual_download",
                    "Automatic download is disabled. Download it from {url}", "{url}", PROJECT_URL + "/releases/latest");
            return;
        }

        JsonObject jarAsset = findJarAsset(release.getAsJsonArray("assets"), latestVersion);
        if (jarAsset == null) {
            throw new IllegalStateException("The latest release does not contain the expected "
                    + expectedAssetName(latestVersion) + " asset");
        }

        URI downloadUrl = URI.create(jarAsset.get("browser_download_url").getAsString());
        String expectedDigest = jarAsset.has("digest") && !jarAsset.get("digest").isJsonNull()
                ? jarAsset.get("digest").getAsString()
                : null;

        httpClient.sendAsync(request(downloadUrl), HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(this::requireSuccess)
                .thenAccept(bytes -> installUpdate(bytes, expectedDigest, latestVersion))
                .exceptionally(error -> {
                    reportFailure(rootCause(error).getMessage());
                    return null;
                });
    }

    private void installUpdate(byte[] bytes, String expectedDigest, String latestVersion) {
        try {
            verifyDigest(bytes, expectedDigest);

            Path runningJar = Path.of(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            if (!Files.isRegularFile(runningJar) || !runningJar.getFileName().toString().endsWith(".jar")) {
                throw new IOException("Plugin is not running from a JAR file");
            }

            Path updateDirectory = plugin.getServer().getUpdateFolderFile().toPath();
            Files.createDirectories(updateDirectory);
            Path target = updateDirectory.resolve(runningJar.getFileName());
            Path temporary = updateDirectory.resolve(runningJar.getFileName() + ".tmp");
            Files.write(temporary, bytes);

            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }

            plugin.logLocalized("updater.downloaded",
                    "Version {version} was downloaded and will be installed on the next server restart.",
                    "{version}", latestVersion);
        } catch (Exception error) {
            reportFailure(error.getMessage());
        }
    }

    private JsonObject findJarAsset(JsonArray assets, String version) {
        String expectedName = expectedAssetName(version);
        for (JsonElement element : assets) {
            JsonObject asset = element.getAsJsonObject();
            String name = asset.get("name").getAsString();
            if (name.equals(expectedName)) {
                return asset;
            }
        }
        return null;
    }

    private String expectedAssetName(String version) {
        String language = plugin.getConfig().getString("language", "zh_CN");
        if (language != null && language.replace('-', '_').equalsIgnoreCase("en_US")) {
            return ASSET_PREFIX + version + ENGLISH_ASSET_SUFFIX;
        }
        return ASSET_PREFIX + version + CHINESE_ASSET_SUFFIX;
    }

    private void verifyDigest(byte[] bytes, String expectedDigest) throws Exception {
        if (expectedDigest == null || !expectedDigest.startsWith("sha256:")) {
            return;
        }

        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        String expected = expectedDigest.substring("sha256:".length());
        if (!actual.equalsIgnoreCase(expected)) {
            throw new IOException("Downloaded JAR failed SHA-256 verification");
        }
    }

    private HttpRequest request(URI uri) {
        return HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "WorldAreaReset/" + plugin.getPluginMeta().getVersion())
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET()
                .build();
    }

    private <T> T requireSuccess(HttpResponse<T> response) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("GitHub returned HTTP " + response.statusCode());
        }
        return response.body();
    }

    private int compareVersions(String left, String right) {
        String[] leftParts = left.split("[.-]");
        String[] rightParts = right.split("[.-]");
        int length = Math.max(leftParts.length, rightParts.length);

        for (int i = 0; i < length; i++) {
            int leftValue = i < leftParts.length ? numericPart(leftParts[i]) : 0;
            int rightValue = i < rightParts.length ? numericPart(rightParts[i]) : 0;
            if (leftValue != rightValue) {
                return Integer.compare(leftValue, rightValue);
            }
        }
        return 0;
    }

    private int numericPart(String part) {
        String digits = part.replaceAll("[^0-9].*$", "");
        return digits.isEmpty() ? 0 : Integer.parseInt(digits);
    }

    private Throwable rootCause(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current.getCause() != null) && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private void reportFailure(String reason) {
        plugin.logLocalized("updater.failed",
                "Update failed: {reason}. Download manually from {url}",
                "{reason}", reason == null ? "unknown error" : reason,
                "{url}", PROJECT_URL + "/releases/latest");
    }
}
