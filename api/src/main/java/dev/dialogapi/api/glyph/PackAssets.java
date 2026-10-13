package dev.dialogapi.api.glyph;

import dev.dialogapi.api.layout.HAlign;
import dev.dialogapi.api.layout.Rect;
import dev.dialogapi.api.layout.VAlign;
import java.util.function.IntUnaryOperator;

/**
 * The assets one plugin adds to the shared DialogAPI resource pack. Get it from
 * {@link dev.dialogapi.api.DialogApi#assets(org.bukkit.plugin.Plugin)} in {@code onEnable}.
 *
 * <p>Registering marks the pack dirty; DialogAPI rebuilds it (debounced) and resends it to online
 * players when its hash changed. Character codes are stable across rebuilds. Every returned handle
 * is resolved once the next build has been applied.
 *
 * <p>A server owner can replace any registered image by dropping
 * {@code plugins/DialogAPI/textures/<namespace>/<id>.png} next to the config; the replacement is
 * applied before nine-slicing and scaling.
 *
 * <p>All {@code top} values are GUI pixels below the top of the text line; negative values lie above
 * it. In the action bar the reference is the action bar line, in a dialog body or a book the body
 * line.
 */
public interface PackAssets {

    /** Namespace in the pack: the plugin name, lower-cased and reduced to {@code [a-z0-9_]}. */
    String namespace();

    /** A texture drawn 1:1 with its top edge at {@code top}. Ids may only contain {@code [a-z0-9_]}. */
    default GlyphRef image(String id, ImageSource source, int top) {
        return image(id, source, 1, height -> top);
    }

    /**
     * @param scale nearest-neighbour upscaling of the texture (the glyph itself stays 1:1)
     * @param top   top edge as a function of the final image height, e.g. to centre it
     */
    GlyphRef image(String id, ImageSource source, int scale, IntUnaryOperator top);

    /**
     * A box stretched to {@code width} x {@code height}: corners of {@code slice} pixels are kept, the
     * edges and the middle are tiled (not scaled).
     */
    GlyphRef nineSlice(String id, ImageSource source, int slice, int width, int height, int top);

    /** A text font; fonts are deduplicated and shared between plugins. */
    TextFontRef text(int top, int scale);

    // ---------------------------------------------------------------------------- layout helpers

    /** Height of the letters of the vanilla font in GUI pixels (at scale 1). */
    int TEXT_HEIGHT = 8;

    /** A box that fills {@code rect}: the size and the top edge come from the rectangle. */
    default Placed box(String id, ImageSource source, int slice, Rect rect) {
        return new Placed(nineSlice(id, source, slice, rect.width(), rect.height(), rect.y()), rect, HAlign.LEFT);
    }

    /** An image (optionally upscaled) aligned inside {@code area}; the x is resolved when it is drawn. */
    default Placed image(String id, ImageSource source, int scale, Rect area, HAlign horizontal, VAlign vertical) {
        GlyphRef ref = image(id, source, scale, height -> vertical.top(area, height));
        return new Placed(ref, area, horizontal);
    }

    /**
     * A line of text in {@code rect}: the letters are {@code scale} times larger and sit at the top, middle
     * or bottom of the rectangle; the text is fitted and aligned to its width when it is drawn.
     */
    default TextSlot text(Rect rect, int scale, HAlign horizontal, VAlign vertical) {
        return new TextSlot(text(vertical.top(rect, TEXT_HEIGHT * scale), scale), rect, horizontal);
    }

    /** A raw file below {@code assets/<namespace>/}, e.g. sounds or models. */
    void file(String path, byte[] bytes);

    /** Removes everything this object registered; the handles stay valid for the old pack. */
    void clear();
}
