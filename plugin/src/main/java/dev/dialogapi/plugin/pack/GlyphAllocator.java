package dev.dialogapi.plugin.pack;

import com.google.gson.JsonArray;
import dev.dialogapi.api.glyph.Glyph;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Writes glyph textures and allocates one private-use character per slice and placement.
 *
 * <p>A bitmap glyph is drawn with its top edge at {@code lineY + 7 - ascent}. The client rejects a
 * provider whose {@code ascent} is greater than its {@code height}, so a glyph that has to be raised
 * gets transparent rows below it until {@code height >= ascent}; the visible pixels keep a 1:1 scale.
 * One padded texture is shared by all placements of the same image at different heights (they
 * only differ in ascent). Textures wider than 256 px are split into 256 px slices.
 */
final class GlyphAllocator {

    /** Glyphs are stitched into 256x256 atlas pages; larger glyphs render as the missing glyph. */
    static final int MAX_GLYPH_SIZE = 256;
    static final int VANILLA_LINE_ASCENT = 7;

    private final Map<String, byte[]> files;
    private final CodepointStore codepoints;
    private final JsonArray providers = new JsonArray();

    GlyphAllocator(Map<String, byte[]> files, CodepointStore codepoints) {
        this.files = files;
        this.codepoints = codepoints;
    }

    JsonArray providers() {
        return providers;
    }

    /**
     * @param name  texture name below {@code <namespace>:font/glyph/}
     * @param tops  one glyph is created per value: the y of the texture's top edge relative to the top of the text line
     * @return the glyphs, in the order of {@code tops}
     */
    List<Glyph> add(String namespace, String name, BufferedImage image, int... tops) throws IOException {
        int maxAscent = Integer.MIN_VALUE;
        for (int top : tops) {
            maxAscent = Math.max(maxAscent, VANILLA_LINE_ASCENT - top);
        }
        int height = Math.max(image.getHeight(), maxAscent);
        if (height > MAX_GLYPH_SIZE) {
            throw new IOException("Glyph '" + namespace + ":" + name + "' is placed too high: it needs a glyph height of "
                    + height + " px but the client limit is " + MAX_GLYPH_SIZE + ". Lower it in the layout.");
        }
        BufferedImage padded = Images.padBottom(image, height);

        int sliceCount = (image.getWidth() + MAX_GLYPH_SIZE - 1) / MAX_GLYPH_SIZE;
        List<String> sliceFiles = new ArrayList<>();
        List<Integer> sliceAdvances = new ArrayList<>();
        Map<String, byte[]> staged = new java.util.LinkedHashMap<>();
        for (int slice = 0; slice < sliceCount; slice++) {
            int x0 = slice * MAX_GLYPH_SIZE;
            int width = Math.min(MAX_GLYPH_SIZE, image.getWidth() - x0);
            BufferedImage part = sliceCount == 1 ? padded : Images.crop(padded, x0, 0, width, height);
            String file = namespace + ":font/glyph/" + name + (sliceCount == 1 ? "" : "_" + slice) + ".png";
            staged.put(PackIo.texturePath(file), Images.png(part));
            sliceFiles.add(file);
            sliceAdvances.add(Images.clientAdvance(Images.opaqueWidth(part, 0, 0, width, height), height, height));
        }
        files.putAll(staged);

        List<Glyph> glyphs = new ArrayList<>();
        for (int top : tops) {
            List<Glyph.Slice> slices = new ArrayList<>();
            for (int slice = 0; slice < sliceCount; slice++) {
                int cp = codepoints.codepoint(namespace + ":" + name + "@" + top + "#" + slice);
                providers.add(PackIo.bitmapProvider(sliceFiles.get(slice), VANILLA_LINE_ASCENT - top, height,
                        new int[][]{{cp}}));
                slices.add(new Glyph.Slice(cp, slice * MAX_GLYPH_SIZE, sliceAdvances.get(slice)));
            }
            glyphs.add(new Glyph(slices, image.getWidth(), image.getHeight()));
        }
        return glyphs;
    }
}
