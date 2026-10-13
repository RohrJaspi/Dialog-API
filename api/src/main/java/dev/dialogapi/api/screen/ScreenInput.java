package dev.dialogapi.api.screen;

import java.util.List;
import net.kyori.adventure.text.Component;

/** An input element of a {@link Screen}; read it in {@link ScreenClick} with its key. */
public sealed interface ScreenInput {

    String key();

    record Text(String key, Component label, int width, String initial, int maxLength) implements ScreenInput {}

    record Bool(String key, Component label, boolean initial) implements ScreenInput {}

    record Slider(String key, Component label, int width, float min, float max, float initial, Float step)
            implements ScreenInput {}

    /** @param options id and label of every choice; the id is what {@link ScreenClick#text} returns */
    record Options(String key, Component label, int width, List<Choice> options) implements ScreenInput {
        public Options {
            options = List.copyOf(options);
        }
    }

    record Choice(String id, Component label, boolean initial) {}

    static ScreenInput text(String key, Component label, int width) {
        return new Text(key, label, width, "", 32);
    }

    static ScreenInput bool(String key, Component label, boolean initial) {
        return new Bool(key, label, initial);
    }

    static ScreenInput slider(String key, Component label, int width, float min, float max, float initial) {
        return new Slider(key, label, width, min, max, initial, null);
    }

    static ScreenInput options(String key, Component label, int width, Choice... choices) {
        return new Options(key, label, width, List.of(choices));
    }
}
