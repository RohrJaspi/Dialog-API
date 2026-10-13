package dev.dialogapi.plugin.screen;

import dev.dialogapi.api.screen.Screen;
import dev.dialogapi.api.screen.ScreenBody;
import dev.dialogapi.api.screen.ScreenButton;
import dev.dialogapi.api.screen.ScreenClick;
import dev.dialogapi.api.screen.ScreenInput;
import dev.dialogapi.api.screen.Screens;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

/** {@link Screens} on top of Paper's dialog API: every {@link Screen} becomes a Paper dialog with server-side callbacks. */
public final class PaperScreens implements Screens {

    private static final ClickCallback.Options ONCE = ClickCallback.Options.builder().build();
    private static final ClickCallback.Options REUSABLE =
            ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build();

    private final Plugin plugin;

    public PaperScreens(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void show(Player player, Screen screen) {
        player.showDialog(toPaperDialog(screen));
    }

    @Override
    public void close(Player player) {
        player.closeDialog();
    }

    @Override
    public Dialog toPaperDialog(Screen screen) {
        DialogBase.Builder base = DialogBase.builder(screen.title())
                .canCloseWithEscape(screen.closeOnEscape())
                .afterAction(switch (screen.afterAction()) {
                    case CLOSE -> DialogBase.DialogAfterAction.CLOSE;
                    case NONE -> DialogBase.DialogAfterAction.NONE;
                    case WAIT_FOR_RESPONSE -> DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE;
                })
                .body(screen.bodies().stream().map(PaperScreens::body).toList())
                .inputs(screen.inputs().stream().map(PaperScreens::input).toList());
        if (screen.externalTitle() != null) {
            base.externalTitle(screen.externalTitle());
        }
        DialogBase built = base.build();

        List<ActionButton> buttons = new ArrayList<>();
        for (ScreenButton button : screen.buttons()) {
            buttons.add(button(button));
        }
        ActionButton exit = screen.exit() == null ? null : button(screen.exit());
        DialogType type = switch (screen.layout()) {
            case NOTICE -> DialogType.notice(buttons.getFirst());
            case CONFIRMATION -> DialogType.confirmation(buttons.get(0), buttons.get(1));
            case MULTI_ACTION -> {
                var multi = DialogType.multiAction(buttons).columns(screen.columns());
                if (exit != null) {
                    multi.exitAction(exit);
                }
                yield multi.build();
            }
        };
        return Dialog.create(factory -> factory.empty().base(built).type(type));
    }

    private ActionButton button(ScreenButton button) {
        ActionButton.Builder builder = ActionButton.builder(button.label()).width(button.width());
        if (button.tooltip() != null) {
            builder.tooltip(button.tooltip());
        }
        Consumer<ScreenClick> onClick = button.onClick();
        if (onClick != null) {
            builder.action(DialogAction.customClick((response, audience) -> dispatch(response, audience, onClick),
                    button.reusable() ? REUSABLE : ONCE));
        }
        return builder.build();
    }

    private void dispatch(DialogResponseView response, Audience audience, Consumer<ScreenClick> onClick) {
        if (!(audience instanceof Player player)) {
            return;
        }
        ScreenClick click = new Click(player, response);
        if (Bukkit.isPrimaryThread()) {
            onClick.accept(click);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> onClick.accept(click));
        }
    }

    private static DialogBody body(ScreenBody body) {
        return switch (body) {
            case ScreenBody.Text text -> DialogBody.plainMessage(text.content(), text.width());
        };
    }

    private static DialogInput input(ScreenInput input) {
        return switch (input) {
            case ScreenInput.Text text -> DialogInput.text(text.key(), text.label())
                    .width(text.width()).initial(text.initial()).maxLength(text.maxLength()).build();
            case ScreenInput.Bool bool -> DialogInput.bool(bool.key(), bool.label()).initial(bool.initial()).build();
            case ScreenInput.Slider slider -> DialogInput.numberRange(slider.key(), slider.label(), slider.min(), slider.max())
                    .width(slider.width()).initial(slider.initial()).step(slider.step()).build();
            case ScreenInput.Options options -> DialogInput.singleOption(options.key(), options.label(),
                            options.options().stream()
                                    .map(choice -> SingleOptionDialogInput.OptionEntry.create(choice.id(), choice.label(), choice.initial()))
                                    .toList())
                    .width(options.width()).build();
        };
    }

    private record Click(Player player, DialogResponseView response) implements ScreenClick {
        @Override
        public @Nullable String text(String key) {
            return response.getText(key);
        }

        @Override
        public @Nullable Boolean bool(String key) {
            return response.getBoolean(key);
        }

        @Override
        public @Nullable Float number(String key) {
            return response.getFloat(key);
        }
    }
}
