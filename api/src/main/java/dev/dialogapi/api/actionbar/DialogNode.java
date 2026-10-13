package dev.dialogapi.api.actionbar;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** A step of a dialog: either a {@link TextNode} or a {@link QuestionNode}. */
public sealed interface DialogNode permits TextNode, QuestionNode {

    String id();

    /** MiniMessage text. Every entry starts a new page; long entries are paginated automatically. */
    List<String> pages();

    /** Overrides the dialog's speaker for this node, or {@code null}. */
    @Nullable String speaker();

    /** Overrides the dialog's portrait for this node, or {@code null}. */
    @Nullable Portrait portrait();

    Pacing pacing();

    /** Side effects executed when the node is entered (commands, messages, callbacks). */
    List<DialogAction> onEnter();
}
