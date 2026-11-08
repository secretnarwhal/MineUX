package com.mineux.visuals;

import com.mineux.core.TrayGroup;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * One of the visuals' four tray groups, and the {@code /mineux/visuals.txt} dirs it holds: each
 * top-level dir named here and everything under it. Its {@link #rows} are the modules the bar
 * shows under its icon and the pages of its Glass settings window ({@link VisualRows}).
 */
public final class VisualGroup extends TrayGroup {

    private final String name;
    private final String icon;
    private final String blurb;
    private final List<String> dirs;
    private final List<Row> rows;

    VisualGroup(String name, String icon, String blurb, List<Row> rows, String... dirs) {
        this.name = name;
        this.icon = icon;
        this.blurb = blurb;
        this.rows = rows;
        this.dirs = Collections.unmodifiableList(Arrays.asList(dirs));
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String icon() {
        return icon;
    }

    @Override
    public String blurb() {
        return blurb;
    }

    @Override
    public List<Row> rows() {
        return rows;
    }

    /** The top-level dirs of {@code visuals.txt} in this group, in tray order. */
    public List<String> dirs() {
        return dirs;
    }

    /** Whether {@code path} (a dir of {@code visuals.txt}, e.g. {@code camera/blur}) is in this group. */
    public boolean holds(String path) {
        int slash = path.indexOf('/');
        String top = slash < 0 ? path : path.substring(0, slash);
        return dirs.contains(top);
    }
}
