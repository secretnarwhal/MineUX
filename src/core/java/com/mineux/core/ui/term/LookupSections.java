package com.mineux.core.ui.term;

import com.mineux.core.ui.term.Term.Span;

import java.util.List;
import java.util.Locale;

import static com.mineux.core.ui.term.Term.*;

/**
 * The two halves of a {@link LookupPane}: what mcpvp.com knows about the player, and what
 * minemen.club does. Each is a heading, a row of numbers as tiles, and its graph.
 */
final class LookupSections {

    private static final String[] FACTOR = {"skill", "confidence", "competition", "variety", "challenges"};
    private static final int GRAPH_H = 34;

    private LookupSections() {}

    // ---- mcpvp.com ------------------------------------------------------

    /**
     * @param kitSpans a chip a kit in {@code p.kits}, to pick the one the cards are about
     * @param kit      the kit picked
     */
    static void mcpvp(Sheet s, Profile p, List<Span> kitSpans, int kit) {
        String note = p.mcpvpNote;
        if (note != null) {
            s.section("mcpvp.com", "", DIM);
            s.text(note, s.left, DIM);
            s.y += Sheet.LINE_H;
            return;
        }
        boolean banned = p.ban.equals("banned");
        boolean on = p.online.equalsIgnoreCase("online");
        s.section("mcpvp.com", (p.online.isEmpty() ? "" : p.online.toLowerCase(Locale.ROOT) + " · ") + p.ban, banned ? RED : on ? GREEN : DIM);

        List<String> kits = p.kits;
        if (!kits.isEmpty() && kit * McPvpKits.STRIDE < kits.size()) cards(s, kits, kitSpans, kit);

        List<String> st = p.stats;
        int gap = 3, w = (s.width - gap * 3) / 4;
        s.tile(s.left, w, McPvp.stat(st, "kills"), "kills", FG);
        s.tile(s.left + (w + gap), w, McPvp.stat(st, "deaths"), "deaths", FG);
        s.tile(s.left + (w + gap) * 2, w, McPvp.stat(st, "kdr"), "k/d", GOLD);
        s.tile(s.left + (w + gap) * 3, w, McPvp.stat(st, "win_rate"), "win rate", GOLD);
        s.y += Sheet.TILE_H + 2;
        s.tile(s.left, w, McPvp.stat(st, "wins"), "wins", GREEN);
        s.tile(s.left + (w + gap), w, McPvp.stat(st, "losses"), "losses", RED);
        s.tile(s.left + (w + gap) * 2, w, McPvp.stat(st, "current_streak"), "streak", FG);
        s.tile(s.left + (w + gap) * 3, w, McPvp.stat(st, "best_streak"), "best streak", FG);
        s.y += Sheet.TILE_H + 2;
        s.record(Minemen.number(McPvp.stat(st, "wins")), Minemen.number(McPvp.stat(st, "losses")));

        tiers(s, p.tiers);
        s.row("client", client(p), FG);

        float[] rank = p.rankHistory;
        if (rank.length >= 2) {
            s.text("overall rank", s.left, DIM);
            s.textRight("#" + grouped(rank[0]) + " -> #" + grouped(rank[rank.length - 1]), s.left + s.width, FG);
            s.y += Sheet.LINE_H;
            // A ranking: lower is better, so the graph climbs as they do.
            Chart.line(s.g, s.left, s.y, s.width, GRAPH_H, rank, GOLD, true);
            s.y += GRAPH_H + 3;
        }
    }

    /** The picked kit's five rating factors as title cards, the kits to pick from over them, and what the hovered card is made of under them. */
    private static void cards(Sheet s, List<String> kits, List<Span> kitSpans, int kit) {
        int x = s.left;
        for (int i = 0; i < kitSpans.size() && i * McPvpKits.STRIDE < kits.size(); i++) x = s.button(kitSpans.get(i), x);
        s.y += Sheet.LINE_H + 4;

        int at = kit * McPvpKits.STRIDE;
        int gap = 3, w = (s.width - gap * (McPvpKits.FACTORS - 1)) / McPvpKits.FACTORS;
        String detail = kits.get(at + 7);
        for (int i = 0; i < McPvpKits.FACTORS; i++) {
            String score = kits.get(at + 6 + i * 2);
            int value = Minemen.number(score);
            if (s.card(s.left + i * (w + gap), w, score, FACTOR[i], value / 100f, scoreColor(value))) detail = kits.get(at + 7 + i * 2);
        }
        s.y += Sheet.CARD_H + 1;
        String rank = kits.get(at + 2);
        String record = kits.get(at + 1) + (rank.equals("-") ? "" : " #" + rank) + " · " + kits.get(at + 3) + "W " + kits.get(at + 4) + "L";
        s.text(detail, s.left, FG);
        s.textRight(record, s.left + s.width, DIM);
        s.y += Sheet.LINE_H + 3;
    }

    private static void tiers(Sheet s, List<String> tiers) {
        s.text("tiers", s.left, DIM);
        if (tiers.isEmpty()) s.text("none", s.left + 52, DIM);
        int x = s.left + 52;
        for (String tier : tiers) {
            if (x + s.font.width(tier) > s.left + s.width) {
                x = s.left + 52;
                s.y += Sheet.LINE_H;
            }
            x = s.text(tier, x, tierColor(tier)) + 8;
        }
        s.y += Sheet.LINE_H;
    }

    private static String client(Profile p) {
        List<String> client = p.client;
        if (client.isEmpty()) return "not seen on their server";
        StringBuilder out = new StringBuilder();
        for (int i = 1; i < client.size(); i += 2) {
            if (out.length() > 0) out.append(" · ");
            out.append(client.get(i));
        }
        return out.append(" · ").append(p.mods.size()).append(" mods").toString();
    }

    // ---- minemen.club ---------------------------------------------------

    static void minemen(Sheet s, Profile p, long now) {
        String note = p.minemenNote;
        if (note != null) {
            s.section("minemen.club", "", DIM);
            s.text(note, s.left, DIM);
            s.y += Sheet.LINE_H;
            return;
        }
        boolean on = p.mmStatus.equalsIgnoreCase("online");
        String state = p.mmStatus.toLowerCase(Locale.ROOT);
        if (!on && p.mmSeen > 0) state = state + " · seen " + ago(now - p.mmSeen) + " ago";
        s.section("minemen.club", state, on ? GREEN : DIM);

        List<String> ladders = p.ladders;
        int wins = 0, losses = 0, casual = 0;
        for (int i = 0; i + 4 < ladders.size(); i += 5) {
            wins += Minemen.number(ladders.get(i + 2));
            losses += Minemen.number(ladders.get(i + 3));
            casual += Minemen.number(ladders.get(i + 4));
        }
        int gap = 3, w = (s.width - gap * 4) / 5;
        s.tile(s.left, w, p.mmGlobal.isEmpty() ? "-" : p.mmGlobal, "global elo", GOLD);
        s.tile(s.left + (w + gap), w, p.mmWorld.isEmpty() ? "-" : "#" + p.mmWorld, "world", FG);
        s.tile(s.left + (w + gap) * 2, w, String.valueOf(wins), "ranked w", GREEN);
        s.tile(s.left + (w + gap) * 3, w, String.valueOf(losses), "ranked l", RED);
        s.tile(s.left + (w + gap) * 4, w, String.valueOf(casual), "casual w", FG);
        s.y += Sheet.TILE_H + 2;
        s.record(wins, losses);

        String about = p.mmRank;
        if (!p.mmJoined.isEmpty()) about = about + " · joined " + p.mmJoined;
        if (!p.mmViews.isEmpty()) about = about + " · " + p.mmViews + " views a month";
        s.row("rank", about, FG);
        ladders(s, ladders);

        float[] elo = p.eloHistory;
        if (elo.length >= 2) {
            s.text("top ladder elo", s.left, DIM);
            s.textRight(grouped(elo[0]) + " -> " + grouped(elo[elo.length - 1]), s.left + s.width, FG);
            s.y += Sheet.LINE_H;
            Chart.line(s.g, s.left, s.y, s.width, GRAPH_H, elo, CYAN, false);
            s.y += GRAPH_H + 3;
        }
    }

    /** A row a ladder they have a game on: its ELO as a number and a bar, the ranked record, the casual wins. */
    private static void ladders(Sheet s, List<String> ladders) {
        int best = 1200;
        for (int i = 0; i + 4 < ladders.size(); i += 5) best = Math.max(best, Minemen.number(ladders.get(i + 1)));
        int barX = s.left + 122, barW = Math.max(20, s.width - 122 - 96);
        boolean any = false;
        for (int i = 0; i + 4 < ladders.size(); i += 5) {
            int elo = Minemen.number(ladders.get(i + 1));
            int won = Minemen.number(ladders.get(i + 2)), lost = Minemen.number(ladders.get(i + 3)), casual = Minemen.number(ladders.get(i + 4));
            if (won + lost + casual == 0) continue;
            any = true;
            s.text(s.font.plainSubstrByWidth(ladders.get(i), 86), s.left, FG);
            s.textRight(String.valueOf(elo), s.left + 116, elo > 1000 ? GOLD : DIM);
            Meter.bar(s.g, barX, s.y + 2, barW, 5, Math.max(0f, (elo - 800) / (float) (best - 800)), elo > 1000 ? GOLD : FAINT, 1f);
            s.text(won + "-" + lost, barX + barW + 6, won >= lost ? GREEN : RED);
            s.textRight(casual + " cas", s.left + s.width, DIM);
            s.y += Sheet.LINE_H;
        }
        if (!any) {
            s.text("no games on any ladder", s.left, DIM);
            s.y += Sheet.LINE_H;
        }
        s.y += 2;
    }

    // ---- shared ---------------------------------------------------------

    /** Red, amber, green: a 0-100 score's thirds. */
    static int scoreColor(int score) {
        return score < 34 ? RED : score < 67 ? AMBER : GREEN;
    }

    /** A tier's colour by its number, as the tier lists paint them: {@code HT1} gold, down to the sixes. */
    static int tierColor(String tier) {
        if (tier.isEmpty()) return DIM;
        char n = tier.charAt(tier.length() - 1);
        if (n == '1') return GOLD;
        if (n == '2') return 0xFF9EB3D1;
        if (n == '3') return 0xFFEB9138;
        if (n == '4') return 0xFF9FD27F;
        if (n == '5') return BLUE;
        return DIM;
    }

    static String grouped(float value) {
        return String.format(Locale.ROOT, "%,d", Math.round(value));
    }

    /** How long, roughly: {@code 40s}, {@code 12m}, {@code 5h}, {@code 3d}. */
    static String ago(long ms) {
        long s = Math.max(0L, ms / 1000L);
        if (s < 60) return s + "s";
        if (s < 3600) return s / 60 + "m";
        if (s < 86400) return s / 3600 + "h";
        return s / 86400 + "d";
    }
}
