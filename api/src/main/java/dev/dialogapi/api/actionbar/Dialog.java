package dev.dialogapi.api.actionbar;

import dev.dialogapi.api.actionbar.DialogBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * An immutable dialog: a speaker, an optional portrait and an ordered set of nodes.
 * Create one with {@link #builder(String)} or a YAML file in {@code plugins/DialogAPI/actionbar/dialogs/}.
 */
public final class Dialog {

    /** Answer/node target that ends the dialog. */
    public static final String END = "@end";

    private final String id;
    private final @Nullable String speaker;
    private final @Nullable Portrait portrait;
    private final Pacing pacing;
    private final @Nullable Double maxDistance;
    private final String startNode;
    private final Map<String, DialogNode> nodes;
    private final List<String> order;

    public Dialog(String id, @Nullable String speaker, @Nullable Portrait portrait, Pacing pacing,
                  @Nullable Double maxDistance, @Nullable String startNode, Collection<DialogNode> nodes) {
        this.id = Objects.requireNonNull(id, "id");
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("Dialog '" + id + "' has no nodes");
        }
        Map<String, DialogNode> map = new LinkedHashMap<>();
        for (DialogNode node : nodes) {
            if (map.put(node.id(), node) != null) {
                throw new IllegalArgumentException("Dialog '" + id + "' has two nodes with id '" + node.id() + "'");
            }
        }
        this.speaker = speaker;
        this.portrait = portrait;
        this.pacing = pacing;
        this.maxDistance = maxDistance;
        this.nodes = Collections.unmodifiableMap(map);
        this.order = List.copyOf(map.keySet());
        this.startNode = startNode != null ? startNode : order.getFirst();
        if (!map.containsKey(this.startNode)) {
            throw new IllegalArgumentException("Dialog '" + id + "' starts at unknown node '" + this.startNode + "'");
        }
    }

    public static DialogBuilder builder(String id) {
        return new DialogBuilder(id);
    }

    public String id() {
        return id;
    }

    public @Nullable String speaker() {
        return speaker;
    }

    public @Nullable Portrait portrait() {
        return portrait;
    }

    public Pacing pacing() {
        return pacing;
    }

    /** Maximum distance from the NPC before the dialog ends, or {@code null} for the config default. */
    public @Nullable Double maxDistance() {
        return maxDistance;
    }

    public String startNodeId() {
        return startNode;
    }

    public DialogNode startNode() {
        return nodes.get(startNode);
    }

    public @Nullable DialogNode node(String nodeId) {
        return nodes.get(nodeId);
    }

    public Collection<DialogNode> nodes() {
        return nodes.values();
    }

    /** The node declared after {@code nodeId}, or {@code null} if it is the last one. */
    public @Nullable DialogNode nodeAfter(String nodeId) {
        int index = order.indexOf(nodeId);
        return index >= 0 && index + 1 < order.size() ? nodes.get(order.get(index + 1)) : null;
    }

    /** Checks node references inside this dialog. Returns human-readable problems (empty = valid). */
    public List<String> validate() {
        List<String> problems = new ArrayList<>();
        for (DialogNode node : nodes.values()) {
            if (node.pages().isEmpty() || node.pages().stream().allMatch(String::isBlank)) {
                problems.add("node '" + node.id() + "' has no text");
            }
            checkTargets(problems, "node '" + node.id() + "'", node.onEnter());
            if (node instanceof TextNode text) {
                checkTargets(problems, "node '" + node.id() + "'", text.then());
            } else if (node instanceof QuestionNode question) {
                if (question.answers().isEmpty()) {
                    problems.add("question '" + node.id() + "' has no answers");
                }
                for (int i = 0; i < question.answers().size(); i++) {
                    checkTargets(problems, "question '" + node.id() + "' answer " + (i + 1),
                            question.answers().get(i).actions());
                }
                if (question.defaultAnswer() >= question.answers().size()) {
                    problems.add("question '" + node.id() + "' has default answer " + (question.defaultAnswer() + 1)
                            + " but only " + question.answers().size() + " answers");
                }
            }
        }
        return problems;
    }

    private void checkTargets(List<String> problems, String where, List<DialogAction> actions) {
        for (DialogAction action : actions) {
            if (action instanceof DialogAction.GoTo(String target) && !nodes.containsKey(target)) {
                problems.add(where + " jumps to unknown node '" + target + "'");
            }
        }
    }

    @Override
    public String toString() {
        return "Dialog[" + id + ", " + nodes.size() + " nodes]";
    }
}
