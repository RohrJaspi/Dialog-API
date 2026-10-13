package dev.dialogapi.api.actionbar;

import java.util.Set;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * RPG style NPC dialogs drawn in the action bar. All methods must be called on the main thread.
 *
 * <p>Build dialogs with {@link Dialog#builder(String)}, or put YAML files into
 * {@code plugins/DialogAPI/actionbar/dialogs/}.
 */
public interface ActionBarDialogs {

    /** Starts a registered dialog; replaces a running one. @throws IllegalArgumentException if the id is unknown */
    boolean start(Player player, String dialogId);

    boolean start(Player player, String dialogId, @Nullable Entity npc);

    boolean start(Player player, Dialog dialog);

    boolean start(Player player, Dialog dialog, @Nullable Entity npc, @Nullable String nodeId);

    boolean stop(Player player);

    boolean isInDialog(Player player);

    @Nullable ActiveDialog session(Player player);

    /** Makes the dialog startable by id; it survives {@code /dialogapi reload}. */
    void register(Dialog dialog);

    boolean unregister(String dialogId);

    @Nullable Dialog dialog(String dialogId);

    Set<String> dialogIds();

    /** Right-clicking the entity starts the dialog (stored in the entity's persistent data). */
    void bind(Entity entity, String dialogId);

    boolean unbind(Entity entity);

    /** Entities with this scoreboard tag start the dialog, until the next reload. */
    void bindTag(String scoreboardTag, String dialogId);
}
