package dev.dialogapi.plugin.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Uses the vanilla glyph sheets (ascii, accented, nonlatin_european) so dialog text looks exactly
 * like normal Minecraft text. The sheets are taken from the official client jar, which is
 * downloaded once from Mojang (SHA-1 verified) and cached in {@code cache/vanilla-font/}.
 * Only the font definition and its PNGs are kept; the jar is deleted afterwards.
 */
final class VanillaFontSource {

    private static final String MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final String CACHE_DIR = "vanilla-font";
    static final String FALLBACK_VERSION = "1.21.11";
    private static final String DEFINITION = "assets/minecraft/font/include/default.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private VanillaFontSource() {
    }

    /**
     * The font files are identical in every supported version (1.21.9 - 26.x), so one cache folder
     * serves all of them. If the running version is not in the manifest (a snapshot, for example) the
     * font of {@link #FALLBACK_VERSION} is used.
     */
    static FontBundle load(Path cacheRoot, String version, Logger logger) throws IOException, InterruptedException {
        Path dir = locate(cacheRoot);
        if (dir == null) {
            dir = cacheRoot.resolve(CACHE_DIR);
            try {
                download(dir, version, logger);
            } catch (VersionNotFoundException e) {
                if (version.equals(FALLBACK_VERSION)) {
                    throw e;
                }
                logger.info("Minecraft " + version + " is not in the version manifest, using the " + FALLBACK_VERSION + " font.");
                download(dir, FALLBACK_VERSION, logger);
            }
        }
        Path definition = dir.resolve("default.json");
        Path source = dir;
        JsonObject json = JsonParser.parseString(Files.readString(definition, StandardCharsets.UTF_8)).getAsJsonObject();
        FontBundle bundle = FontDefinitions.parse(json, file -> Images.read(source.resolve(file)),
                "vanilla font");
        // include/space.json of the vanilla font
        Map<Integer, Integer> spaces = Map.of((int) ' ', 4, 0x200C, 0);
        return new FontBundle(bundle.sheets(), spaces, bundle.origin());
    }

    /** {@code cache/vanilla-font}, or a folder of an older layout ({@code vanilla-font-<version>}). */
    private static Path locate(Path cacheRoot) throws IOException {
        Path current = cacheRoot.resolve(CACHE_DIR);
        if (Files.isRegularFile(current.resolve("default.json"))) {
            return current;
        }
        if (Files.isDirectory(cacheRoot)) {
            try (var list = Files.list(cacheRoot)) {
                return list.filter(path -> path.getFileName().toString().startsWith(CACHE_DIR + "-"))
                        .filter(path -> Files.isRegularFile(path.resolve("default.json")))
                        .sorted().findFirst().orElse(null);
            }
        }
        return null;
    }

    private static final class VersionNotFoundException extends IOException {
        VersionNotFoundException(String version) {
            super("Minecraft version " + version + " not found in the Mojang version manifest");
        }
    }

    private static void download(Path dir, String version, Logger logger) throws IOException, InterruptedException {
        logger.info("Downloading the Minecraft " + version + " client jar once to extract the vanilla font sheets...");
        HttpClient http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build();

        JsonObject manifest = getJson(http, MANIFEST);
        String versionUrl = null;
        for (JsonElement element : manifest.getAsJsonArray("versions")) {
            JsonObject entry = element.getAsJsonObject();
            if (entry.get("id").getAsString().equals(version)) {
                versionUrl = entry.get("url").getAsString();
                break;
            }
        }
        if (versionUrl == null) {
            throw new VersionNotFoundException(version);
        }
        JsonObject client = getJson(http, versionUrl).getAsJsonObject("downloads").getAsJsonObject("client");
        String jarUrl = client.get("url").getAsString();
        String expectedSha1 = client.get("sha1").getAsString();

        Files.createDirectories(dir.getParent());
        Path jar = Files.createTempFile(dir.getParent(), "client-", ".jar");
        Path staging = Files.createTempDirectory(dir.getParent(), "font-staging-");
        try {
            HttpResponse<InputStream> response = http.send(request(jarUrl), HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode() + " for " + jarUrl);
            }
            MessageDigest sha1 = sha1();
            try (InputStream in = new DigestInputStream(response.body(), sha1);
                 OutputStream out = Files.newOutputStream(jar)) {
                in.transferTo(out);
            }
            String actual = HexFormat.of().formatHex(sha1.digest());
            if (!actual.equalsIgnoreCase(expectedSha1)) {
                throw new IOException("Client jar SHA-1 mismatch (expected " + expectedSha1 + ", got " + actual + ")");
            }

            try (ZipFile zip = new ZipFile(jar.toFile())) {
                JsonObject definition = JsonParser.parseString(readEntry(zip, DEFINITION)).getAsJsonObject();
                for (JsonElement element : definition.getAsJsonArray("providers")) {
                    JsonObject provider = element.getAsJsonObject();
                    if (!"bitmap".equals(provider.get("type").getAsString())) {
                        continue;
                    }
                    String file = provider.get("file").getAsString();
                    String namespace = file.contains(":") ? file.substring(0, file.indexOf(':')) : "minecraft";
                    String path = file.substring(file.indexOf(':') + 1);
                    String localName = FontDefinitions.sheetName(file) + ".png";
                    ZipEntry entry = zip.getEntry("assets/" + namespace + "/textures/" + path);
                    if (entry == null) {
                        throw new IOException("Missing " + path + " in client jar");
                    }
                    try (InputStream in = zip.getInputStream(entry)) {
                        Files.copy(in, staging.resolve(localName), StandardCopyOption.REPLACE_EXISTING);
                    }
                    provider.addProperty("file", localName);
                }
                Files.writeString(staging.resolve("default.json"), GSON.toJson(definition), StandardCharsets.UTF_8);
            }
            if (Files.exists(dir)) {
                PackFiles.deleteRecursively(dir);
            }
            Files.move(staging, dir, StandardCopyOption.ATOMIC_MOVE);
            logger.info("Vanilla font sheets cached in " + dir);
        } finally {
            Files.deleteIfExists(jar);
            if (Files.exists(staging)) {
                PackFiles.deleteRecursively(staging);
            }
        }
    }

    private static JsonObject getJson(HttpClient http, String url) throws IOException, InterruptedException {
        HttpResponse<String> response = http.send(request(url), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " for " + url);
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static HttpRequest request(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(5))
                .header("User-Agent", "DialogAPI (Paper plugin)")
                .build();
    }

    private static String readEntry(ZipFile zip, String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) {
            throw new IOException("Missing " + name + " in client jar");
        }
        try (InputStream in = zip.getInputStream(entry)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static MessageDigest sha1() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
