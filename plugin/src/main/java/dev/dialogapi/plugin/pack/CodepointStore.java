package dev.dialogapi.plugin.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * Remembers which private-use character belongs to which glyph ({@code <namespace>:<id>@<top>#<slice>}).
 *
 * <p>Without it every new registration, removed plugin or changed layout would renumber all glyphs,
 * and clients that still hold the previous pack (for example because it is hosted externally) would
 * draw garbage until the pack is re-uploaded. Codepoints are never reused for a different glyph.
 */
final class CodepointStore {

    static final int FIRST = 0xE000;
    /** The offset characters ({@link dev.dialogapi.api.glyph.OffsetChars}) live here. */
    static final int RESERVED_FROM = 0xE800;
    static final int RESERVED_TO = 0xE83F;
    /** Plane 15 private use area, used when the BMP block is full. */
    static final int PLANE_15 = 0xF0000;
    private static final int PLANE_15_END = 0xFFFFD;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, Integer> assigned = new TreeMap<>();
    private int next = FIRST;

    static CodepointStore load(Path file, Logger logger) {
        CodepointStore store = new CodepointStore();
        if (Files.isRegularFile(file)) {
            try {
                JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                    store.assigned.put(entry.getKey(), entry.getValue().getAsInt());
                }
            } catch (Exception e) {
                logger.warning("Ignoring unreadable " + file.getFileName() + ": " + e.getMessage());
                store.assigned.clear();
            }
        }
        store.next = FIRST;
        for (int cp : store.assigned.values()) {
            store.next = Math.max(store.next, cp + 1);
        }
        store.next = skipReserved(store.next);
        return store;
    }

    int codepoint(String key) throws IOException {
        Integer existing = assigned.get(key);
        if (existing != null) {
            return existing;
        }
        if (next > PLANE_15_END) {
            throw new IOException("No private-use characters left for glyph " + key);
        }
        int cp = next;
        assigned.put(key, cp);
        next = skipReserved(cp + 1);
        return cp;
    }

    private static int skipReserved(int cp) {
        if (cp >= RESERVED_FROM && cp <= RESERVED_TO) {
            return RESERVED_TO + 1;
        }
        if (cp > 0xF8FF && cp < PLANE_15) {
            return PLANE_15;
        }
        return cp;
    }

    void save(Path file) throws IOException {
        JsonObject json = new JsonObject();
        assigned.forEach(json::addProperty);
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.writeString(file, GSON.toJson(json) + "\n", StandardCharsets.UTF_8);
    }

    Map<String, Integer> assigned() {
        return Map.copyOf(assigned);
    }
}
