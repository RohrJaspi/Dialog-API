package dev.dialogapi.api.actionbar;

import java.util.List;

/**
 * One selectable answer of a {@link QuestionNode}.
 *
 * @param text    MiniMessage text shown in the answer box
 * @param actions what happens when the answer is confirmed
 */
public record Answer(String text, List<DialogAction> actions) {
    public Answer {
        actions = List.copyOf(actions);
    }
}
