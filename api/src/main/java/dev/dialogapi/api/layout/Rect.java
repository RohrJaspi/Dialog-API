package dev.dialogapi.api.layout;

import java.util.ArrayList;
import java.util.List;

/**
 * A rectangle in GUI pixels: {@code x} relative to the centre of the body (or the left edge of a book
 * page), {@code y} below the top of the first line. Layouts are made by splitting one rectangle into
 * rows and columns instead of adding up coordinates by hand:
 *
 * <pre>{@code
 * Rect card = Rect.centered(328, 168);
 * List<Rect> rows = card.rows(8, 112, 48);              // a 112 px row, 8 px gap, a 48 px row
 * List<Rect> top = rows.get(0).columns(8, 112, Rect.FILL);   // a 112 px square and the rest
 * Rect icon = top.get(0);
 * Rect text = top.get(1).inset(8);
 * }</pre>
 *
 * The same rectangles feed {@link dev.dialogapi.api.glyph.PackAssets} (sizes and tops) and
 * {@link dev.dialogapi.api.glyph.GlyphCanvas} (drawing), so a layout change happens in one place.
 */
public record Rect(int x, int y, int width, int height) {

    /** Marks the one size in {@link #rows} / {@link #columns} that takes the remaining space. */
    public static final int FILL = -1;

    public Rect {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("negative size: " + width + "x" + height);
        }
    }

    public static Rect of(int x, int y, int width, int height) {
        return new Rect(x, y, width, height);
    }

    /** Horizontally centred on the body (x = -width / 2), top edge at y = 0. */
    public static Rect centered(int width, int height) {
        return new Rect(-(width / 2), 0, width, height);
    }

    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }

    public Rect translate(int dx, int dy) {
        return new Rect(x + dx, y + dy, width, height);
    }

    public Rect inset(int all) {
        return inset(all, all);
    }

    /** Shrinks the rectangle by {@code horizontal} px on the left and right and {@code vertical} px on the top and bottom. */
    public Rect inset(int horizontal, int vertical) {
        if (2 * horizontal > width || 2 * vertical > height) {
            throw new IllegalArgumentException("cannot inset " + horizontal + "," + vertical + " from " + this);
        }
        return new Rect(x + horizontal, y + vertical, width - 2 * horizontal, height - 2 * vertical);
    }

    /** An element of {@code width} x {@code height} placed inside this rectangle. */
    public Rect place(int width, int height, HAlign horizontal, VAlign vertical) {
        Rect size = new Rect(0, 0, width, height);
        if (width > this.width || height > this.height) {
            throw new IllegalArgumentException(size.width + "x" + size.height + " does not fit into " + this);
        }
        return new Rect(horizontal.x(this, width), vertical.top(this, height), width, height);
    }

    /**
     * Splits left to right. {@code widths} are fixed pixel widths, at most one of them may be {@link #FILL}
     * (it takes what the others and the gaps leave). Without {@code FILL} the rest stays empty on the right.
     */
    public List<Rect> columns(int gap, int... widths) {
        int[] sizes = resolve(width, gap, widths, "columns");
        List<Rect> result = new ArrayList<>(sizes.length);
        int cursor = x;
        for (int size : sizes) {
            result.add(new Rect(cursor, y, size, height));
            cursor += size + gap;
        }
        return result;
    }

    /** Splits top to bottom, like {@link #columns} with heights. */
    public List<Rect> rows(int gap, int... heights) {
        int[] sizes = resolve(height, gap, heights, "rows");
        List<Rect> result = new ArrayList<>(sizes.length);
        int cursor = y;
        for (int size : sizes) {
            result.add(new Rect(x, cursor, width, size));
            cursor += size + gap;
        }
        return result;
    }

    private static int[] resolve(int total, int gap, int[] sizes, String what) {
        if (sizes.length == 0) {
            throw new IllegalArgumentException("no " + what);
        }
        if (gap < 0) {
            throw new IllegalArgumentException("negative gap");
        }
        int used = gap * (sizes.length - 1);
        int fill = -1;
        for (int i = 0; i < sizes.length; i++) {
            if (sizes[i] == FILL) {
                if (fill >= 0) {
                    throw new IllegalArgumentException("only one of the " + what + " may be FILL");
                }
                fill = i;
            } else if (sizes[i] < 0) {
                throw new IllegalArgumentException("negative size in " + what);
            } else {
                used += sizes[i];
            }
        }
        if (used > total) {
            throw new IllegalArgumentException(what + " need " + used + " px but only " + total + " are available");
        }
        int[] resolved = sizes.clone();
        if (fill >= 0) {
            resolved[fill] = total - used;
        }
        return resolved;
    }
}
