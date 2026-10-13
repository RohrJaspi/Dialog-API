package dev.dialogapi.plugin.pack;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small original pixel font (4 px wide letters, 6 px cap height; printable ASCII and a few
 * accented letters), used when the vanilla font sheets are not available. Glyphs are defined in
 * {@code fallback_font.txt}.
 */
final class FallbackFont {

    private static final int COLUMNS = 16;
    private static final int CELL = 8;
    private static final int WHITE = 0xFFFFFFFF;

    private FallbackFont() {
    }

    /** Writes glyphs.png and font.json into {@code dir}. */
    static void write(Path dir) throws IOException {
        Map<Integer, List<String>> glyphs = parse();
        List<Integer> chars = new ArrayList<>(glyphs.keySet());
        int rows = (chars.size() + COLUMNS - 1) / COLUMNS;

        BufferedImage image = new BufferedImage(COLUMNS * CELL, rows * CELL, BufferedImage.TYPE_INT_ARGB);
        JsonArray charRows = new JsonArray();
        for (int row = 0; row < rows; row++) {
            StringBuilder line = new StringBuilder();
            for (int column = 0; column < COLUMNS; column++) {
                int index = row * COLUMNS + column;
                if (index >= chars.size()) {
                    line.append('\u0000');
                    continue;
                }
                int cp = chars.get(index);
                line.appendCodePoint(cp);
                List<String> bitmap = glyphs.get(cp);
                for (int y = 0; y < Math.min(CELL, bitmap.size()); y++) {
                    String bits = bitmap.get(y);
                    for (int x = 0; x < Math.min(CELL, bits.length()); x++) {
                        if (bits.charAt(x) == '#') {
                            image.setRGB(column * CELL + x, row * CELL + y, WHITE);
                        }
                    }
                }
            }
            charRows.add(line.toString());
        }

        JsonObject space = new JsonObject();
        space.addProperty("type", "space");
        JsonObject advances = new JsonObject();
        advances.addProperty(" ", 4);
        advances.addProperty("‌", 0);
        space.add("advances", advances);

        JsonObject bitmap = new JsonObject();
        bitmap.addProperty("type", "bitmap");
        bitmap.addProperty("file", "glyphs.png");
        bitmap.addProperty("ascent", 7);
        bitmap.addProperty("height", 8);
        bitmap.add("chars", charRows);

        JsonArray providers = new JsonArray();
        providers.add(space);
        providers.add(bitmap);
        JsonObject root = new JsonObject();
        root.add("providers", providers);

        Files.createDirectories(dir);
        Files.write(dir.resolve("glyphs.png"), Images.png(image));
        Files.writeString(dir.resolve("font.json"),
                new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root),
                StandardCharsets.UTF_8);
    }

    /**
     * Format: a line {@code =X} (or {@code =X <firstRow>}) starts glyph X, followed by its rows of
     * {@code #} (pixel) and {@code .} (empty). Lines starting with {@code //} are comments.
     * Returns every glyph as 8 rows (row 0 = top of the cell).
     */
    static Map<Integer, List<String>> parse() throws IOException {
        Map<Integer, List<String>> glyphs = new LinkedHashMap<>();
        try (InputStream in = FallbackFont.class.getResourceAsStream("/fallback_font.txt")) {
            if (in == null) {
                throw new IOException("fallback_font.txt missing from the plugin jar");
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            List<String> current = null;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("//") || line.isBlank()) {
                    continue;
                }
                if (line.startsWith("=") && line.length() > 1) {
                    int cp = line.codePointAt(1);
                    String start = line.substring(1 + Character.charCount(cp)).strip();
                    current = new ArrayList<>();
                    for (int row = start.isEmpty() ? 0 : Integer.parseInt(start); row > 0; row--) {
                        current.add("");
                    }
                    glyphs.put(cp, current);
                } else if (current != null) {
                    current.add(line.strip());
                }
            }
        }
        return glyphs;
    }
}
