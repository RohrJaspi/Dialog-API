package dev.dialogapi.api.pack;

import java.util.concurrent.CompletableFuture;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/** The shared resource pack that contains the assets of DialogAPI and of every plugin that registered some. */
public interface ResourcePack {

    /** @return whether a build has been applied and no registration is waiting for the next one */
    boolean isReady();

    /**
     * Runs {@code task} on the main thread as soon as the pack contains everything registered so far:
     * right away if it is {@link #isReady() ready}, otherwise after the next applied build.
     */
    void whenReady(Runnable task);

    /** @return SHA-1 of the current pack zip, or {@code null} before the first build */
    @Nullable String sha1();

    /** @return where clients download the pack from, or {@code null} if it is not served */
    @Nullable String url();

    /** Sends the pack to the player; returns {@code false} if there is nothing to send. */
    boolean send(Player player);

    /** Rebuilds the pack now (otherwise a rebuild is scheduled by registrations and {@code /dialogapi reload}). */
    CompletableFuture<Void> rebuild();
}
