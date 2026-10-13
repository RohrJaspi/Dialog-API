package dev.dialogapi.api.actionbar;

import dev.dialogapi.api.actionbar.Answer;
import dev.dialogapi.api.actionbar.Dialog;
import dev.dialogapi.api.actionbar.QuestionNode;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/** Called when a player confirms an answer, before its actions run. */
public final class DialogAnswerEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Dialog dialog;
    private final QuestionNode question;
    private final int answerIndex;
    private final boolean timedOut;

    public DialogAnswerEvent(Player player, Dialog dialog, QuestionNode question, int answerIndex, boolean timedOut) {
        super(player);
        this.dialog = dialog;
        this.question = question;
        this.answerIndex = answerIndex;
        this.timedOut = timedOut;
    }

    public Dialog getDialog() {
        return dialog;
    }

    public QuestionNode getQuestion() {
        return question;
    }

    /** Zero-based index of the chosen answer. */
    public int getAnswerIndex() {
        return answerIndex;
    }

    public Answer getAnswer() {
        return question.answers().get(answerIndex);
    }

    /** True if the answer was chosen automatically because the question timed out. */
    public boolean isTimedOut() {
        return timedOut;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
