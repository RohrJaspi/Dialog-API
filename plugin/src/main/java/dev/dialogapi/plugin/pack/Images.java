package dev.dialogapi.plugin.pack;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Small image helpers for the pack generator. All images are handled as TYPE_INT_ARGB. */
public final class Images {

    private Images() {
    }

    public static BufferedImage read(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return read(in, path.toString());
        }
    }

    public static BufferedImage read(InputStream in, String name) throws IOException {
        BufferedImage image = ImageIO.read(in);
        if (image == null) {
            throw new IOException("Not a readable image: " + name);
        }
        return toArgb(image);
    }

    public static BufferedImage toArgb(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_ARGB) {
            return image;
        }
        BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                copy.setRGB(x, y, image.getRGB(x, y));
            }
        }
        return copy;
    }

    /** Copy of a region (never shares the raster with the source). */
    public static BufferedImage crop(BufferedImage image, int x0, int y0, int width, int height) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                out.setRGB(x, y, image.getRGB(x0 + x, y0 + y));
            }
        }
        return out;
    }

    /** Adds transparent rows below the image until it is {@code height} pixels tall. */
    public static BufferedImage padBottom(BufferedImage image, int height) {
        if (height <= image.getHeight()) {
            return image;
        }
        BufferedImage out = new BufferedImage(image.getWidth(), height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                out.setRGB(x, y, image.getRGB(x, y));
            }
        }
        return out;
    }

    /** Enlarges the image by a whole factor without smoothing (every pixel becomes a factor x factor block). */
    public static BufferedImage scaleNearest(BufferedImage image, int factor) {
        if (factor == 1) {
            return image;
        }
        BufferedImage out = new BufferedImage(image.getWidth() * factor, image.getHeight() * factor,
                BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < out.getHeight(); y++) {
            for (int x = 0; x < out.getWidth(); x++) {
                out.setRGB(x, y, image.getRGB(x / factor, y / factor));
            }
        }
        return out;
    }

    /**
     * 9-slice: stretches the image to {@code width} x {@code height}. The {@code slice} pixels at every
     * edge keep their size (corners are copied, edges repeat along one axis), the middle is tiled.
     */
    public static BufferedImage nineSlice(BufferedImage image, int slice, int width, int height) {
        int middleWidth = image.getWidth() - 2 * slice;
        int middleHeight = image.getHeight() - 2 * slice;
        if (middleWidth <= 0 || middleHeight <= 0) {
            throw new IllegalArgumentException("box-slice " + slice + " is too large for a "
                    + image.getWidth() + "x" + image.getHeight() + " texture");
        }
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            int sy = sliceSource(y, height, image.getHeight(), slice, middleHeight);
            for (int x = 0; x < width; x++) {
                out.setRGB(x, y, image.getRGB(sliceSource(x, width, image.getWidth(), slice, middleWidth), sy));
            }
        }
        return out;
    }

    private static int sliceSource(int position, int size, int sourceSize, int slice, int middle) {
        if (position < slice) {
            return position;
        }
        if (position >= size - slice) {
            return sourceSize - (size - position);
        }
        return slice + (position - slice) % middle;
    }

    /** Repeats the columns of {@code image} until the result is {@code width} pixels wide. */
    public static BufferedImage tileHorizontally(BufferedImage image, int width) {
        BufferedImage out = new BufferedImage(width, image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < width; x++) {
                out.setRGB(x, y, image.getRGB(x % image.getWidth(), y));
            }
        }
        return out;
    }

    /**
     * Vertical 3-slice: keeps {@code top} rows and {@code bottom} rows and repeats the rows in
     * between so the middle part becomes {@code middleHeight} pixels tall.
     */
    public static BufferedImage stretchVertically(BufferedImage image, int top, int bottom, int middleHeight) {
        int sourceMiddle = image.getHeight() - top - bottom;
        if (sourceMiddle <= 0) {
            throw new IllegalArgumentException("slice-top + slice-bottom must be smaller than the texture height");
        }
        int height = top + middleHeight + bottom;
        BufferedImage out = new BufferedImage(image.getWidth(), height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            int sourceY;
            if (y < top) {
                sourceY = y;
            } else if (y < top + middleHeight) {
                sourceY = top + (y - top) % sourceMiddle;
            } else {
                sourceY = image.getHeight() - (height - y);
            }
            for (int x = 0; x < image.getWidth(); x++) {
                out.setRGB(x, y, image.getRGB(x, sourceY));
            }
        }
        return out;
    }


    /** Index of the rightmost column with a non-transparent pixel, plus one (0 if empty). */
    public static int opaqueWidth(BufferedImage image, int x0, int y0, int width, int height) {
        for (int x = width - 1; x >= 0; x--) {
            for (int y = 0; y < height; y++) {
                if ((image.getRGB(x0 + x, y0 + y) >>> 24) != 0) {
                    return x + 1;
                }
            }
        }
        return 0;
    }

    /** The advance the client assigns to a bitmap glyph (BitmapProvider). */
    public static int clientAdvance(int opaqueWidth, int height, int cellHeight) {
        float scale = (float) height / (float) cellHeight;
        return (int) (0.5 + (double) ((float) opaqueWidth * scale)) + 1;
    }

    public static byte[] png(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
