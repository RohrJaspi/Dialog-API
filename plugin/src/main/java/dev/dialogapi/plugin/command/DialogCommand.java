package dev.dialogapi.plugin.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import dev.dialogapi.plugin.DialogApiPlugin;
import dev.dialogapi.api.actionbar.Dialog;
import dev.dialogapi.plugin.session.DialogSession;
import dev.dialogapi.api.actionbar.EndReason;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import java.net.URI;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * {@code /dialogapi start <dialogId> [player] | stop [player] | reload | list | bind <dialogId> | unbind | pack [send [player]]}
 * (permission {@code dialogapi.admin}).
 */
public final class DialogCommand {

    public static final String PERMISSION = "dialogapi.admin";

    private final DialogApiPlugin plugin;

    public DialogCommand(DialogApiPlugin plugin) {
        this.plugin = plugin;
    }

    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("dialogapi")
                .requires(source -> source.getSender().hasPermission(PERMISSION))
                .then(Commands.literal("start")
                        .then(Commands.argument("dialog", StringArgumentType.word())
                                .suggests(this::suggestDialogs)
                                .executes(context -> start(context, self(context)))
                                .then(Commands.argument("player", ArgumentTypes.player())
                                        .executes(context -> start(context, target(context))))))
                .then(Commands.literal("stop")
                        .executes(context -> stop(context, self(context)))
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .executes(context -> stop(context, target(context)))))
                .then(Commands.literal("reload").executes(this::reload))
                .then(Commands.literal("list").executes(this::list))
                .then(Commands.literal("bind")
                        .then(Commands.argument("dialog", StringArgumentType.word())
                                .suggests(this::suggestDialogs)
                                .executes(this::bind)))
                .then(Commands.literal("unbind").executes(this::unbind))
                .then(Commands.literal("pack")
                        .executes(this::packInfo)
                        .then(Commands.literal("send")
                                .executes(context -> sendPack(context, self(context)))
                                .then(Commands.argument("player", ArgumentTypes.player())
                                        .executes(context -> sendPack(context, target(context))))))
                .build();
    }

    private int start(CommandContext<CommandSourceStack> context, @Nullable Player player) {
        CommandSender sender = context.getSource().getSender();
        if (player == null) {
            return error(sender, "Specify a player: /dialogapi start <dialogId> <player>");
        }
        String id = StringArgumentType.getString(context, "dialog");
        Dialog dialog = plugin.registry().get(id);
        if (dialog == null) {
            return error(sender, "Unknown dialog '" + id + "'. Use /dialogapi list.");
        }
        DialogSession session = plugin.sessions().start(player, dialog, null, null);
        if (session == null) {
            return error(sender, "The dialog could not be started (resource pack still building or start cancelled).");
        }
        if (sender != player) {
            info(sender, "Started '" + id + "' for " + player.getName() + ".");
        }
        return Command.SINGLE_SUCCESS;
    }

    private int stop(CommandContext<CommandSourceStack> context, @Nullable Player player) {
        CommandSender sender = context.getSource().getSender();
        if (player == null) {
            return error(sender, "Specify a player: /dialogapi stop <player>");
        }
        if (!plugin.sessions().stop(player, EndReason.STOPPED)) {
            return error(sender, player.getName() + " is not in a dialog.");
        }
        info(sender, "Stopped the dialog of " + player.getName() + ".");
        return Command.SINGLE_SUCCESS;
    }

    private int reload(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        info(sender, "Reloading config, layout, dialogs and rebuilding the resource pack...");
        plugin.reload().whenComplete((result, error) -> {
            if (error != null) {
                error(sender, "Resource pack build failed: " + rootMessage(error) + " (see console)");
            } else {
                info(sender, "Reloaded " + plugin.registry().ids().size() + " dialogs. Pack SHA-1: " + result.sha1());
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    private int list(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        info(sender, plugin.registry().ids().size() + " dialogs, " + plugin.sessions().count() + " running:");
        for (String id : plugin.registry().ids()) {
            sender.sendMessage(Component.text(" - ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(id, NamedTextColor.WHITE)
                            .clickEvent(ClickEvent.suggestCommand("/dialogapi start " + id + " ")))
                    .append(Component.text(plugin.registry().isFromApi(id) ? " (API)" : "", NamedTextColor.GRAY)));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int bind(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            return error(sender, "Only players can bind (look at the NPC).");
        }
        String id = StringArgumentType.getString(context, "dialog");
        Entity target = player.getTargetEntity(8);
        if (target == null || target instanceof Player) {
            return error(sender, "Look at the NPC entity (within 8 blocks).");
        }
        plugin.bindings().bind(target, id);
        info(sender, "Bound " + target.getType().name().toLowerCase() + " " + target.getUniqueId() + " to '" + id + "'."
                + (plugin.registry().get(id) == null ? " (warning: no dialog with this id is loaded)" : ""));
        return Command.SINGLE_SUCCESS;
    }

    private int unbind(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!(sender instanceof Player player)) {
            return error(sender, "Only players can unbind (look at the NPC).");
        }
        Entity target = player.getTargetEntity(8);
        if (target == null) {
            return error(sender, "Look at the NPC entity (within 8 blocks).");
        }
        if (!plugin.bindings().unbind(target)) {
            return error(sender, "This entity has no /dialogapi bind binding (tag bindings are removed with /tag).");
        }
        info(sender, "Removed the dialog binding.");
        return Command.SINGLE_SUCCESS;
    }

    private int packInfo(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        String sha1 = plugin.packService().sha1();
        if (sha1 == null) {
            return error(sender, "The resource pack is still being built.");
        }
        URI uri = plugin.packService().uri();
        info(sender, "Pack: plugins/DialogAPI/DialogAPI-pack.zip");
        sender.sendMessage(Component.text("SHA-1: ", NamedTextColor.GRAY)
                .append(Component.text(sha1, NamedTextColor.WHITE).clickEvent(ClickEvent.copyToClipboard(sha1))));
        sender.sendMessage(Component.text("URL: ", NamedTextColor.GRAY)
                .append(Component.text(uri == null ? "none (set pack.url or enable pack.self-host)" : uri.toString(),
                        NamedTextColor.WHITE)));
        return Command.SINGLE_SUCCESS;
    }

    private int sendPack(CommandContext<CommandSourceStack> context, @Nullable Player player) {
        CommandSender sender = context.getSource().getSender();
        if (player == null) {
            return error(sender, "Specify a player: /dialogapi pack send <player>");
        }
        if (!plugin.packService().send(player)) {
            return error(sender, "No pack URL configured (set pack.url or enable pack.self-host in config.yml).");
        }
        info(sender, "Sent the resource pack to " + player.getName() + ".");
        return Command.SINGLE_SUCCESS;
    }

    // ------------------------------------------------------------------------------------------

    private CompletableFuture<Suggestions> suggestDialogs(CommandContext<CommandSourceStack> context,
                                                          SuggestionsBuilder builder) {
        for (String id : plugin.registry().ids()) {
            if (id.startsWith(builder.getRemainingLowerCase())) {
                builder.suggest(id);
            }
        }
        return builder.buildFuture();
    }

    private static @Nullable Player self(CommandContext<CommandSourceStack> context) {
        if (context.getSource().getExecutor() instanceof Player player) {
            return player;
        }
        return context.getSource().getSender() instanceof Player player ? player : null;
    }

    private static Player target(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        return context.getArgument("player", PlayerSelectorArgumentResolver.class)
                .resolve(context.getSource()).getFirst();
    }

    private static void info(CommandSender sender, String message) {
        sender.sendMessage(Component.text("[Dialogs] ", NamedTextColor.GOLD).append(Component.text(message, NamedTextColor.YELLOW)));
    }

    private static int error(CommandSender sender, String message) {
        sender.sendMessage(Component.text("[Dialogs] ", NamedTextColor.GOLD).append(Component.text(message, NamedTextColor.RED)));
        return 0;
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }
}
