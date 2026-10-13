package dev.dialogapi.plugin.binding;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jspecify.annotations.Nullable;

/**
 * Which entity starts which dialog when right-clicked. Checked in this order:
 * <ol>
 *   <li>the entity's persistent data ({@code /dialogapi bind <id>} or {@code ActionBarDialogs.bind})</li>
 *   <li>{@code uuids:} in bindings.yml</li>
 *   <li>a scoreboard tag {@code <tag-prefix><dialogId>}, e.g. {@code dialog.stats_house}</li>
 *   <li>{@code tags:} in bindings.yml (scoreboard tag -> dialog id)</li>
 * </ol>
 */
public final class NpcBindings {

    private final NamespacedKey key;
    private final File file;
    private final Logger logger;
    private final Map<UUID, String> byUuid = new HashMap<>();
    private final Map<String, String> byTag = new HashMap<>();
    private String tagPrefix = "dialog.";

    public NpcBindings(Plugin plugin) {
        this.key = new NamespacedKey(plugin, "dialog");
        this.file = new File(plugin.getDataFolder(), "bindings.yml");
        this.logger = plugin.getLogger();
    }

    public void load(String tagPrefix) {
        this.tagPrefix = tagPrefix;
        byUuid.clear();
        byTag.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection uuids = yaml.getConfigurationSection("uuids");
        if (uuids != null) {
            for (String id : uuids.getKeys(false)) {
                try {
                    byUuid.put(UUID.fromString(id), uuids.getString(id));
                } catch (IllegalArgumentException e) {
                    logger.warning("bindings.yml: '" + id + "' is not a UUID");
                }
            }
        }
        ConfigurationSection tags = yaml.getConfigurationSection("tags");
        if (tags != null) {
            for (String tag : tags.getKeys(false)) {
                byTag.put(tag, tags.getString(tag));
            }
        }
    }

    /** The dialog bound to this entity, or {@code null}. */
    public @Nullable String resolve(Entity entity) {
        String bound = entity.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (bound != null) {
            return bound;
        }
        bound = byUuid.get(entity.getUniqueId());
        if (bound != null) {
            return bound;
        }
        for (String tag : entity.getScoreboardTags()) {
            if (!tagPrefix.isEmpty() && tag.startsWith(tagPrefix) && tag.length() > tagPrefix.length()) {
                return tag.substring(tagPrefix.length());
            }
            String mapped = byTag.get(tag);
            if (mapped != null) {
                return mapped;
            }
        }
        return null;
    }

    /** Stores the binding in the entity itself (survives restarts, moves with the entity). */
    public void bind(Entity entity, String dialogId) {
        entity.getPersistentDataContainer().set(key, PersistentDataType.STRING, dialogId);
    }

    /** Removes the persistent binding and a bindings.yml UUID entry. Returns true if one existed. */
    public boolean unbind(Entity entity) {
        boolean removed = entity.getPersistentDataContainer().has(key, PersistentDataType.STRING);
        entity.getPersistentDataContainer().remove(key);
        if (byUuid.remove(entity.getUniqueId()) != null) {
            removed = true;
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            yaml.set("uuids." + entity.getUniqueId(), null);
            try {
                yaml.save(file);
            } catch (IOException e) {
                logger.warning("Could not save bindings.yml: " + e.getMessage());
            }
        }
        return removed;
    }

    public void bindTag(String scoreboardTag, String dialogId) {
        byTag.put(scoreboardTag, dialogId);
    }

    public void bindUuid(UUID entity, String dialogId) {
        byUuid.put(entity, dialogId);
    }
}
