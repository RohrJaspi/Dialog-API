package dev.dialogapi.plugin.pack;

import java.awt.image.BufferedImage;

/**
 * A bitmap font sheet (one {@code bitmap} provider of a vanilla-style font definition).
 *
 * @param name       file name used inside the generated pack
 * @param ascent     ascent of the source provider (vanilla ascii: 7)
 * @param height     height of the source provider in GUI pixels (vanilla ascii: 8)
 * @param codepoints rows x columns of characters, 0 for empty cells
 */
public record FontSheet(String name, BufferedImage image, int ascent, int height, int[][] codepoints) {

    public int rows() {
        return codepoints.length;
    }

    public int columns() {
        return codepoints[0].length;
    }

    public int cellWidth() {
        return image.getWidth() / columns();
    }

    public int cellHeight() {
        return image.getHeight() / rows();
    }
}
