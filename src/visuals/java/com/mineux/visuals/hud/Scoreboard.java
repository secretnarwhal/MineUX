package com.mineux.visuals.hud;

import com.mineux.visuals.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The scoreboard sidebar as a glass card: the same fifteen lines the game would show, in the
 * same order, on a rounded panel with an accent under the title or down its outer edge, on
 * either side, the score numbers left out if asked (on most servers they are only the order of
 * the lines). The panel eases to its new height when lines come and go.
 */
public final class Scoreboard {

    private static final Logger LOGGER = LoggerFactory.getLogger("mineux/scoreboard");

    private static final Comparator<PlayerScoreEntry> ORDER = Comparator.comparing(PlayerScoreEntry::value)
        .reversed()
        .thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);

    private static float shownHeight = 0.0F - 1.0F;
    private static long lastFrame;
    private static boolean failed;

    private Scoreboard() {}

    /** From the HUD's sidebar: true when this drew, and the game's is to be left out. */
    public static boolean draw(GuiGraphicsExtractor g, Objective objective) {
        SkinConfig config = SkinConfig.get();
        if (failed || !config.scoreboard || objective == null) return false;
        try {
            drawAll(g, config, objective);
            return true;
        } catch (RuntimeException | LinkageError e) {
            failed = true;
            LOGGER.error("The scoreboard failed and is vanilla's until the game restarts", e);
            return false;
        }
    }

    private static void drawAll(GuiGraphicsExtractor g, SkinConfig config, Objective objective) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        net.minecraft.world.scores.Scoreboard board = objective.getScoreboard();
        NumberFormat format = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);
        List<PlayerScoreEntry> entries = new ArrayList<>();
        for (PlayerScoreEntry entry : board.listPlayerScores(objective)) {
            if (!entry.isHidden()) entries.add(entry);
        }
        entries.sort(ORDER);
        if (entries.size() > 15) entries = entries.subList(0, 15);

        List<Component> names = new ArrayList<>();
        List<Component> scores = new ArrayList<>();
        Component title = objective.getDisplayName();
        int widest = font.width(title);
        int pad = Math.max(0, config.sbPadding);
        int gap = font.width(": ");
        for (PlayerScoreEntry entry : entries) {
            PlayerTeam team = board.getPlayersTeam(entry.owner());
            Component name = PlayerTeam.formatNameForTeam(team, entry.ownerName());
            Component score = entry.formatValue(format);
            names.add(name);
            scores.add(score);
            int w = font.width(name) + (config.sbHideNumbers ? 0 : gap + font.width(score));
            widest = Math.max(widest, w);
        }

        float scale = HudScale.factor(mc);
        HudScale.push(g, scale);
        try {
            card(g, font, config, title, names, scores, widest, pad, HudScale.width(g, scale), HudScale.height(g, scale));
        } finally {
            HudScale.pop(g);
        }
    }

    private static void card(GuiGraphicsExtractor g, Font font, SkinConfig config, Component title, List<Component> names,
                             List<Component> scores, int widest, int pad, int width, int height) {
        int line = 9 + Math.max(0, config.sbSpacing);
        int titleH = 12;
        int w = widest + pad * 2;
        int fullH = pad * 2 + titleH + names.size() * line;
        long now = System.nanoTime();
        float dt = lastFrame == 0L ? 0.016F : Math.min(0.1F, (now - lastFrame) / 1.0E9F);
        lastFrame = now;
        if (shownHeight < 0.0F || !config.sbAnimate) shownHeight = fullH;
        else shownHeight += (fullH - shownHeight) * Math.min(1.0F, dt * 12.0F);
        int h = Math.round(shownHeight);

        boolean right = Objects.equals("right", config.sbSide);
        int x0 = right ? width - w - config.sbX : config.sbX;
        int y0 = height / 2 - fullH / 2 + config.sbY;

        int back = Theme.alpha(Hotbar.SURFACE, config.sbOpacity);
        Theme.roundRect(g, x0, y0, w, h, 4, back);
        g.fill(x0 + 1, y0 + 1, x0 + w - 1, y0 + titleH + pad, Theme.alpha(0xFFFFFF, config.sbOpacity * 0.08F));
        if (config.sbBorder) Theme.ring(g, x0, y0, w, h, 4, Theme.EDGE);
        if (Objects.equals("line", config.sbAccent)) {
            g.fill(x0 + pad, y0 + pad + titleH - 2, x0 + w - pad, y0 + pad + titleH - 1, Theme.GOLD_EDGE);
        } else if (Objects.equals("bar", config.sbAccent)) {
            int ex = right ? x0 + w - 2 : x0;
            g.fill(ex, y0 + 3, ex + 2, y0 + h - 3, Theme.GOLD);
        }

        boolean shadow = config.sbShadow;
        int tw = font.width(title);
        int tx = config.sbCenterTitle ? x0 + (w - tw) / 2 : x0 + pad;
        g.text(font, title, tx, y0 + pad, Theme.TEXT, shadow);

        g.enableScissor(x0, y0, x0 + w, y0 + h);
        int y = y0 + pad + titleH;
        for (int i = 0; i < names.size(); i++) {
            g.text(font, names.get(i), x0 + pad, y, Theme.TEXT, shadow);
            if (!config.sbHideNumbers) {
                Component score = scores.get(i);
                g.text(font, score, x0 + w - pad - font.width(score), y, Theme.RED, shadow);
            }
            y += line;
        }
        g.disableScissor();
    }
}
