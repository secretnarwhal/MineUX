package com.mineux.core.ui.term;

import com.mineux.core.ui.term.Term.Line;

import java.util.ArrayList;
import java.util.List;

import static com.mineux.core.ui.term.Term.*;

/**
 * The lists a {@link LookupPane}'s buttons open, each in a floating window of its own (a
 * {@link ListPane}): friends, following, match history, activity, mods, every name. Built
 * from the {@link Profile} each time the window asks, so one opened before its part has
 * loaded fills in when it does. A player's name in one is a click away from their own lookup.
 */
final class LookupLists {

    static final String FRIENDS = "friends", FOLLOWING = "following", MATCHES = "matches", ACTIVITY = "activity",
        MODS = "mods", NAMES = "names", MM_FRIENDS = "mmfriends";

    private LookupLists() {}

    static String title(Profile p, String section) {
        String what = section;
        if (section.equals(MM_FRIENDS)) what = "minemen friends";
        else if (section.equals(MATCHES)) what = "mcpvp matches";
        else if (section.equals(ACTIVITY)) what = "mcpvp activity";
        else if (section.equals(NAMES)) what = "name history";
        return p.shown() + " - " + what;
    }

    static List<Line> lines(Profile p, String section) {
        List<Line> out = new ArrayList<>();
        if (p.failed) {
            out.add(new Line().t(String.valueOf(p.note), RED));
            return out;
        }
        if (section.equals(MM_FRIENDS)) {
            if (p.minemenNote != null) out.add(new Line().t(p.minemenNote, DIM));
            else people(out, p.mmFriends, p.mmFriends.size(), "no friends on minemen");
            return out;
        }
        if (p.mcpvpNote != null) {
            out.add(new Line().t(p.mcpvpNote, DIM));
            return out;
        }
        if (section.equals(FRIENDS)) people(out, p.friends, p.friendsTotal, "no friends on mcpvp");
        else if (section.equals(FOLLOWING)) people(out, p.following, p.followingTotal, "not following anyone on mcpvp");
        else if (section.equals(MATCHES)) matches(out, p.matches);
        else if (section.equals(ACTIVITY)) activity(out, p);
        else if (section.equals(MODS)) plain(out, p.mods, "no mods reported (or hidden)");
        else if (section.equals(NAMES)) plain(out, p.names, "no names listed");
        return out;
    }

    private static void people(List<Line> out, List<String> names, int total, String none) {
        if (names == null) {
            out.add(new Line().t("loading...", DIM));
            return;
        }
        if (names.isEmpty()) {
            out.add(new Line().t(none, DIM));
            return;
        }
        for (String name : names) {
            // A Bedrock name (.Name) is not one Mojang or `lookup` knows.
            if (DiscordCommands.validName(name)) out.add(new Line().run(name, CYAN, "lookup " + name, "look " + name + " up"));
            else out.add(new Line().t(name, FG));
        }
        if (total > names.size()) out.add(new Line().t("+" + (total - names.size()) + " more on the site", FAINT));
    }

    private static void matches(List<Line> out, List<String> matches) {
        if (matches == null) {
            out.add(new Line().t("loading...", DIM));
            return;
        }
        if (matches.isEmpty()) {
            out.add(new Line().t("no matches on mcpvp", DIM));
            return;
        }
        for (int i = 0; i + 5 < matches.size(); i += 6) {
            String result = matches.get(i + 4);
            int color = result.equals("W") ? GREEN : result.equals("L") ? RED : DIM;
            String opponent = matches.get(i + 5);
            Line line = new Line().t(result + " ", color).t(matches.get(i + 3), FG).at(42);
            if (DiscordCommands.validName(opponent)) line.run(opponent, CYAN, "lookup " + opponent, "look " + opponent + " up");
            else line.t(opponent, FG);
            line.at(132).t(matches.get(i + 1), GOLD).at(196).t(matches.get(i), DIM).at(252).t(matches.get(i + 2), FAINT);
            out.add(line);
        }
        out.add(new Line().t("the newest " + matches.size() / 6 + "; the rest are on the site", FAINT));
    }

    private static void activity(List<Line> out, Profile p) {
        out.add(new Line().t(p.online.isEmpty() ? "status unknown" : p.online.toLowerCase(java.util.Locale.ROOT), p.online.equalsIgnoreCase("online") ? GREEN : DIM));
        out.add(new Line().t("top played", GOLD));
        plain(out, p.activityTop, "nothing recorded");
        out.add(new Line().t("recent", GOLD));
        plain(out, p.activityRecent, "nothing recorded");
    }

    private static void plain(List<Line> out, List<String> rows, String none) {
        if (rows.isEmpty()) {
            out.add(new Line().t("  " + none, DIM));
            return;
        }
        for (int i = 0; i < rows.size(); i++) out.add(new Line().t("  " + (i + 1) + "  ", FAINT).t(rows.get(i), FG));
    }
}
