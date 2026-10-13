package dev.dialogapi.plugin.loader;

/** A dialog file could not be read; the message explains what is wrong. */
public final class DialogLoadException extends Exception {
    public DialogLoadException(String message) {
        super(message);
    }
}
