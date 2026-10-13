package dev.dialogapi.api.screen;

import java.util.ArrayList;
import java.util.List;
import dev.dialogapi.api.glyph.GlyphCanvas;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;

/** A block of content above the buttons of a {@link Screen}. */
public sealed interface ScreenBody {

    /** Height of one text line in a dialog body. */
    int LINE_HEIGHT = 9;

    /** @param width wrap width in pixels (1-1024) */
    record Text(Component content, int width) implements ScreenBody {}

    static ScreenBody text(Component content, int width) {
        return new Text(content, width);
    }

    /**
     * A drawing made with {@link GlyphCanvas}: {@code line} is the first line of the body (a canvas
     * line has a total advance of 0, so it starts at the centre of the body). Empty lines are added
     * below so that the buttons start under the drawing, which is {@code height} pixels tall.
     */
    static ScreenBody canvas(Component line, int width, int height) {
        int lines = Math.max(2, (height + 2 + LINE_HEIGHT - 1) / LINE_HEIGHT);
        List<Component> all = new ArrayList<>(lines);
        all.add(line);
        for (int i = 2; i < lines; i++) {
            all.add(Component.empty());
        }
        all.add(GlyphCanvas.zeroWidth());
        return new Text(Component.join(JoinConfiguration.newlines(), all), width);
    }
}
