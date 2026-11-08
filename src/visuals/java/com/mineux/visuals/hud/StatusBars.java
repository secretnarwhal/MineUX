package com.mineux.visuals.hud;

import com.mineux.visuals.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Objects;

/**
 * Hearts, armour, food and air as bars over the hotbar, where the game's icons go: health on
 * the left with absorption after it and the damage you took lingering as a pale trail before
 * it drains, food on the right; armour and air as thin bars above them; the numbers over the
 * ends. Poison, wither, freezing and hunger tint their bar as the icons would; a mount's own
 * hearts keep the food's place, as in the game. "pips" breaks each bar into ten.
 */
public final class StatusBars {

    private static final Logger LOGGER = LoggerFactory.getLogger("mineux/statusbars");

    private static final int BAR_W = 85;
    private static final int TRAIL = 0xC8FFFFFF;

    private static float shownHealth = 0.0F - 1.0F;
    private static float trailHealth = 0.0F - 1.0F;
    private static float trailHold;
    private static float shownAbsorb;
    private static float shownFood = 0.0F - 1.0F;
    private static float shownArmor;
    private static float shownAir = 0.0F - 1.0F;
    private static long lastFrame;
    private static boolean failed;

    private StatusBars() {}

    /** From the HUD's hearts: true when this drew, and the game's are to be left out. */
    public static boolean draw(GuiGraphicsExtractor g) {
        SkinConfig config = SkinConfig.get();
        if (failed || !config.hotbar || !config.hbStatus) return false;
        try {
            return drawAll(g, config);
        } catch (RuntimeException | LinkageError e) {
            failed = true;
            LOGGER.error("The status bars failed and are vanilla's until the game restarts", e);
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
            bars(g, mc.font, config, me, HudScale.width(g, scale), HudScale.height(g, scale), dt);
        } finally {
            HudScale.pop(g);
        }
        return true;
    }

    private static void bars(GuiGraphicsExtractor g, Font font, SkinConfig config, LocalPlayer me, int width, int height, float dt) {
        boolean smooth = config.hbSmooth;
        boolean pips = Objects.equals("pips", config.hbStyle);
        float k = smooth ? Math.min(1.0F, dt * 12.0F) : 1.0F;
        int left = width / 2 - 91;
        int right = width / 2 + 91 - BAR_W;
        int row = height - 31;
        int back = Theme.alpha(Hotbar.SURFACE, config.hbOpacity);

        // ---- health ----
        float health = Math.max(0.0F, me.getHealth());
        float max = Math.max((float) me.getAttributeValue(Attributes.MAX_HEALTH), health);
        float absorb = me.getAbsorptionAmount();
        if (shownHealth < 0.0F) {
            shownHealth = health;
            trailHealth = health;
        }
        shownHealth += (health - shownHealth) * k;
        shownAbsorb += (absorb - shownAbsorb) * k;
        if (!config.hbTrail || health >= trailHealth) {
            trailHealth = health;
            trailHold = 0.4F;
        } else if (trailHold > 0.0F) {
            trailHold -= dt;
        } else {
            trailHealth += (health - trailHealth) * Math.min(1.0F, dt * 5.0F);
        }
        float whole = Math.max(max, health + absorb);
        int healthInk = SkinConfig.rgb(config.hbHealth, 0xFF4D5E);
        if (me.hasEffect(MobEffects.POISON)) healthInk = 0xFF8BC34A;
        else if (me.hasEffect(MobEffects.WITHER)) healthInk = 0xFF5C5560;
        else if (me.isFullyFrozen()) healthInk = 0xFF9FD8FF;
        if (config.hbPulse && health <= max * 0.25F) {
            float beat = 0.65F + 0.35F * (float) Math.sin(System.nanoTime() / 1.0E9 * 9.0);
            healthInk = Theme.mix(Theme.darken(healthInk, 0.35F), healthInk, beat);
        }
        bar(g, left, row, BAR_W, 5, back, pips);
        segment(g, left, row, BAR_W, 5, 0.0F, trailHealth / whole, TRAIL, pips);
        segment(g, left, row, BAR_W, 5, 0.0F, shownHealth / whole, healthInk, pips);
        if (shownAbsorb > 0.05F) {
            segment(g, left, row, BAR_W, 5, shownHealth / whole, (shownHealth + shownAbsorb) / whole,
                SkinConfig.rgb(config.hbAbsorb, 0xFFC940), pips);
        }

        // ---- armour, over the health ----
        float armor = me.getArmorValue();
        shownArmor += (armor - shownArmor) * k;
        if (armor > 0.0F) {
            bar(g, left, row - 4, BAR_W, 2, back, false);
            segment(g, left, row - 4, BAR_W, 2, 0.0F, Math.min(1.0F, shownArmor / 20.0F), SkinConfig.rgb(config.hbArmor, 0xA9C4E8), false);
        }

        // ---- food, unless a mount's hearts have its place ----
        Entity vehicle = me.getVehicle();
        boolean mounted = vehicle != null && LivingEntity.class.isInstance(vehicle) && ((LivingEntity) vehicle).showVehicleHealth();
        int food = me.getFoodData().getFoodLevel();
        if (!mounted) {
            if (shownFood < 0.0F) shownFood = food;
            shownFood += (food - shownFood) * k;
            int foodInk = me.hasEffect(MobEffects.HUNGER) ? 0xFF8DB360 : SkinConfig.rgb(config.hbHunger, 0xF2A33A);
            bar(g, right, row, BAR_W, 5, back, pips);
            // Fills from the right, as the drumsticks do.
            segment(g, right, row, BAR_W, 5, 1.0F - shownFood / 20.0F, 1.0F, foodInk, pips);
        }

        // ---- air, over the food ----
        int air = me.getAirSupply();
        int maxAir = me.getMaxAirSupply();
        boolean showAir = maxAir > 0 && (air < maxAir || me.isEyeInFluid(FluidTags.WATER));
        if (shownAir < 0.0F) shownAir = air;
        shownAir += (air - shownAir) * k;
        int numbersY = row - 10;
        if (showAir) {
            int airRow = mounted ? row : row - 4;
            bar(g, right, airRow, BAR_W, 2, back, false);
            segment(g, right, airRow, BAR_W, 2, 1.0F - Math.max(0.0F, shownAir) / maxAir, 1.0F, SkinConfig.rgb(config.hbAir, 0x5BC8FF), false);
        }
        if (armor > 0.0F || showAir) numbersY -= 4;

        if (config.hbNumbers) {
            String hp = String.format(Locale.ROOT, "%.0f", Math.ceil(health));
            if (absorb > 0.0F) hp = hp + " +" + (int) Math.ceil(absorb);
            if (armor > 0.0F) hp = hp + "  " + (int) armor + "a";
            small(g, font, hp, left, numbersY, healthInk, false);
            if (!mounted) {
                String hunger = food + (showAir ? "  " + Math.max(0, air * 10 / Math.max(1, maxAir)) + "o" : "");
                small(g, font, hunger, right + BAR_W, numbersY, SkinConfig.rgb(config.hbHunger, 0xF2A33A), true);
            }
        }
    }

    /** The empty bar, or ten empty pips. */
    private static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, int back, boolean pips) {
        if (!pips) {
            g.fill(x, y, x + w, y + h, back);
            return;
        }
        for (int i = 0; i < 10; i++) {
            int px = x + i * (w + 1) / 10;
            int pw = (w + 1) / 10 - 1;
            g.fill(px, y, px + pw, y + h, back);
        }
    }

    /** The bar filled from {@code from} to {@code to}, fractions of its width. */
    private static void segment(GuiGraphicsExtractor g, int x, int y, int w, int h, float from, float to, int ink, boolean pips) {
        float a = Math.max(0.0F, Math.min(1.0F, from));
        float b = Math.max(0.0F, Math.min(1.0F, to));
        if (b <= a) return;
        int x0 = x + Math.round(a * w);
        int x1 = x + Math.round(b * w);
        if (!pips) {
            g.fill(x0, y, x1, y + h, ink);
            g.fill(x0, y, x1, y + 1, Theme.lighten(ink, 0.3F));
            return;
        }
        for (int i = 0; i < 10; i++) {
            int px = x + i * (w + 1) / 10;
            int pw = (w + 1) / 10 - 1;
            int lo = Math.max(px, x0);
            int hi = Math.min(px + pw, x1);
            if (hi > lo) {
                g.fill(lo, y, hi, y + h, ink);
                g.fill(lo, y, hi, y + 1, Theme.lighten(ink, 0.3F));
            }
        }
    }

    /** A line of text at two thirds size, its left (or right) end at x. */
    private static void small(GuiGraphicsExtractor g, Font font, String text, int x, int y, int ink, boolean fromRight) {
        float s = 0.67F;
        float w = font.width(text) * s;
        g.pose().pushMatrix();
        g.pose().translate(fromRight ? x - w : (float) x, (float) y + 2.0F);
        g.pose().scale(s, s);
        g.text(font, text, 0, 0, ink, true);
        g.pose().popMatrix();
    }
}
