package dev.dialogapi.plugin.config;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Positions of every HUD element, in GUI pixels (layout.yml).
 *
 * <p>x is measured from the left edge of the HUD; the HUD as a whole is centred on screen.
 * y is measured from the top of the vanilla action bar line, negative values are higher up.
 * The pack generator turns the y values into font {@code ascent}s; the renderer uses the x values.
 */
public record HudLayout(
        int offsetX,
        // portrait
        boolean portraitEnabled, int frameX, int frameY, int headX,
        // main box and its text
        int boxX, int boxY,
        int textX, int textY, int lineHeight, int maxLines, int textWidth,
        int indicatorX, int indicatorY,
        // name tab (3-slice: left cap, stretched middle, right cap)
        int tabX, int tabY, int tabSliceLeft, int tabSliceRight,
        int namePaddingLeft, int namePaddingRight, int nameTextY, int nameMaxWidth,
        // answer box (3-slice vertically, grows upwards from answerBoxBottom)
        int answerBoxX, int answerBoxBottom, int answerSliceTop, int answerSliceBottom,
        int answerRowHeight, int maxAnswers,
        int pointerX, int pointerOffsetY, int answerTextX, int answerTextOffsetY, int answerTextWidth) {

    /** The player head object is always drawn by the client on the action bar line: y -1 to 7. */
    public static final int HEAD_TOP = -1;
    public static final int HEAD_SIZE = 8;

    public static HudLayout defaults() {
        return new HudLayout(0,
                true, 0, -7, 6,
                24, -39,
                32, -33, 10, 4, 224,
                250, 5,
                28, -52, 4, 4, 5, 4, -49, 82,
                114, -41, 4, 4, 11, 4,
                119, 2, 130, 2, 128);
    }

    public static HudLayout load(ConfigurationSection c) {
        HudLayout d = defaults();
        return new HudLayout(
                c.getInt("offset-x", d.offsetX),
                c.getBoolean("portrait.enabled", d.portraitEnabled),
                c.getInt("portrait.frame-x", d.frameX),
                c.getInt("portrait.frame-y", d.frameY),
                c.getInt("portrait.head-x", d.headX),
                c.getInt("box.x", d.boxX),
                c.getInt("box.y", d.boxY),
                c.getInt("text.x", d.textX),
                c.getInt("text.y", d.textY),
                c.getInt("text.line-height", d.lineHeight),
                c.getInt("text.max-lines", d.maxLines),
                c.getInt("text.width", d.textWidth),
                c.getInt("indicator.x", d.indicatorX),
                c.getInt("indicator.y", d.indicatorY),
                c.getInt("name-tab.x", d.tabX),
                c.getInt("name-tab.y", d.tabY),
                c.getInt("name-tab.slice-left", d.tabSliceLeft),
                c.getInt("name-tab.slice-right", d.tabSliceRight),
                c.getInt("name-tab.padding-left", d.namePaddingLeft),
                c.getInt("name-tab.padding-right", d.namePaddingRight),
                c.getInt("name-tab.text-y", d.nameTextY),
                c.getInt("name-tab.max-width", d.nameMaxWidth),
                c.getInt("answers.box-x", d.answerBoxX),
                c.getInt("answers.box-bottom", d.answerBoxBottom),
                c.getInt("answers.slice-top", d.answerSliceTop),
                c.getInt("answers.slice-bottom", d.answerSliceBottom),
                c.getInt("answers.row-height", d.answerRowHeight),
                c.getInt("answers.max-answers", d.maxAnswers),
                c.getInt("answers.pointer-x", d.pointerX),
                c.getInt("answers.pointer-y", d.pointerOffsetY),
                c.getInt("answers.text-x", d.answerTextX),
                c.getInt("answers.text-y", d.answerTextOffsetY),
                c.getInt("answers.text-width", d.answerTextWidth));
    }

    /** Top of text line {@code index} (0-based). */
    public int lineTop(int index) {
        return textY + index * lineHeight;
    }

    /** Top of answer row {@code slot}, counted from the bottom of the answer box (slot 0 = lowest row). */
    public int answerRowTop(int slot) {
        return answerBoxBottom - answerSliceBottom - (slot + 1) * answerRowHeight;
    }

    /** Height of the answer box when it holds {@code answers} rows. */
    public int answerBoxHeight(int answers) {
        return answerSliceTop + answers * answerRowHeight + answerSliceBottom;
    }

    /** Text top of the answer font {@code answerK} (1-based, 1 = the highest row of a full box). */
    public int answerFontTop(int k) {
        return answerRowTop(maxAnswers - k) + answerTextOffsetY;
    }

    public List<String> validate() {
        List<String> problems = new ArrayList<>();
        if (maxLines < 1 || maxLines > 12) {
            problems.add("text.max-lines must be between 1 and 12");
        }
        if (maxAnswers < 1 || maxAnswers > 9) {
            problems.add("answers.max-answers must be between 1 and 9");
        }
        if (lineHeight < 1 || answerRowHeight < 1) {
            problems.add("line heights must be positive");
        }
        if (textWidth < 16 || answerTextWidth < 16) {
            problems.add("text widths must be at least 16 pixels");
        }
        return problems;
    }
}
