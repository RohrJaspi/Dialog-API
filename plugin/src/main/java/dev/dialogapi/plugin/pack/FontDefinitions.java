package dev.dialogapi.plugin.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/** Reads vanilla-style font definition files ({@code {"providers": [...]}}). */
final class FontDefinitions {

    interface ImageLoader {
        BufferedImage load(String file) throws IOException;
    }

    private FontDefinitions() {
    }

    static FontBundle parse(JsonObject definition, ImageLoader images, String origin) throws IOException {
        List<FontSheet> sheets = new ArrayList<>();
        Map<Integer, Integer> spaces = new LinkedHashMap<>();
        for (JsonElement element : definition.getAsJsonArray("providers")) {
            JsonObject provider = element.getAsJsonObject();
            String type = provider.get("type").getAsString().toLowerCase(Locale.ROOT);
            switch (type) {
                case "bitmap" -> {
                    String file = provider.get("file").getAsString();
                    int ascent = provider.get("ascent").getAsInt();
                    int height = provider.has("height") ? provider.get("height").getAsInt() : 8;
                    int[][] rows = parseChars(provider.getAsJsonArray("chars"), file);
                    sheets.add(new FontSheet(sheetName(file), images.load(file), ascent, height, rows));
                }
                case "space" -> {
                    for (Map.Entry<String, JsonElement> entry : provider.getAsJsonObject("advances").entrySet()) {
                        spaces.putIfAbsent(entry.getKey().codePointAt(0), Math.round(entry.getValue().getAsFloat()));
                    }
                }
                default -> {
                    // reference, unihex, ttf: not usable for shifted text, skip
                }
            }
        }
        if (sheets.isEmpty()) {
            throw new IOException("Font definition of " + origin + " has no bitmap provider");
        }
        return new FontBundle(sheets, spaces, origin);
    }

    private static int[][] parseChars(JsonArray chars, String file) throws IOException {
        int[][] rows = new int[chars.size()][];
        for (int i = 0; i < rows.length; i++) {
            rows[i] = chars.get(i).getAsString().codePoints().toArray();
            if (rows[i].length != rows[0].length) {
                throw new IOException("Rows of 'chars' have different lengths in provider " + file);
            }
        }
        if (rows.length == 0 || rows[0].length == 0) {
            throw new IOException("Empty 'chars' in provider " + file);
        }
        return rows;
    }

    /** "minecraft:font/ascii.png" -> "ascii" */
    static String sheetName(String file) {
        String name = file.substring(file.lastIndexOf('/') + 1);
        name = name.substring(name.indexOf(':') + 1);
        if (name.endsWith(".png")) {
            name = name.substring(0, name.length() - 4);
        }
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
    }
}
