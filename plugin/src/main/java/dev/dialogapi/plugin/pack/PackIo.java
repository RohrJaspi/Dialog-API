package dev.dialogapi.plugin.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** JSON snippets, paths, deterministic zip and SHA-1 shared by the pack builders. */
final class PackIo {

    private static final LocalDateTime ZIP_TIME = LocalDateTime.of(2024, 1, 1, 0, 0);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private PackIo() {
    }

    static JsonObject bitmapProvider(String file, int ascent, int height, int[][] codepoints) {
        JsonObject provider = new JsonObject();
        provider.addProperty("type", "bitmap");
        provider.addProperty("file", file);
        provider.addProperty("ascent", ascent);
        provider.addProperty("height", height);
        JsonArray chars = new JsonArray();
        for (int[] row : codepoints) {
            chars.add(new String(row, 0, row.length));
        }
        provider.add("chars", chars);
        return provider;
    }

    static JsonObject spaceProvider(Map<Integer, Integer> advances) {
        JsonObject provider = new JsonObject();
        provider.addProperty("type", "space");
        JsonObject map = new JsonObject();
        advances.forEach((cp, advance) -> map.addProperty(Character.toString(cp), advance));
        provider.add("advances", map);
        return provider;
    }

    static JsonObject providersObject(JsonArray providers) {
        JsonObject root = new JsonObject();
        root.add("providers", providers);
        return root;
    }

    /** {@code min_format}/{@code max_format} only: every supported client (1.21.9+) understands them. */
    static JsonObject packMeta(String description, int minFormat, int maxFormat) {
        JsonObject pack = new JsonObject();
        pack.addProperty("description", description);
        pack.addProperty("min_format", minFormat);
        pack.addProperty("max_format", Math.max(minFormat, maxFormat));
        JsonObject root = new JsonObject();
        root.add("pack", pack);
        return root;
    }

    /** "ns:font/x.png" -> "assets/ns/textures/font/x.png" */
    static String texturePath(String resource) {
        int colon = resource.indexOf(':');
        return "assets/" + resource.substring(0, colon) + "/textures/" + resource.substring(colon + 1);
    }

    /** font key "dialogapi:text/s1_m33" -> "assets/dialogapi/font/text/s1_m33.json" */
    static String fontPath(String namespace, String value) {
        return "assets/" + namespace + "/font/" + value + ".json";
    }

    static byte[] json(JsonObject object) {
        return (GSON.toJson(object) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    static void writeDirectory(Path packDir, Map<String, byte[]> files) throws IOException {
        PackFiles.deleteRecursively(packDir);
        for (Map.Entry<String, byte[]> entry : files.entrySet()) {
            Path target = packDir.resolve(entry.getKey());
            Files.createDirectories(target.getParent());
            Files.write(target, entry.getValue());
        }
    }

    /** Deterministic zip (sorted entries, fixed timestamps) so the SHA-1 only changes with the content. */
    static byte[] zip(Map<String, byte[]> files) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.setLevel(9);
            for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                ZipEntry zipEntry = new ZipEntry(entry.getKey());
                zipEntry.setTimeLocal(ZIP_TIME);
                zip.putNextEntry(zipEntry);
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    static String sha1(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
