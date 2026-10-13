package dev.dialogapi.api;

import org.jetbrains.annotations.ApiStatus;
import org.jspecify.annotations.Nullable;

/** Entry point: {@code DialogAPI.get().actionBar().start(player, "my_dialog")}. */
public final class DialogAPI {

    private static volatile @Nullable DialogServices instance;

    private DialogAPI() {
    }

    /**
     * @throws IllegalStateException if the DialogAPI plugin is not enabled yet (declare it as a
     *                               dependency of your plugin), or if your jar contains its own copy
     *                               of the API classes (do not shade {@code dialogapi-api})
     */
    public static DialogServices get() {
        DialogServices api = instance;
        if (api == null) {
            throw new IllegalStateException("DialogAPI is not enabled. Add it to the dependencies of your plugin"
                    + " and do not shade dialogapi-api into your jar (scope provided).");
        }
        return api;
    }

    public static boolean isAvailable() {
        return instance != null;
    }

    @ApiStatus.Internal
    public static void set(@Nullable DialogServices api) {
        instance = api;
    }
}
