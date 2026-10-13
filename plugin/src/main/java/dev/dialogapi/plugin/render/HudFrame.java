package dev.dialogapi.plugin.render;

import java.util.List;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;
import org.jspecify.annotations.Nullable;

/**
 * What the HUD shows at one moment.
 *
 * @param name      speaker name for the name tab, or {@code null} to hide the tab
 * @param head      portrait head, or {@code null} to hide the portrait box
 * @param page      the current page of text
 * @param visible   number of characters of the page revealed by the typewriter
 * @param indicator whether the continue indicator is shown
 * @param answers   answer lines (already formatted for selected/unselected), empty = no answer box
 * @param selected  index of the selected answer
 */
public record HudFrame(@Nullable StyledLine name, @Nullable PlayerHeadObjectContents head, Page page, int visible,
                       boolean indicator, List<StyledLine> answers, int selected) {
}
