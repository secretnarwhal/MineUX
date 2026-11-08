package com.mineux.visuals.hud;

import com.mineux.visuals.ui.Theme;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The hotbar in the client's look, where the game's goes: a glass dock, the selection sliding
 * from slot to slot in the accent colour, slot numbers if asked, the offhand on its side, the
 * attack charge beside it when the game's option puts it there; and the experience bar as a
 * thin strip over the dock with the level above it. The hearts, food, armour and air are
 * {@link StatusBars}.
 */
public final class Hotbar {

    private static final Logger LOGGER = LoggerFactory.getLogger("mineux/hotbar");

    static final int SURFACE = 0x12161F;

    private static float selX = 0.0F - 1.0F;
    private static float shownXp = 0.0F - 1.0F;
    private static long lastFrame;
    private static boolean failed;

    private Hotbar() {}

    /** Whether the slots are this one's rather than the game's. */
    static boolean drawsSlots() {
        SkinConfig config = SkinConfig.get();
        return !failed && config.hotbar && config.hbSlots;
    }

    /** Whether the experience bar is this one's: the game's would show experience (no locator, no jumping mount). */
    public static boolean ownsExperience() {
        SkinConfig config = SkinConfig.get();
        if (failed || !config.hotbar || !config.hbXp) return false;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer me = mc.player;
        if (me == null || mc.gameMode == null || !mc.gameMode.hasExperience()) return false;
        if (me.jumpableVehicle() != null) return false;
        return !me.connection.getWaypointManager().hasWaypoints();
    }

    /** From the HUD's hotbar: draws the slots and the experience strip; true leaves the game's slots out. */
    public static boolean draw(GuiGraphicsExtractor g) {
        SkinConfig config = SkinConfig.get();
        if (failed || !config.hotbar) return false;
        try {
            return drawAll(g, config);
        } catch (RuntimeException | LinkageError e) {
            failed = true;
            LOGGER.error("The hotbar failed and is vanilla's until the game restarts", e);
            return false;
        }
    }

    private static boolean drawAll(GuiGraphicsExtractor g, SkinConfig config) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer me = mc.player;
        if (me == null) return false;
        long now = System.nanoTime();
        float dt = lastFrame == 0L ? 0.016F : Math.min(0.1F, (now - lastFrame) / 1.0E9F);
        lastFrame = now;
        float scale = HudScale.factor(mc);
        HudScale.push(g, scale);
        try {
            int width = HudScale.width(g, scale);
            int height = HudScale.height(g, scale);
            if (ownsExperience()) experience(g, mc.font, config, me, width, height, dt);
            if (!config.hbSlots) return false;
            slots(g, mc, config, me, width, height, dt);
            return true;
        } finally {
            HudScale.pop(g);
        }
    }

    private static void slots(GuiGraphicsExtractor g, Minecraft mc, SkinConfig config, LocalPlayer me, int width, int height, float dt) {
        Font font = mc.font;
        int accent = SkinConfig.rgb(config.hbAccent, 0xEFC648);
        int cx = width / 2;
        int x0 = cx - 91;
        int y0 = height - 22;
        int back = Theme.alpha(SURFACE, config.hbOpacity);
        Theme.roundRect(g, x0, y0, 182, 22, 3, back);
        Theme.ring(g, x0, y0, 182, 22, 3, Theme.EDGE);

        int selected = me.getInventory().getSelectedSlot();
        float target = selected * 20.0F;
        if (selX < 0.0F || !config.hbSmooth) selX = target;
        else selX += (target - selX) * Math.min(1.0F, dt * 18.0F);
        int sx = x0 + 1 + Math.round(selX);
        g.fill(sx, y0 + 1, sx + 20, y0 + 21, Theme.alpha(accent, 0.18F));
        Theme.ring(g, sx, y0 + 1, 20, 20, 2, Theme.alpha(accent, 0.9F));
        g.fill(sx + 4, y0 + 20, sx + 16, y0 + 21, accent);

        for (int i = 0; i < 9; i++) {
            int ix = cx - 90 + i * 20 + 2;
            int iy = height - 19;
            ItemStack stack = me.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                g.item(me, stack, ix, iy, i + 1);
                g.itemDecorations(font, stack, ix, iy);
            }
            if (config.hbSlotNumbers) {
                g.pose().pushMatrix();
                g.pose().translate(ix - 1.0F, iy - 2.0F);
                g.pose().scale(0.5F, 0.5F);
                g.text(font, Integer.toString(i + 1), 0, 0, i == selected ? accent : Theme.TEXT_MUTED, false);
                g.pose().popMatrix();
            }
        }

        ItemStack off = me.getOffhandItem();
        boolean leftSide = me.getMainArm().getOpposite() == HumanoidArm.LEFT;
        if (!off.isEmpty()) {
            int ox = leftSide ? x0 - 26 : x0 + 182 + 4;
            Theme.roundRect(g, ox, y0, 22, 22, 3, back);
            Theme.ring(g, ox, y0, 22, 22, 3, Theme.EDGE);
            g.item(me, off, ox + 3, height - 19, 10);
            g.itemDecorations(font, off, ox + 3, height - 19);
        }

        if (mc.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR) {
            float charge = me.getAttackStrengthScale(0.0F);
            if (charge < 1.0F) {
                // Beside the dock on the side the offhand is not.
                int bx = leftSide ? x0 + 182 + 6 : x0 - 10;
                int fill = Math.round(18.0F * charge);
                g.fill(bx, height - 20, bx + 4, height - 2, Theme.alpha(SURFACE, config.hbOpacity));
                g.fill(bx + 1, height - 3 - fill, bx + 3, height - 3, accent);
            }
        }
    }

    /** The experience bar: a strip over the dock, the level above its middle. */
    private static void experience(GuiGraphicsExtractor g, Font font, SkinConfig config, LocalPlayer me, int width, int height, float dt) {
        int colour = SkinConfig.rgb(config.hbXpColor, 0x7CE35B);
        float progress = Math.max(0.0F, Math.min(1.0F, me.experienceProgress));
        if (shownXp < 0.0F || !config.hbSmooth || progress < shownXp - 0.5F) shownXp = progress;
        else shownXp += (progress - shownXp) * Math.min(1.0F, dt * 10.0F);
        int x0 = width / 2 - 91;
        int y = height - 25;
        g.fill(x0, y, x0 + 182, y + 2, Theme.alpha(SURFACE, config.hbOpacity));
        g.fill(x0, y, x0 + Math.round(182.0F * shownXp), y + 2, colour);
        int level = me.experienceLevel;
        if (level > 0) {
            String text = Integer.toString(level);
            int tx = width / 2 - font.width(text) / 2;
            int ty = height - 34;
            g.text(font, text, tx + 1, ty, 0xFF000000, false);
            g.text(font, text, tx - 1, ty, 0xFF000000, false);
            g.text(font, text, tx, ty + 1, 0xFF000000, false);
            g.text(font, text, tx, ty - 1, 0xFF000000, false);
            g.text(font, text, tx, ty, colour, false);
        }
    }
}
