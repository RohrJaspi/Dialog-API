package dev.dialogapi.plugin.session;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

/**
 * Invisible {@link Interaction} box around the player's head, only shown to that player.
 * <p>
 * The client sends nothing when right-clicking air with an empty hand. With the eye inside this box,
 * the client's entity pick hits it at distance 0 for every click (beating any block), so every
 * right-click becomes an entity interaction, with or without an item. Projectiles pass through
 * interaction entities and they have no collision; left-clicks hit the box as well.
 * <p>
 * The box is large because the client only receives its position every few ticks.
 */
final class ClickHitbox {

    private static final float SIZE = 4f;
    private static final double BELOW_FEET = 1.0;

    private final Plugin plugin;
    private final Player player;
    private @Nullable Interaction entity;

    ClickHitbox(Plugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    /** Spawns the box if needed and moves it to the player. */
    void follow() {
        Location target = player.getLocation().subtract(0, BELOW_FEET, 0);
        if (entity == null || !entity.isValid()) {
            entity = player.getWorld().spawn(target, Interaction.class, box -> {
                box.setPersistent(false);
                box.setVisibleByDefault(false);
                box.setInteractionWidth(SIZE);
                box.setInteractionHeight(SIZE);
                box.setResponsive(false);
            });
            player.showEntity(plugin, entity);
            return;
        }
        Location current = entity.getLocation();
        if (current.getWorld() != target.getWorld() || current.distanceSquared(target) > 0.0025) {
            entity.teleport(target);
        }
    }

    void remove() {
        if (entity != null) {
            entity.remove();
            entity = null;
        }
    }

    boolean is(Entity other) {
        return entity != null && entity.getUniqueId().equals(other.getUniqueId());
    }
}
