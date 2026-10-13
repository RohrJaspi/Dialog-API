package dev.dialogapi.api.screen;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.jspecify.annotations.Nullable;

/**
 * A dialog screen (the vanilla dialog window): title, body, inputs and buttons. Build one and show it
 * with {@link Screens#show}; {@link Screens#toPaperDialog} gives the Paper dialog if you need more.
 */
public final class Screen {

    public enum Layout { MULTI_ACTION, NOTICE, CONFIRMATION }

    public enum AfterAction { CLOSE, NONE, WAIT_FOR_RESPONSE }

    private final Component title;
    private final @Nullable Component externalTitle;
    private final List<ScreenBody> bodies;
    private final List<ScreenInput> inputs;
    private final Layout layout;
    private final int columns;
    private final List<ScreenButton> buttons;
    private final @Nullable ScreenButton exit;
    private final boolean closeOnEscape;
    private final AfterAction afterAction;

    private Screen(Builder b) {
        this.title = b.title;
        this.externalTitle = b.externalTitle;
        this.bodies = List.copyOf(b.bodies);
        this.inputs = List.copyOf(b.inputs);
        this.layout = b.layout;
        this.columns = b.columns;
        this.buttons = List.copyOf(b.buttons);
        this.exit = b.exit;
        this.closeOnEscape = b.closeOnEscape;
        this.afterAction = b.afterAction;
        if (buttons.isEmpty()) {
            throw new IllegalStateException("A screen needs at least one button");
        }
        if (layout == Layout.CONFIRMATION && buttons.size() != 2) {
            throw new IllegalStateException("A confirmation screen needs exactly two buttons (yes, no)");
        }
        if (layout == Layout.NOTICE && buttons.size() != 1) {
            throw new IllegalStateException("A notice screen needs exactly one button");
        }
    }

    public static Builder builder(Component title) {
        return new Builder(title);
    }

    public Component title() { return title; }
    public @Nullable Component externalTitle() { return externalTitle; }
    public List<ScreenBody> bodies() { return bodies; }
    public List<ScreenInput> inputs() { return inputs; }
    public Layout layout() { return layout; }
    public int columns() { return columns; }
    /** Buttons; for a confirmation screen: yes, no. */
    public List<ScreenButton> buttons() { return buttons; }
    /** The button in the footer of a multi-action screen, or {@code null}. */
    public @Nullable ScreenButton exit() { return exit; }
    public boolean closeOnEscape() { return closeOnEscape; }
    public AfterAction afterAction() { return afterAction; }

    public static final class Builder {
        private final Component title;
        private @Nullable Component externalTitle;
        private final List<ScreenBody> bodies = new ArrayList<>();
        private final List<ScreenInput> inputs = new ArrayList<>();
        private Layout layout = Layout.MULTI_ACTION;
        private int columns = 1;
        private final List<ScreenButton> buttons = new ArrayList<>();
        private @Nullable ScreenButton exit;
        private boolean closeOnEscape = true;
        private AfterAction afterAction = AfterAction.CLOSE;

        private Builder(Component title) {
            this.title = title;
        }

        /** Title shown on the button that opens this screen from the pause menu. */
        public Builder externalTitle(Component externalTitle) {
            this.externalTitle = externalTitle;
            return this;
        }

        public Builder body(ScreenBody body) {
            bodies.add(body);
            return this;
        }

        public Builder input(ScreenInput input) {
            inputs.add(input);
            return this;
        }

        /** Buttons in a grid with {@code columns} columns. */
        public Builder multiAction(int columns, ScreenButton... buttons) {
            this.layout = Layout.MULTI_ACTION;
            this.columns = columns;
            this.buttons.clear();
            this.buttons.addAll(List.of(buttons));
            return this;
        }

        public Builder notice(ScreenButton button) {
            this.layout = Layout.NOTICE;
            this.buttons.clear();
            this.buttons.add(button);
            return this;
        }

        public Builder confirmation(ScreenButton yes, ScreenButton no) {
            this.layout = Layout.CONFIRMATION;
            this.buttons.clear();
            this.buttons.add(yes);
            this.buttons.add(no);
            return this;
        }

        /** Footer button of a multi-action screen. */
        public Builder exit(ScreenButton exit) {
            this.exit = exit;
            return this;
        }

        public Builder closeOnEscape(boolean closeOnEscape) {
            this.closeOnEscape = closeOnEscape;
            return this;
        }

        public Builder afterAction(AfterAction afterAction) {
            this.afterAction = afterAction;
            return this;
        }

        public Screen build() {
            return new Screen(this);
        }
    }
}
