package dev.dialogapi.plugin.pack;

import com.google.gson.JsonArray;
import dev.dialogapi.api.glyph.GlyphWidths;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.key.Key;

/**
 * Writes the text fonts: one font per requested {@code (top, scale)}, all pointing at the same
 * (padded) glyph sheets and differing only in ascent and height.
 *
 * <p>With {@code H} the height of a sheet, {@code A} its ascent and {@code P} the transparent rows
 * added below every cell (GUI pixels), a font at {@code top} with size {@code scale} gets
 * <pre>
 *   height = scale * (H + P)
 *   ascent = 7 - top + scale * (A - 7)
 * </pre>
 * so the top of a letter cell lands on {@code top} and the sheet is drawn {@code scale} times larger.
 * The client rejects {@code ascent > height}; {@code P} is the smallest padding that satisfies every
 * request, rounded up to a whole number of texture pixels (so the pixel scale stays exact and the
 * glyph widths computed here equal the client's).
 */
final class TextFontFactory {

    static final String NAMESPACE = "dialogapi";

    record Request(int top, int scale) {}

    record Result(Map<Request, Key> keys, Map<Integer, GlyphWidths> widthsByScale) {}

    private record PaddedSheet(FontSheet sheet, String file, BufferedImage image, int cellHeight, int height) {}

    private TextFontFactory() {
    }

    static Key key(Request request) {
        String top = request.top() < 0 ? "m" + -request.top() : "p" + request.top();
        return Key.key(NAMESPACE, "text/s" + request.scale() + "_" + top);
    }

    static Result write(FontBundle font, Set<Request> requests, Map<Integer, Integer> offsets,
                        Map<String, byte[]> files) throws IOException {
        List<PaddedSheet> sheets = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (FontSheet sheet : font.sheets()) {
            String name = sheet.name();
            for (int i = 2; !names.add(name); i++) {
                name = sheet.name() + "_" + i;
            }
            PaddedSheet padded = pad(sheet, NAMESPACE + ":font/text/" + name + ".png", requests);
            files.put(PackIo.texturePath(padded.file()), Images.png(padded.image()));
            sheets.add(padded);
        }

        Map<Integer, GlyphWidths> widths = new HashMap<>();
        for (Request request : requests) {
            widths.computeIfAbsent(request.scale(), scale -> widths(font, sheets, scale));
        }

        Map<Request, Key> keys = new LinkedHashMap<>();
        List<Request> ordered = new ArrayList<>(requests);
        ordered.sort(Comparator.comparingInt(Request::scale).thenComparingInt(Request::top));
        for (Request request : ordered) {
            Map<Integer, Integer> spaces = new LinkedHashMap<>(offsets);
            font.spaces().forEach((cp, advance) -> spaces.putIfAbsent(cp, advance * request.scale()));
            JsonArray providers = new JsonArray();
            providers.add(PackIo.spaceProvider(spaces));
            for (PaddedSheet sheet : sheets) {
                int ascent = Metrics.ascent(request, sheet.sheet().ascent());
                int height = request.scale() * sheet.height();
                if (ascent > height) {
                    throw new IllegalStateException("ascent " + ascent + " > height " + height);
                }
                providers.add(PackIo.bitmapProvider(sheet.file(), ascent, height, sheet.sheet().codepoints()));
            }
            Key key = key(request);
            files.put(PackIo.fontPath(key.namespace(), key.value()), PackIo.json(PackIo.providersObject(providers)));
            keys.put(request, key);
        }
        return new Result(keys, widths);
    }

    /** Advances of the font drawn {@code scale} times larger, exactly as the client computes them. */
    private static GlyphWidths widths(FontBundle font, List<PaddedSheet> padded, int scale) {
        GlyphWidths.Builder widths = GlyphWidths.builder();
        font.spaces().forEach((cp, advance) -> widths.putIfAbsent(cp, advance * scale));
        for (PaddedSheet sheet : padded) {
            FontSheet source = sheet.sheet();
            for (int row = 0; row < source.rows(); row++) {
                for (int column = 0; column < source.columns(); column++) {
                    int cp = source.codepoints()[row][column];
                    if (cp == 0) {
                        continue;
                    }
                    int opaque = Images.opaqueWidth(source.image(), column * source.cellWidth(),
                            row * source.cellHeight(), source.cellWidth(), source.cellHeight());
                    widths.putIfAbsent(cp, Images.clientAdvance(opaque, scale * sheet.height(), sheet.cellHeight()));
                }
            }
        }
        return widths.build();
    }

    /**
     * Adds transparent rows below every glyph cell so that every request satisfies
     * {@code ascent <= height}, keeping the original pixel scale.
     */
    private static PaddedSheet pad(FontSheet sheet, String file, Set<Request> requests) throws IOException {
        int cellWidth = sheet.cellWidth();
        int cellHeight = sheet.cellHeight();
        int padGui = 0;
        int worstTop = 0;
        for (Request request : requests) {
            int needed = Metrics.requiredHeight(request, sheet.ascent());
            if (needed - sheet.height() > padGui) {
                padGui = needed - sheet.height();
                worstTop = request.top();
            }
        }
        // the padding must be a whole number of texture pixels
        int step = sheet.height() / gcd(cellHeight, sheet.height());
        padGui = (padGui + step - 1) / step * step;
        int padPixels = padGui * cellHeight / sheet.height();
        int newCellHeight = cellHeight + padPixels;
        if (newCellHeight > GlyphAllocator.MAX_GLYPH_SIZE || cellWidth > GlyphAllocator.MAX_GLYPH_SIZE) {
            throw new IOException("Font sheet '" + sheet.name() + "' would need " + cellWidth + "x" + newCellHeight
                    + " px glyph cells, the client limit is " + GlyphAllocator.MAX_GLYPH_SIZE
                    + ". Move the highest text (" + -worstTop + " px above its line) down or use a lower-resolution font.");
        }
        BufferedImage out = new BufferedImage(sheet.columns() * cellWidth, sheet.rows() * newCellHeight,
                BufferedImage.TYPE_INT_ARGB);
        for (int row = 0; row < sheet.rows(); row++) {
            for (int y = 0; y < cellHeight; y++) {
                for (int x = 0; x < out.getWidth(); x++) {
                    out.setRGB(x, row * newCellHeight + y, sheet.image().getRGB(x, row * cellHeight + y));
                }
            }
        }
        return new PaddedSheet(sheet, file, out, newCellHeight, sheet.height() + padGui);
    }

    private static int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    /** The two formulas of the class comment. */
    static final class Metrics {
        private Metrics() {
        }

        static int ascent(Request request, int sheetAscent) {
            return GlyphAllocator.VANILLA_LINE_ASCENT - request.top()
                    + request.scale() * (sheetAscent - GlyphAllocator.VANILLA_LINE_ASCENT);
        }

        /** Smallest {@code H + P} with {@code scale * (H + P) >= ascent}. */
        static int requiredHeight(Request request, int sheetAscent) {
            return Math.floorDiv(ascent(request, sheetAscent) + request.scale() - 1, request.scale());
        }
    }
}
