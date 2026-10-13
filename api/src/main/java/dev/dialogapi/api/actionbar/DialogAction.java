package dev.dialogapi.api.actionbar;

import java.util.Objects;
import java.util.function.Consumer;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Something that happens when an answer is chosen or a node is entered/left.
 *
 * <p>Side effects ({@link ConsoleCommand}, {@link PlayerCommand}, {@link Message}, {@link Callback})
 * run in order. The <em>flow</em> actions ({@link GoTo}, {@link StartDialog}, {@link End}) decide
 * where the dialog continues; if there is none, the dialog continues with the next node in
 * declaration order (or ends after the last node).
 */
public sealed interface DialogAction {

    /** Jump to another node of the same dialog. */
    record GoTo(String node) implements DialogAction {
        public GoTo { Objects.requireNonNull(node, "node"); }
    }

    /** Continue with another dialog (optionally at a specific node). */
    record StartDialog(String dialog, @Nullable String node) implements DialogAction {
        public StartDialog { Objects.requireNonNull(dialog, "dialog"); }
    }

    /** End the dialog. */
    record End() implements DialogAction {}

    /** Run a command as the console. {@code %player%} and {@code %uuid%} are replaced. */
    record ConsoleCommand(String command) implements DialogAction {}

    /** Run a command as the player. {@code %player%} and {@code %uuid%} are replaced. */
    record PlayerCommand(String command) implements DialogAction {}

    /** Send a MiniMessage chat message to the player. */
    record Message(String miniMessage) implements DialogAction {}

    /** Run Java code (builder API only). */
    record Callback(Consumer<Player> callback) implements DialogAction {}

    default boolean isFlow() {
        return this instanceof GoTo || this instanceof StartDialog || this instanceof End;
    }

    static DialogAction goTo(String node) {
        return new GoTo(node);
    }

    static DialogAction startDialog(String dialog) {
        return new StartDialog(dialog, null);
    }

    static DialogAction startDialog(String dialog, String node) {
        return new StartDialog(dialog, node);
    }

    static DialogAction end() {
        return new End();
    }

    static DialogAction consoleCommand(String command) {
        return new ConsoleCommand(command);
    }

    static DialogAction playerCommand(String command) {
        return new PlayerCommand(command);
    }

    static DialogAction message(String miniMessage) {
        return new Message(miniMessage);
    }

    static DialogAction run(Consumer<Player> callback) {
        return new Callback(callback);
    }

    /**
     * Converts a jump target string into a flow action: {@link Dialog#END} ends the dialog,
     * {@code @dialog:<id>} or {@code @dialog:<id>#<node>} switches dialogs, anything else is a node id.
     */
    static DialogAction target(String target) {
        if (target.equals(Dialog.END)) {
            return end();
        }
        if (target.startsWith("@dialog:")) {
            String rest = target.substring("@dialog:".length());
            int hash = rest.indexOf('#');
            return hash < 0 ? startDialog(rest) : startDialog(rest.substring(0, hash), rest.substring(hash + 1));
        }
        return goTo(target);
    }
}
