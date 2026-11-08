package com.mineux.core.ui.term;

import java.util.List;

/**
 * Hyprland's dwindle layout: a binary tree of splits. A new pane splits the one it opens on
 * in two -- side by side when that space is wider than tall, one over the other when not --
 * and a closed pane gives its whole half back to its sibling. SUPER+J flips the focused pane's
 * split; dragging an edge moves a split's ratio.
 * <p>
 * Kept alongside the columns ({@link Desk#columns}) for every desk, holding the same panes, so
 * {@code general:layout} can change at any time and each layout keeps its own arrangement.
 */
final class Dwindle {

    static final int AUTO = -1, LEFT = 0, RIGHT = 1, TOP = 2, BOTTOM = 3;

    static final class Node {
        Node parent, a, b;
        /** The pane, for a leaf. */
        Pane pane;
        /** One half over the other rather than side by side. */
        boolean stacked;
        /** Its direction was chosen (a drop on an edge, SUPER+J), so the space's shape no longer picks it. */
        boolean fixed;
        /** Hyprland's split ratio: the first half takes {@code ratio / 2} of the space, 0.1 to 1.9. */
        float ratio = 1f;
        /** Where it was last laid out. */
        float x, y, w, h;
    }

    Node root;

    Node leaf(Pane pane) {
        return leaf(root, pane);
    }

    private static Node leaf(Node node, Pane pane) {
        if (node == null) return null;
        if (node.pane != null) return node.pane == pane ? node : null;
        Node found = leaf(node.a, pane);
        return found != null ? found : leaf(node.b, pane);
    }

    /** The panes, first to last. */
    void order(List<Pane> out) {
        order(root, out);
    }

    private static void order(Node node, List<Pane> out) {
        if (node == null) return;
        if (node.pane != null) {
            out.add(node.pane);
            return;
        }
        order(node.a, out);
        order(node.b, out);
    }

    private Node last() {
        Node node = root;
        while (node != null && node.pane == null) node = node.b;
        return node;
    }

    /**
     * Splits {@code near}'s space (the last pane's, when it is null or not here) for a new pane,
     * which goes on {@code side} of it -- or, for {@link #AUTO}, after it, across the longer side.
     */
    void insert(Pane pane, Pane near, int side) {
        remove(pane);
        Node fresh = new Node();
        fresh.pane = pane;
        if (root == null) {
            root = fresh;
            return;
        }
        Node target = near == null ? null : leaf(near);
        if (target == null) target = last();

        Node split = new Node();
        replaceNode(target, split);
        if (side == AUTO) {
            split.stacked = target.h > target.w;
            split.fixed = false;
        } else {
            split.stacked = side == TOP || side == BOTTOM;
            split.fixed = true;
        }
        boolean after = side == AUTO || side == RIGHT || side == BOTTOM;
        split.a = after ? target : fresh;
        split.b = after ? fresh : target;
        split.a.parent = split;
        split.b.parent = split;
        // The new half starts where its sibling was, so both ease out of the same box.
        split.x = target.x;
        split.y = target.y;
        split.w = target.w;
        split.h = target.h;
    }

    /** Splits the pane under a point, the new pane going on whichever side of it the point is. */
    void insertAt(Pane pane, Pane under, float atX, float atY, int forceSplit) {
        Node target = under == null ? null : leaf(under);
        if (target == null) {
            insert(pane, null, AUTO);
            return;
        }
        boolean stacked = target.h > target.w;
        boolean before;
        if (forceSplit == 1) before = true;
        else if (forceSplit == 2) before = false;
        else if (stacked) before = atY < target.y + target.h / 2f;
        else before = atX < target.x + target.w / 2f;
        insert(pane, under, stacked ? (before ? TOP : BOTTOM) : (before ? LEFT : RIGHT));
        // A split made by where the cursor was is still free to follow the space's shape.
        Node node = leaf(pane);
        if (node != null && node.parent != null) node.parent.fixed = false;
    }

    void remove(Pane pane) {
        Node node = leaf(pane);
        if (node == null) return;
        if (node == root) {
            root = null;
            return;
        }
        Node parent = node.parent;
        Node sibling = parent.a == node ? parent.b : parent.a;
        replaceNode(parent, sibling);
    }

    private void replaceNode(Node old, Node with) {
        relink(old, with, old.parent);
    }

    /** {@code parent}: {@code old}'s, a parameter rather than a local copy of the field (HoistCheck). */
    private void relink(Node old, Node with, Node parent) {
        with.parent = parent;
        if (parent == null) root = with;
        else if (parent.a == old) parent.a = with;
        else parent.b = with;
    }

    void swap(Pane a, Pane b) {
        Node la = leaf(a), lb = leaf(b);
        if (la == null || lb == null) return;
        la.pane = b;
        lb.pane = a;
    }

    /** {@code with} takes {@code old}'s place, as a group's newly picked tab does. */
    void replace(Pane old, Pane with) {
        Node node = leaf(old);
        if (node != null) node.pane = with;
    }

    /** SUPER+J: the split the pane is half of turns the other way. */
    void toggleSplit(Pane pane) {
        Node node = leaf(pane);
        if (node == null || node.parent == null) return;
        node.parent.stacked = !node.parent.stacked;
        node.parent.fixed = true;
    }

    /** The nearest split above a pane that divides the way asked, for resizing; null for none. */
    Node splitAbove(Pane pane, boolean stacked) {
        Node node = leaf(pane);
        for (Node n = node == null ? null : node.parent; n != null; n = n.parent) {
            if (n.stacked == stacked) return n;
        }
        return null;
    }

    /** Whether a pane is in the first half of a split, somewhere below it. */
    static boolean inFirst(Node split, Pane pane) {
        return leaf(split.a, pane) != null;
    }

    /**
     * Sets every pane's target: the tree laid over the box, with {@code gap} between halves.
     * {@code skip} (a pane being dragged) is left out, its sibling taking its half.
     */
    void place(float x, float y, float w, float h, float gap, Pane skip, boolean preserve) {
        place(root, x, y, w, h, gap, skip, preserve);
    }

    private static void place(Node node, float x, float y, float w, float h, float gap, Pane skip, boolean preserve) {
        if (node == null) return;
        node.x = x;
        node.y = y;
        node.w = w;
        node.h = h;
        if (node.pane != null) {
            if (node.pane == skip) return;
            node.pane.targetX = x;
            node.pane.targetY = y;
            node.pane.targetW = w;
            node.pane.targetH = h;
            return;
        }
        if (node.a.pane != null && node.a.pane == skip) {
            place(node.b, x, y, w, h, gap, skip, preserve);
            return;
        }
        if (node.b.pane != null && node.b.pane == skip) {
            place(node.a, x, y, w, h, gap, skip, preserve);
            return;
        }
        if (!node.fixed && !preserve) node.stacked = h > w;
        float share = Math.max(0.1f, Math.min(1.9f, node.ratio)) / 2f;
        if (node.stacked) {
            float first = (h - gap) * share;
            place(node.a, x, y, w, first, gap, skip, preserve);
            place(node.b, x, y + first + gap, w, h - gap - first, gap, skip, preserve);
        } else {
            float first = (w - gap) * share;
            place(node.a, x, y, first, h, gap, skip, preserve);
            place(node.b, x + first + gap, y, w - gap - first, h, gap, skip, preserve);
        }
    }
}
