package net.lazyz.worldareareset;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
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

    public static final String REPOSITORY = "Lazyzouo/WorldAreaReset";
    public static final String PROJECT_URL = "https://github.com/" + REPOSITORY;
    /** Alias used by the Kitloader updater contract. */
    public static final String REPOSITORY_URL = PROJECT_URL;
    public static final String RELEASES_URL = PROJECT_URL + "/releases";
    private static final String ASSET_PREFIX = "WorldAreaReset-";
    private static final String ASSET_SUFFIX = ".jar";
    private static final URI LATEST_RELEASE_API = URI.create(
            "https://api.github.com/repos/Lazyzouo/WorldAreaReset/releases/latest");
    private static final long MAX_DOWNLOAD_BYTES = 50L * 1024L * 1024L;

    private final WorldAreaResetPlugin plugin;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public UpdateChecker(WorldAreaResetPlugin plugin) {
        this.plugin = plugin;
    }

    public void checkOnStartup() {
        if (!plugin.getConfig().getBoolean("updates.enabled", true)) return;

        plugin.logLocalized("update_checking", "Checking the official WorldAreaReset GitHub Release for updates.");
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
            plugin.logLocalized("update_latest", "WorldAreaReset {version} is already the latest version.", "{version}", currentVersion);
            return;
        }

        plugin.logLocalized("update_available", "WorldAreaReset {latest} is available; the current version is {current}.",
                "{latest}", latestVersion, "{current}", currentVersion);

        if (!plugin.getConfig().getBoolean("updates.auto-download", true)) {
            plugin.logLocalized("update_manual",
                    "WorldAreaReset {version} is available. Download it from the official Releases page: {url}",
                    "{version}", latestVersion, "{url}", RELEASES_URL);
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

        httpClient.sendAsync(request(downloadUrl), HttpResponse.BodyHandlers.ofInputStream())
                .thenAccept(response -> installUpdate(response, expectedDigest, latestVersion))
                .exceptionally(error -> {
                    reportFailure(rootCause(error).getMessage());
                    return null;
                });
    }

    private void installUpdate(HttpResponse<InputStream> response, String expectedDigest, String latestVersion) {
        Path temporary = null;
        try {
            requireSuccess(response);

            Path runningJar = Path.of(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            if (!Files.isRegularFile(runningJar) || !runningJar.getFileName().toString().endsWith(".jar")) {
                throw new IOException("Plugin is not running from a JAR file");
            }

            Path updateDirectory = plugin.getServer().getUpdateFolderFile().toPath();
            Files.createDirectories(updateDirectory);
            Path target = updateDirectory.resolve(expectedAssetName(latestVersion));
            temporary = Files.createTempFile(updateDirectory, "worldareareset-", ".download");
            download(response, temporary);
            verifyDigest(temporary, expectedDigest);

            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            temporary = null;

            plugin.logLocalized("update_downloaded",
                    "Version {version} was downloaded and will be installed on the next server restart.",
                    "{version}", latestVersion);
        } catch (Exception error) {
            reportFailure(error.getMessage());
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // The failed temporary file is harmless and will be
                    // replaced on the next update attempt.
                }
            }
            try {
                response.body().close();
            } catch (IOException ignored) {
                // The response stream is best-effort cleanup only.
            }
        }
    }

    private void download(HttpResponse<InputStream> response, Path destination)
            throws IOException {
        long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        if (contentLength > MAX_DOWNLOAD_BYTES) {
            throw new IOException("release asset exceeds 50 MiB");
        }

        try (InputStream input = response.body(); var output = Files.newOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            long total = 0L;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_DOWNLOAD_BYTES) {
                    throw new IOException("release asset exceeds 50 MiB");
                }
                output.write(buffer, 0, read);
            }
        }
    }

    private JsonObject findJarAsset(JsonArray assets, String version) {
        String expectedName = expectedAssetName(version);
        for (JsonElement element : assets) {
            JsonObject asset = element.getAsJsonObject();
            String name = asset.get("name").getAsString();
            if (name.equals(expectedName)) {
                String digest = asset.has("digest") && !asset.get("digest").isJsonNull()
                        ? asset.get("digest").getAsString() : "";
                if (!digest.matches("(?i)^sha256:[0-9a-f]{64}$")) {
                    throw new IllegalStateException("release asset has no valid SHA-256 digest: " + expectedName);
                }
                return asset;
            }
        }
        return null;
    }

    private String expectedAssetName(String version) {
        return ASSET_PREFIX + version + ASSET_SUFFIX;
    }

    private void verifyDigest(Path file, String expectedDigest) throws Exception {
        if (expectedDigest == null || !expectedDigest.matches("(?i)^sha256:[0-9a-f]{64}$")) {
            throw new IOException("release asset has no valid SHA-256 digest");
        }

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        String actual = HexFormat.of().formatHex(digest.digest());
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
        plugin.logLocalized("update_failed",
                "Update failed: {reason}. Download manually from {url}",
                "{reason}", reason == null ? "unknown error" : reason,
                "{url}", RELEASES_URL);
    }
}
