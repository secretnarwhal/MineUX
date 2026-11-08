package com.mineux.core.ui.term;

import com.mineux.core.ui.term.Term.Hit;
import com.mineux.core.ui.term.Term.Span;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;

import static com.mineux.core.ui.term.Term.*;

/**
 * {@code lookup <player>} as a window: who they are, then what mcpvp.com and minemen.club
 * know about them ({@link LookupSections}) -- rating factors as title cards, numbers as
 * tiles, rank and ELO history as graphs -- and a row of buttons, each opening one of the long
 * lists ({@link LookupLists}) in a floating window of its own. A window like any other on the
 * desk; it scrolls when it is shorter than what it shows.
 * <p>
 * It draws its {@link Profile} as it stands each frame, so the sources fill in as they answer.
 */
final class LookupPane extends Pane {

    /** The prefix of this window's kind, for {@code Desktop#openWindow}: {@code lookup:name}, or {@code lookup:name:friends} for a list. */
    static final String KIND = "lookup:";
    static final int WIDTH = 330, HEIGHT = 380;

    private static final int HEADER_H = 12;
    private static final int PAD = 6;

    final Profile profile;
    private Desktop desktop;
    private int scroll, contentH;

    private final Span copyUuid = action("[copy]", CYAN, "copy the uuid", this::copyUuid);
    private final Span copyDiscord = action("[copy]", CYAN, "copy the discord id", this::copyDiscord);
    private final List<Span> buttons = new ArrayList<>();
    private final List<Span> links = new ArrayList<>();
    /** A chip a kit, remade when the kits arrive. */
    private List<Span> kitSpans = new ArrayList<>();
    private List<String> kitsFor;
    private int kit;

    LookupPane(Desktop desktop, Profile profile) {
        this.desktop = desktop;
        this.profile = profile;
        this.w = 1;
        this.h = 1;
        list(LookupLists.FRIENDS, "friends", "their friends on mcpvp");
        list(LookupLists.FOLLOWING, "following", "who they follow on mcpvp");
        list(LookupLists.MATCHES, "matches", "their newest matches on mcpvp");
        list(LookupLists.ACTIVITY, "activity", "the servers they play on");
        list(LookupLists.MODS, "mods", "the mods their client reported");
        list(LookupLists.NAMES, "names", "every name they have had");
        list(LookupLists.MM_FRIENDS, "mm friends", "their friends on minemen");
        link("mcpvp", ProfileLoader.MCPVP + "@", false);
        link("minemen", ProfileLoader.MINEMEN, false);
        link("namemc", "https://namemc.com/profile/", true);
    }

    private static Span action(String text, int color, String hint, Runnable action) {
        Span span = new Span(text, color);
        span.hint = hint;
        span.action = action;
        return span;
    }

    /** A button that opens one of the lists, its label counting what is in it once that is known. */
    private void list(String section, String label, String hint) {
        Span span = new Span(label, FG);
        span.liveText = () -> label + count(section);
        span.liveColor = () -> FG;
        span.hint = hint;
        span.action = () -> desktop.openWindow(KIND + profile.asked + ":" + section);
        buttons.add(span);
    }

    private String count(String section) {
        Profile p = profile;
        if (section.equals(LookupLists.FRIENDS)) return p.friends == null ? "" : " " + p.friendsTotal;
        if (section.equals(LookupLists.FOLLOWING)) return p.following == null ? "" : " " + p.followingTotal;
        if (section.equals(LookupLists.MATCHES)) return p.matches == null ? "" : " " + p.matches.size() / 6;
        if (section.equals(LookupLists.MODS)) return p.mcpvpNote != null ? "" : " " + p.mods.size();
        if (section.equals(LookupLists.NAMES)) return p.mcpvpNote != null ? "" : " " + p.names.size();
        if (section.equals(LookupLists.MM_FRIENDS)) return p.minemenNote != null ? "" : " " + p.mmFriends.size();
        return "";
    }

    /** A link to their page on a site, by name or by UUID, in the browser. */
    private void link(String site, String base, boolean byUuid) {
        links.add(action(site, CYAN, "open their " + site + " page in the browser", () -> {
            String who = byUuid ? profile.uuid : profile.name;
            if (who != null) Util.getPlatform().openUri(base + who);
        }));
    }

    private void copyUuid() {
        String uuid = profile.uuid;
        if (uuid != null) Minecraft.getInstance().keyboardHandler.setClipboard(uuid);
    }

    private void copyDiscord() {
        String discord = profile.discord;
        if (discord != null) Minecraft.getInstance().keyboardHandler.setClipboard(discord);
    }

    @Override
    int minW() {
        return 250;
    }

    @Override
    int minH() {
        return HEADER_H + 60;
    }

    @Override
    int header() {
        return HEADER_H;
    }

    @Override
    String title() {
        return "lookup " + profile.shown();
    }

    @Override
    void attach(Desktop screen) {
        this.desktop = screen;
    }

    @Override
    void close() {
        desktop.closeWindow(this);
    }

    @Override
    void scrolled(double mouseY, double amount) {
        scroll -= (int) Math.signum(amount) * 20;
    }

    @Override
    void render(GuiGraphicsExtractor g, Font font, long now, int mouseX, int mouseY, boolean focused,
                List<Hit> hits, Hit hovered) {
        int wx = ix(), wy = iy(), ww = iw(), wh = ih();
        if (ww < 8 || wh < 8) return;

        g.fillGradient(wx, wy, wx + ww, wy + wh, 0xA6121620, 0xBC07080C);
        Decor.border(g, this, wx, wy, ww, wh, focused, 0);
        hits.add(new Hit(wx, wy, wx + ww, wy + wh, null, null, -1, null, ACT_BODY));

        g.enableScissor(wx, wy, wx + ww, wy + wh);
        header(g, font, wx, wy, ww, mouseX, mouseY, hits);

        int top = wy + HEADER_H + 1, bottom = wy + wh - 2;
        scroll = Math.max(0, Math.min(scroll, contentH - (bottom - top)));
        g.enableScissor(wx + 1, top, wx + ww - 1, bottom);
        Sheet s = new Sheet(g, font, hits, hovered, mouseX, mouseY, wx + PAD, ww - PAD * 2, top, bottom, top + 4 - scroll);
        body(s, now);
        contentH = s.y + scroll - top + 4;
        g.disableScissor();
        g.disableScissor();
        drawGrip(g, onGrip(mouseX, mouseY));
    }

    private void header(GuiGraphicsExtractor g, Font font, int wx, int wy, int ww, int mouseX, int mouseY, List<Hit> hits) {
        g.fill(wx, wy, wx + ww, wy + HEADER_H, 0x40000000);
        g.fill(wx, wy + HEADER_H - 1, wx + ww, wy + HEADER_H, 0x1CFFFFFF);
        g.text(font, "lookup - " + profile.shown(), wx + PAD, wy + 2, GOLD, false);
        String close = "x";
        int cx = wx + ww - PAD - font.width(close);
        boolean closeHot = mouseX >= cx - 3 && mouseX < cx + font.width(close) + 3 && mouseY >= wy && mouseY < wy + HEADER_H - 1;
        if (closeHot) g.fill(cx - 3, wy, cx + font.width(close) + 3, wy + HEADER_H - 1, 0x40FF5C57);
        g.text(font, close, cx, wy + 2, closeHot ? RED : DIM, false);
        Span closeSpan = new Span(close, RED);
        closeSpan.hint = "close";
        hits.add(new Hit(cx - 3, wy, cx + font.width(close) + 3, wy + HEADER_H - 1, null, closeSpan, -1, null, ACT_CLOSE));
    }

    private void body(Sheet s, long now) {
        Profile p = profile;
        String note = p.note;
        if (note != null) {
            s.text(note, s.left, p.failed ? RED : DIM);
            s.y += Sheet.LINE_H;
            return;
        }
        identity(s, p);
        LookupSections.mcpvp(s, p, kitSpans(p), kit);
        LookupSections.minemen(s, p, now);

        s.section("more", "", DIM);
        int x = s.left;
        for (Span button : buttons) x = s.button(button, x);
        s.y += Sheet.LINE_H + 5;
        x = s.text("open", s.left, DIM) + 8;
        for (Span link : links) x = s.link(link, x) + 8;
        s.y += Sheet.LINE_H;
    }

    private void identity(Sheet s, Profile p) {
        int x = s.text(p.shown(), s.left, FG) + 6;
        x = s.text(String.valueOf(p.uuid), x, DIM) + 5;
        s.link(copyUuid, x);
        s.y += Sheet.LINE_H;

        s.text("discord", s.left, DIM);
        String discord = p.discord;
        if (discord == null) {
            s.text(String.valueOf(p.discordNote), s.left + 52, DIM);
        } else {
            s.link(copyDiscord, s.text(discord, s.left + 52, GREEN) + 5);
        }
        s.y += Sheet.LINE_H;

        List<String> names = p.names;
        if (names.size() > 1) {
            String was = names.get(1);
            if (names.size() > 2) was = was + ", +" + (names.size() - 2) + " earlier";
            s.row("was", was, FG);
        }
    }

    /** The chips for the kits as they now are, the first (the most played) picked when they change. */
    private List<Span> kitSpans(Profile p) {
        List<String> kits = p.kits;
        if (kits == kitsFor) return kitSpans;
        List<Span> spans = new ArrayList<>();
        for (int i = 0; i * McPvpKits.STRIDE < kits.size(); i++) {
            int index = i;
            String name = kits.get(i * McPvpKits.STRIDE);
            String tier = kits.get(i * McPvpKits.STRIDE + 1);
            Span span = new Span(name, FG);
            span.liveText = () -> kit == index ? "> " + name : name;
            span.liveColor = () -> kit == index ? GOLD : LookupSections.tierColor(tier);
            span.hint = name + ": " + tier + ", " + kits.get(index * McPvpKits.STRIDE + 5) + " matches";
            span.action = () -> kit = index;
            spans.add(span);
        }
        kitsFor = kits;
        kitSpans = spans;
        kit = 0;
        return spans;
    }
}
