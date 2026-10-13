package dev.dialogapi.api.actionbar;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Shows the question text, then the answer box. The player scrolls to select and confirms.
 *
 * @param timeoutTicks  0 = no timeout; otherwise the default answer is chosen after this many ticks
 * @param defaultAnswer zero-based index of the answer used on timeout, or -1 to end the dialog
 */
public record QuestionNode(String id, List<String> pages, @Nullable String speaker, @Nullable Portrait portrait,
                           Pacing pacing, List<DialogAction> onEnter, List<Answer> answers,
                           int timeoutTicks, int defaultAnswer) implements DialogNode {
    public QuestionNode {
        pages = List.copyOf(pages);
        onEnter = List.copyOf(onEnter);
        answers = List.copyOf(answers);
    }
}
