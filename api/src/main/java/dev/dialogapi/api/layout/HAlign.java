package dev.dialogapi.api.layout;

/** Horizontal alignment of something inside an area. */
public enum HAlign {
    LEFT, CENTER, RIGHT;

    /** The x position of an element that is {@code width} wide inside {@code area}. */
    public int x(Rect area, int width) {
        return switch (this) {
            case LEFT -> area.x();
            case CENTER -> area.x() + (area.width() - width) / 2;
            case RIGHT -> area.x() + area.width() - width;
        };
    }
}
