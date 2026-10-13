package dev.dialogapi.plugin.pack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Test helper that reads the generated pack and lays out a component roughly the way the 1.21.11
 * client does: advances from the font providers, glyph top at {@code y + 7 - ascent}, bold = drawn
 * twice and +1 advance, drop shadow at +1/+1. It also enforces the client's rules (ascent <= height,
 * glyphs at most 256 px).
 *
 * <p>Text in the vanilla font is not part of the pack. It is approximated: 6 px per character
 * (7 when bold) and drawn as a solid block, which is enough to see where lines and glyphs end up.
 * If the pack folder contains {@code assets/minecraft/font/default.json} (previews write one), that
 * font is used instead.
 */
public final class BodySimulator {

    public static final int LINE_HEIGHT = 9;
    private static final int APPROX_CHAR_WIDTH = 6;
    private static final Key DEFAULT_FONT = Key.key("minecraft", "default");

    private record Bitmap(BufferedImage image, int ascent, int height, int cellWidth, int cellHeight,
                          Map<Integer, int[]> cells) {}

    private record FontDef(Map<Integer, Float> spaces, List<Bitmap> bitmaps) {}

    private record Piece(int codepoint, Style style) {}

    private final Path packDir;
    private final boolean hasDefaultFont;
    private final Map<Key, FontDef> fonts = new HashMap<>();

    public BodySimulator(Path packDir) {
        this.packDir = packDir;
        this.hasDefaultFont = Files.isRegularFile(packDir.resolve("assets/minecraft/font/default.json"));
    }

    /** Sum of the advances of the whole component (what the client centres text with). */
    public int width(Component component) {
        int width = 0;
        for (Piece piece : flatten(component)) {
            width += advance(piece);
        }
        return width;
    }

    /** Highest running width at any character: this is what the client's line wrapping looks at. */
    public int maxRunningWidth(Component component) {
        int max = 0;
        int running = 0;
        for (Piece piece : flatten(component)) {
            if (piece.codepoint() == '\n') {
                running = 0;
                continue;
            }
            running += advance(piece);
            max = Math.max(max, running);
        }
        return max;
    }

    /** Number of lines after the explicit line breaks and a greedy break at {@code wrapWidth}. */
    public int lineCount(Component component, int wrapWidth) {
        int lines = 1;
        int running = 0;
        for (Piece piece : flatten(component)) {
            if (piece.codepoint() == '\n') {
                lines++;
                running = 0;
                continue;
            }
            int advance = advance(piece);
            running += advance;
            if (running > wrapWidth) {
                lines++;
                running = advance;
            }
        }
        return lines;
    }

    /**
     * Draws a multi-line component. Every line starts at its own x: centred around {@code anchorX},
     * or at {@code anchorX} when not centred.
     */
    public void drawLines(Component component, BufferedImage canvas, int anchorX, int topY, boolean centered) {
        List<List<Piece>> lines = new ArrayList<>();
        lines.add(new ArrayList<>());
        for (Piece piece : flatten(component)) {
            if (piece.codepoint() == '\n') {
                lines.add(new ArrayList<>());
            } else {
                lines.getLast().add(piece);
            }
        }
        for (int i = 0; i < lines.size(); i++) {
            List<Piece> line = lines.get(i);
            int width = 0;
            for (Piece piece : line) {
                width += advance(piece);
            }
            float x = centered ? anchorX - width / 2 : anchorX;
            drawLine(line, canvas, x, topY + i * LINE_HEIGHT);
        }
    }

    private void drawLine(List<Piece> pieces, BufferedImage canvas, float startX, int lineY) {
        float x = startX;
        for (Piece piece : pieces) {
            Key font = piece.style().font();
            boolean bold = piece.style().decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE;
            int color = piece.style().color() != null ? piece.style().color().value() : 0xFFFFFF;
            if (isVanillaText(piece)) {
                int width = advance(piece);
                if (piece.codepoint() != ' ') {
                    fill(canvas, Math.round(x), lineY + 1, width - 1, 7, 0xFF000000 | color);
                }
                x += width;
                continue;
            }
            FontDef def = font(font);
            Float space = def.spaces().get(piece.codepoint());
            if (space != null) {
                x += space + (bold ? 1 : 0);
                continue;
            }
            Bitmap bitmap = bitmapFor(def, piece.codepoint(), font);
            ShadowColor shadow = piece.style().shadowColor();
            int shadowArgb = shadow != null ? shadow.value() : (0xFF << 24) | scale(color, 0.25f);
            int top = lineY + 7 - bitmap.ascent();
            int[] cell = bitmap.cells().get(piece.codepoint());
            if ((shadowArgb >>> 24) != 0) {
                blit(canvas, bitmap, cell, Math.round(x) + 1, top + 1, shadowArgb);
            }
            blit(canvas, bitmap, cell, Math.round(x), top, 0xFF000000 | color);
            x += advance(piece);
        }
    }

    /**
     * Text in the vanilla font that is approximated: everything when the pack has no default font,
     * otherwise only characters the preview default font lacks.
     */
    private boolean isVanillaText(Piece piece) {
        Key font = piece.style().font();
        boolean vanilla = font == null || font.equals(DEFAULT_FONT);
        if (!vanilla) {
            return false;
        }
        if (!hasDefaultFont) {
            return true;
        }
        FontDef def = font(DEFAULT_FONT);
        return !def.spaces().containsKey(piece.codepoint())
                && def.bitmaps().stream().noneMatch(bitmap -> bitmap.cells().containsKey(piece.codepoint()));
    }

    private int advance(Piece piece) {
        boolean bold = piece.style().decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE;
        if (isVanillaText(piece)) {
            return APPROX_CHAR_WIDTH + (bold ? 1 : 0);
        }
        Key fontKey = piece.style().font();
        FontDef def = font(fontKey);
        Float space = def.spaces().get(piece.codepoint());
        if (space != null) {
            return Math.round(space) + (bold ? 1 : 0);
        }
        Bitmap bitmap = bitmapFor(def, piece.codepoint(), fontKey);
        int[] cell = bitmap.cells().get(piece.codepoint());
        int opaque = Images.opaqueWidth(bitmap.image(), cell[0] * bitmap.cellWidth(), cell[1] * bitmap.cellHeight(),
                bitmap.cellWidth(), bitmap.cellHeight());
        return Images.clientAdvance(opaque, bitmap.height(), bitmap.cellHeight()) + (bold ? 1 : 0);
    }

    private static Bitmap bitmapFor(FontDef def, int codepoint, Key font) {
        for (Bitmap bitmap : def.bitmaps()) {
            if (bitmap.cells().containsKey(codepoint)) {
                return bitmap;
            }
        }
        throw new AssertionError("Character U+" + Integer.toHexString(codepoint) + " missing in font " + font);
    }

    private List<Piece> flatten(Component component) {
        List<Piece> pieces = new ArrayList<>();
        flatten(component, Style.empty(), pieces);
        return pieces;
    }

    private void flatten(Component component, Style parent, List<Piece> out) {
        Style style = parent.merge(component.style());
        if (component instanceof TextComponent text) {
            text.content().codePoints().forEach(cp -> out.add(new Piece(cp, style)));
        }
        for (Component child : component.children()) {
            flatten(child, style, out);
        }
    }

    private FontDef font(Key key) {
        return fonts.computeIfAbsent(key == null ? DEFAULT_FONT : key, this::loadFont);
    }

    private FontDef loadFont(Key key) {
        Path file = packDir.resolve("assets/" + key.namespace() + "/font/" + key.value() + ".json");
        try {
            JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<Integer, Float> spaces = new HashMap<>();
            List<Bitmap> bitmaps = new ArrayList<>();
            for (JsonElement element : json.getAsJsonArray("providers")) {
                JsonObject provider = element.getAsJsonObject();
                switch (provider.get("type").getAsString()) {
                    case "space" -> provider.getAsJsonObject("advances").entrySet().forEach(entry ->
                            spaces.putIfAbsent(entry.getKey().codePointAt(0), entry.getValue().getAsFloat()));
                    case "bitmap" -> bitmaps.add(loadBitmap(provider));
                    default -> throw new AssertionError("unexpected provider " + provider);
                }
            }
            return new FontDef(spaces, bitmaps);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Bitmap loadBitmap(JsonObject provider) throws IOException {
        String file = provider.get("file").getAsString();
        int colon = file.indexOf(':');
        Path path = packDir.resolve("assets/" + file.substring(0, colon) + "/textures/" + file.substring(colon + 1));
        BufferedImage image = Images.toArgb(ImageIO.read(path.toFile()));
        int ascent = provider.get("ascent").getAsInt();
        int height = provider.has("height") ? provider.get("height").getAsInt() : 8;
        if (ascent > height) {
            throw new AssertionError("Client would reject " + file + ": ascent " + ascent + " > height " + height);
        }
        List<String> rows = new ArrayList<>();
        provider.getAsJsonArray("chars").forEach(row -> rows.add(row.getAsString()));
        int columns = rows.getFirst().codePointCount(0, rows.getFirst().length());
        int cellWidth = image.getWidth() / columns;
        int cellHeight = image.getHeight() / rows.size();
        if (cellWidth > 256 || cellHeight > 256) {
            throw new AssertionError("Glyph of " + file + " is " + cellWidth + "x" + cellHeight + ", client limit 256");
        }
        Map<Integer, int[]> cells = new HashMap<>();
        for (int row = 0; row < rows.size(); row++) {
            int[] codepoints = rows.get(row).codePoints().toArray();
            for (int column = 0; column < codepoints.length; column++) {
                if (codepoints[column] != 0) {
                    cells.putIfAbsent(codepoints[column], new int[]{column, row});
                }
            }
        }
        return new Bitmap(image, ascent, height, cellWidth, cellHeight, cells);
    }

    private static void blit(BufferedImage canvas, Bitmap bitmap, int[] cell, int x, int y, int tint) {
        float scale = (float) bitmap.height() / bitmap.cellHeight();
        int w = Math.round(bitmap.cellWidth() * scale);
        int h = Math.round(bitmap.cellHeight() * scale);
        for (int dy = 0; dy < h; dy++) {
            for (int dx = 0; dx < w; dx++) {
                int sx = cell[0] * bitmap.cellWidth() + (int) (dx / scale);
                int sy = cell[1] * bitmap.cellHeight() + (int) (dy / scale);
                int src = bitmap.image().getRGB(sx, sy);
                int alpha = (int) ((src >>> 24) * ((tint >>> 24) / 255f));
                if (alpha == 0) {
                    continue;
                }
                int r = ((src >> 16) & 0xFF) * ((tint >> 16) & 0xFF) / 255;
                int g = ((src >> 8) & 0xFF) * ((tint >> 8) & 0xFF) / 255;
                int b = (src & 0xFF) * (tint & 0xFF) / 255;
                blend(canvas, x + dx, y + dy, (alpha << 24) | (r << 16) | (g << 8) | b);
            }
        }
    }

    private static void fill(BufferedImage canvas, int x, int y, int width, int height, int argb) {
        for (int dy = 0; dy < height; dy++) {
            for (int dx = 0; dx < width; dx++) {
                blend(canvas, x + dx, y + dy, argb);
            }
        }
    }

    private static void blend(BufferedImage canvas, int x, int y, int argb) {
        if (x < 0 || y < 0 || x >= canvas.getWidth() || y >= canvas.getHeight()) {
            return;
        }
        float a = (argb >>> 24) / 255f;
        int dst = canvas.getRGB(x, y);
        int r = (int) (((argb >> 16) & 0xFF) * a + ((dst >> 16) & 0xFF) * (1 - a));
        int g = (int) (((argb >> 8) & 0xFF) * a + ((dst >> 8) & 0xFF) * (1 - a));
        int b = (int) ((argb & 0xFF) * a + (dst & 0xFF) * (1 - a));
        canvas.setRGB(x, y, 0xFF000000 | (r << 16) | (g << 8) | b);
    }

    private static int scale(int rgb, float factor) {
        int r = (int) (((rgb >> 16) & 0xFF) * factor);
        int g = (int) (((rgb >> 8) & 0xFF) * factor);
        int b = (int) ((rgb & 0xFF) * factor);
        return (r << 16) | (g << 8) | b;
    }
}
