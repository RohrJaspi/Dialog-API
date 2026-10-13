package dev.dialogapi.api.screen;

import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.jspecify.annotations.Nullable;

/**
 * A button of a {@link Screen}.
 *
 * @param onClick   called on the main thread when the button is clicked; {@code null} = only closes the screen
 * @param reusable  whether the callback may be used more than once (needed for screens that stay open)
 */
public record ScreenButton(Component label, int width, @Nullable Component tooltip,
                           @Nullable Consumer<ScreenClick> onClick, boolean reusable) {

    public ScreenButton {
        if (width < 1 || width > 1024) {
            throw new IllegalArgumentException("button width must be 1-1024");
        }
    }

    public static ScreenButton of(Component label, int width, Consumer<ScreenClick> onClick) {
        return new ScreenButton(label, width, null, onClick, false);
    }

    /** A button that only closes the screen. */
    public static ScreenButton close(Component label, int width) {
        return new ScreenButton(label, width, null, null, false);
    }

    public ScreenButton tooltip(@Nullable Component tooltip) {
        return new ScreenButton(label, width, tooltip, onClick, reusable);
    }

    public ScreenButton asReusable() {
        return new ScreenButton(label, width, tooltip, onClick, true);
    }
}
