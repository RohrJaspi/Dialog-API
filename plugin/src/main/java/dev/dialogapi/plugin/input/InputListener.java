package dev.dialogapi.plugin.input;

import com.destroystokyo.paper.event.player.PlayerUseUnknownEntityEvent;
import dev.dialogapi.plugin.DialogApiPlugin;
import dev.dialogapi.plugin.config.PluginSettings;
import dev.dialogapi.plugin.session.DialogSession;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Player input for dialogs.
 * <ul>
 *   <li>Mouse wheel / number keys: {@link PlayerItemHeldEvent}, cancelled while answers are shown
 *       so the real hotbar slot never changes.</li>
 *   <li>Confirm: right-click on the NPC (works with an empty hand), sneak, and optionally
 *       right-click on any entity, block or air.</li>
 *   <li>Right-clicking a bound NPC starts its dialog.</li>
 * </ul>
 * The client sends up to four packets for one entity click (interact-at + interact, both hands),
 * so clicks are handled once per tick and only for the main hand; the others are just cancelled.
 */
public final class InputListener implements Listener {

    private final DialogApiPlugin plugin;
    private final Map<UUID, Integer> lastClickTick = new HashMap<>();

    public InputListener(DialogApiPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onItemHeld(PlayerItemHeldEvent event) {
        DialogSession session = plugin.sessions().get(event.getPlayer());
        if (session == null || !session.isAnswering()) {
            return;
        }
        event.setCancelled(true);
        session.onHotbarScroll(event.getPreviousSlot(), event.getNewSlot());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        handleEntityClick(event, event.getPlayer(), event.getRightClicked(), event.getHand());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        handleEntityClick(event, event.getPlayer(), event.getRightClicked(), event.getHand());
    }

    /** Packet-based NPCs (no server-side entity). Only used to continue a running dialog. */
    @EventHandler
    public void onUnknownEntity(PlayerUseUnknownEntityEvent event) {
        if (event.isAttack()) {
            return;
        }
        DialogSession session = plugin.sessions().get(event.getPlayer());
        PluginSettings settings = plugin.settings();
        if (session == null) {
            return;
        }
        boolean confirm = settings.confirmOnAnyEntityClick() || (!session.hasNpc() && settings.confirmOnNpcClick());
        if (confirm && event.getHand() == EquipmentSlot.HAND && firstClickThisTick(event.getPlayer())) {
            session.confirm();
        }
    }

    private void handleEntityClick(Cancellable event, Player player, Entity entity, EquipmentSlot hand) {
        PluginSettings settings = plugin.settings();
        DialogSession session = plugin.sessions().get(player);
        if (session != null) {
            boolean confirm = settings.confirmOnAnyEntityClick()
                    || (settings.confirmOnNpcClick() && (session.isNpc(entity) || !session.hasNpc()));
            if (confirm) {
                event.setCancelled(true);
                if (hand == EquipmentSlot.HAND && firstClickThisTick(player)) {
                    session.confirm();
                }
                return;
            }
        }
        String dialogId = plugin.bindings().resolve(entity);
        if (dialogId == null) {
            return;
        }
        event.setCancelled(true);
        if (hand != EquipmentSlot.HAND || !firstClickThisTick(player)) {
            return;
        }
        if (plugin.registry().get(dialogId) == null) {
            plugin.getLogger().warning(entity.getType() + " " + entity.getUniqueId() + " is bound to unknown dialog '"
                    + dialogId + "'");
            return;
        }
        plugin.sessions().start(player, plugin.registry().get(dialogId), entity, null);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking() || !plugin.settings().confirmOnSneak()) {
            return;
        }
        DialogSession session = plugin.sessions().get(event.getPlayer());
        if (session != null) {
            session.confirm();
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (!plugin.settings().confirmOnBlockOrAirClick()
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        DialogSession session = plugin.sessions().get(event.getPlayer());
        if (session == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() == EquipmentSlot.HAND && firstClickThisTick(event.getPlayer())) {
            session.confirm();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastClickTick.remove(event.getPlayer().getUniqueId());
    }

    private boolean firstClickThisTick(Player player) {
        int tick = Bukkit.getCurrentTick();
        Integer previous = lastClickTick.put(player.getUniqueId(), tick);
        return previous == null || previous != tick;
    }
}
