package dev.dialogapi.plugin.session;

import dev.dialogapi.plugin.pack.PackBuilder;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.dialogapi.api.actionbar.Portrait;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.object.ObjectContents;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * Turns a {@link Portrait} into player-head object contents. Skins of named portraits are looked
 * up once in the background and cached, so the client receives complete profiles (with textures)
 * and does not have to resolve names itself.
 */
public final class PortraitResolver {

    private final Map<String, List<ProfileProperty>> textures = new ConcurrentHashMap<>();
    private final Set<String> pending = ConcurrentHashMap.newKeySet();

    public @Nullable PlayerHeadObjectContents resolve(@Nullable Portrait portrait, Player viewer) {
        if (portrait == null) {
            return null;
        }
        PlayerHeadObjectContents.Builder head = ObjectContents.playerHead().hat(portrait.hat());
        if (portrait.showViewer()) {
            head.id(viewer.getUniqueId()).name(viewer.getName());
            addProperties(head, viewer.getPlayerProfile().getProperties());
        } else if (portrait.skinFile() != null) {
            head.texture(Key.key(PackBuilder.NAMESPACE, "portrait/" + portrait.skinFile()));
        } else if (portrait.textureValue() != null) {
            head.profileProperty(portrait.textureSignature() == null
                    ? PlayerHeadObjectContents.property("textures", portrait.textureValue())
                    : PlayerHeadObjectContents.property("textures", portrait.textureValue(), portrait.textureSignature()));
        } else if (portrait.uuid() != null) {
            head.id(portrait.uuid());
            Player online = Bukkit.getPlayer(portrait.uuid());
            if (online != null) {
                addProperties(head, online.getPlayerProfile().getProperties());
            }
        } else if (portrait.name() != null) {
            head.name(portrait.name());
            Player online = Bukkit.getPlayerExact(portrait.name());
            if (online != null) {
                head.id(online.getUniqueId());
                addProperties(head, online.getPlayerProfile().getProperties());
            } else {
                List<ProfileProperty> cached = textures.get(portrait.name().toLowerCase(Locale.ROOT));
                if (cached != null) {
                    addProperties(head, cached);
                } else {
                    prefetch(portrait);
                }
            }
        } else {
            return null;
        }
        return head.build();
    }

    /** Looks up the skin of a named portrait in the background (no-op for other portrait kinds). */
    public void prefetch(@Nullable Portrait portrait) {
        if (portrait == null || portrait.name() == null || portrait.showViewer()) {
            return;
        }
        String key = portrait.name().toLowerCase(Locale.ROOT);
        if (textures.containsKey(key) || !pending.add(key)) {
            return;
        }
        PlayerProfile profile;
        try {
            profile = Bukkit.createProfile(portrait.name());
        } catch (IllegalArgumentException e) {
            pending.remove(key);
            return;
        }
        profile.update().whenComplete((updated, error) -> {
            pending.remove(key);
            if (error == null && updated != null && updated.hasTextures()) {
                textures.put(key, updated.getProperties().stream()
                        .filter(property -> property.getName().equals("textures"))
                        .toList());
            }
        });
    }

    private static void addProperties(PlayerHeadObjectContents.Builder head, Iterable<ProfileProperty> properties) {
        for (ProfileProperty property : properties) {
            if (!property.getName().equals("textures")) {
                continue;
            }
            head.profileProperty(property.getSignature() == null
                    ? PlayerHeadObjectContents.property(property.getName(), property.getValue())
                    : PlayerHeadObjectContents.property(property.getName(), property.getValue(), property.getSignature()));
        }
    }
}
