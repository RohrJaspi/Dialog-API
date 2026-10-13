package dev.dialogapi.api.actionbar;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Shows text with the typewriter effect, then shows the continue indicator and waits for a click
 * (or continues automatically).
 *
 * @param then actions executed when the player continues past the node; normally a single flow
 *             action. Empty means "continue with the next node in declaration order".
 */
public record TextNode(String id, List<String> pages, @Nullable String speaker, @Nullable Portrait portrait,
                       Pacing pacing, List<DialogAction> onEnter, List<DialogAction> then) implements DialogNode {
    public TextNode {
        pages = List.copyOf(pages);
        onEnter = List.copyOf(onEnter);
        then = List.copyOf(then);
    }
}
