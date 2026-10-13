package dev.dialogapi.api.layout;

/** Vertical alignment of something inside an area. */
public enum VAlign {
    TOP, MIDDLE, BOTTOM;

    /** The y of the top edge of an element that is {@code height} tall inside {@code area}. */
    public int top(Rect area, int height) {
        return switch (this) {
            case TOP -> area.y();
            case MIDDLE -> area.y() + (area.height() - height) / 2;
            case BOTTOM -> area.y() + area.height() - height;
        };
    }
}
