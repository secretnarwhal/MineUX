package com.mineux.visuals.hand;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Inspect: a press of its key (or a slot switch, if asked) plays a knife-style inspect of the
 * item in your main hand -- the arm lifts and turns, the item flips, spins or is tossed -- and
 * eases back. Swinging, using or switching away cuts it short with a quick fade. Drawn only.
 * <p>
 * The keyframes are {@code mineux/inspect.txt}: per key, its time, the hand's move and turn
 * and the item's move and turn, smoothed between keys by a Hermite curve.
 */
public final class Inspect {

    private static final Logger LOGGER = LoggerFactory.getLogger("mineux/hand");
    private static final String RESOURCE = "/mineux/inspect.txt";

    /** The choices: auto picks by the item, random any; then the animations in inspect.txt. */
    private static final List<String> ANIMS = Arrays.asList(
        "auto", "random", "karambit", "butterfly", "flip", "twirl", "showcase", "huntsman", "toss", "spin", "gut", "bayonet");
    /** Numbers per key: the time, then six for the hand and six for the item. */
    private static final int ROW = 13;
    private static final int CH = 12;
    private static final float FADE_S = 0.18f;

    /** inspect.txt's keys for each of {@link #ANIMS}, null where it has none. */
    private static List<float[]> table;
    private static final Random RANDOM = new Random();

    private static float[] keys;
    private static float duration = 1.6f;
    private static long startNanos;
    private static long fadeStart;
    private static final float[] FADE_FROM = new float[CH];
    private static final float[] CUR = new float[CH];
    private static long sampledAt = 0 - 1L;
    private static Item inspected;
    private static int inspectedSlot = 0 - 1;
    private static int lastSlot = 0 - 1;
    private static Item lastItem;

    private Inspect() {}

    static int animIndex(String name) {
        return ANIMS.indexOf(name);
    }

    /** The inspect key: plays the animation on what is in your main hand. */
    public static void press() {
        HandConfig config = HandConfig.get();
        Minecraft mc = Minecraft.getInstance();
        if (!config.inspect || mc.player == null) return;
        start(config, mc.player.getMainHandItem(), mc.player.getInventory().getSelectedSlot());
    }

    /** Once a frame, from the main hand: a switch starts one, a switch away or a swing ends one. */
    static void frame(HandConfig c, AbstractClientPlayer player, ItemStack held) {
        if (!c.inspect) {
            startNanos = 0L;
            fadeStart = 0L;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int slot = player.getInventory().getSelectedSlot();
        boolean switched = slot != lastSlot || lastItem != null && held.getItem() != lastItem;
        lastSlot = slot;
        lastItem = held.getItem();
        if (startNanos != 0L) {
            boolean away = slot != inspectedSlot || held.getItem() != inspected;
            boolean busy = c.inspectCancel && (mc.options.keyAttack.isDown() || mc.options.keyUse.isDown());
            if (away || busy) cancel();
        }
        if (switched && c.inspectOnSwitch && !held.isEmpty()) start(c, held, slot);
    }

    private static void start(HandConfig c, ItemStack held, int slot) {
        if (held.isEmpty() && !c.inspectEmpty) return;
        int anim = Math.max(0, ANIMS.indexOf(c.inspectAnim));
        if (anim == 1) anim = 2 + RANDOM.nextInt(ANIMS.size() - 2);
        else if (anim == 0) anim = auto(held);
        float[] k = frames(anim);
        if (k == null) return;
        keys = k;
        duration = (float) Math.max(0.3, c.inspectDuration / Math.max(0.1, c.inspectSpeed));
        inspected = held.getItem();
        inspectedSlot = slot;
        startNanos = System.nanoTime();
        fadeStart = 0L;
    }

    /** Cut short: from wherever it is, back to rest over a moment. */
    private static void cancel() {
        sample(HandConfig.get());
        for (int i = 0; i < CH; i++) {
            boolean angle = i >= 3 && i < 6 || i >= 9;
            FADE_FROM[i] = angle ? wrap(CUR[i]) : CUR[i];
        }
        startNanos = 0L;
        fadeStart = System.nanoTime();
    }

    static boolean playing(HandConfig c) {
        return c.inspect && (startNanos != 0L || fadeStart != 0L);
    }

    static boolean hidesOffHand(HandConfig c) {
        return c.inspectHideOff && playing(c);
    }

    /** The hand's part: the arm moved and turned about its own place. */
    static void applyHand(HandConfig c, PoseStack pose, float side, float equip) {
        if (!playing(c)) return;
        float[] v = sample(c);
        if (allZero(v, 0, 6)) return;
        float ax = side * 0.56f;
        float ay = 0f - 0.52f - equip * 0.6f;
        float az = 0f - 0.72f;
        pose.translate(ax + side * v[0], ay + v[1], az + v[2]);
        pose.mulPose(Axis.YP.rotationDegrees(side * v[4]));
        pose.mulPose(Axis.XP.rotationDegrees(v[3]));
        pose.mulPose(Axis.ZP.rotationDegrees(side * v[5]));
        pose.translate(0f - ax, 0f - ay, 0f - az);
    }

    /** The item's part: moved and turned in the hand. */
    static void applyItem(HandConfig c, PoseStack pose, boolean main) {
        if (!main || !playing(c)) return;
        float[] v = sample(c);
        if (allZero(v, 6, 12)) return;
        float side = side();
        pose.translate(side * v[6], v[7], v[8]);
        pose.mulPose(Axis.ZP.rotationDegrees(side * v[11]));
        pose.mulPose(Axis.XP.rotationDegrees(v[9]));
        pose.mulPose(Axis.YP.rotationDegrees(side * v[10]));
    }

    /** Where the animation is now, worked out once a frame. */
    private static float[] sample(HandConfig c) {
        long now = System.nanoTime();
        if (now == sampledAt) return CUR;
        sampledAt = now;
        Arrays.fill(CUR, 0f);
        if (startNanos != 0L && keys != null) {
            float t = Math.min(1f, (now - startNanos) / 1.0e9f / duration);
            if (t >= 1f) {
                startNanos = 0L;
                return CUR;
            }
            float s = (float) c.inspectSmooth;
            interpolate(keys, t * (1f - s) + smooth(t) * s, CUR);
        } else if (fadeStart != 0L) {
            float f = (now - fadeStart) / 1.0e9f / FADE_S;
            if (f >= 1f) {
                fadeStart = 0L;
                return CUR;
            }
            float w = 1f - smooth(f);
            for (int i = 0; i < CH; i++) CUR[i] = FADE_FROM[i] * w;
        }
        float amount = (float) c.inspectIntensity;
        if (amount != 1f) for (int i = 0; i < 6; i++) CUR[i] *= amount;
        return CUR;
    }

    /** Hermite between the two keys either side of t, with tangents from the keys beyond. */
    private static void interpolate(float[] k, float t, float[] out) {
        int n = k.length / ROW;
        int i = 0;
        while (i < n - 2 && t > k[(i + 1) * ROW]) i++;
        int a = i * ROW, b = a + ROW;
        float t0 = k[a], t1 = k[b];
        float u = t1 > t0 ? Math.max(0f, Math.min(1f, (t - t0) / (t1 - t0))) : 1f;
        float u2 = u * u, u3 = u2 * u;
        float h00 = 2f * u3 - 3f * u2 + 1f;
        float h10 = u3 - 2f * u2 + u;
        float h01 = 3f * u2 - 2f * u3;
        float h11 = u3 - u2;
        float dt = t1 - t0;
        boolean before = i > 0, after = i + 2 < n;
        int pa = a - ROW, nb = b + ROW;
        for (int ch = 1; ch <= CH; ch++) {
            float va = k[a + ch], vb = k[b + ch];
            float ma = before ? (vb - k[pa + ch]) / Math.max(1.0e-4f, k[b] - k[pa]) * dt : 0f;
            float mb = after ? (k[nb + ch] - va) / Math.max(1.0e-4f, k[nb] - k[a]) * dt : 0f;
            out[ch - 1] = h00 * va + h10 * ma + h01 * vb + h11 * mb;
        }
    }

    /** An animation for the item: knives for swords, heavier for axes and maces, a showing-off for bows. */
    private static int auto(ItemStack st) {
        String id = String.valueOf(BuiltInRegistries.ITEM.getKey(st.getItem())).toLowerCase(Locale.ROOT);
        boolean coin = RANDOM.nextBoolean();
        if (id.contains("sword") || id.contains("spear") || id.contains("dagger") || id.contains("knife")) {
            return ANIMS.indexOf(coin ? "karambit" : "butterfly");
        }
        if (id.contains("_axe") || id.contains("mace") || id.contains("trident")) return ANIMS.indexOf(coin ? "huntsman" : "gut");
        if (id.contains("bow") || id.contains("shield")) return ANIMS.indexOf("showcase");
        if (id.contains("pickaxe") || id.contains("shovel") || id.contains("hoe")) return ANIMS.indexOf("twirl");
        return ANIMS.indexOf(BlockItem.class.isInstance(st.getItem()) ? "toss" : "flip");
    }

    private static synchronized float[] frames(int anim) {
        if (table == null) table = load();
        return anim >= 0 && anim < table.size() ? table.get(anim) : null;
    }

    /** inspect.txt, as one flat array of keys per animation. */
    private static List<float[]> load() {
        List<float[]> out = new ArrayList<>();
        String text = "";
        try (InputStream in = Inspect.class.getResourceAsStream(RESOURCE)) {
            if (in != null) text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Could not read {}", RESOURCE, e);
        }
        List<List<String>> rows = new ArrayList<>();
        for (int i = 0; i < ANIMS.size(); i++) rows.add(new ArrayList<>());
        for (String line : text.split("\n")) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length != ROW + 1 || parts[0].startsWith("#")) continue;
            int at = ANIMS.indexOf(parts[0]);
            if (at >= 2) rows.get(at).add(line.trim());
        }
        for (int i = 0; i < ANIMS.size(); i++) out.add(i < 2 ? null : parse(rows.get(i)));
        return out;
    }

    private static float[] parse(List<String> lines) {
        if (lines.size() < 2) return null;
        float[] k = new float[lines.size() * ROW];
        for (int r = 0; r < lines.size(); r++) {
            String[] parts = lines.get(r).split("\\s+");
            for (int c = 0; c < ROW; c++) k[r * ROW + c] = Float.parseFloat(parts[c + 1]);
        }
        return k;
    }

    private static float side() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getMainArm() == HumanoidArm.LEFT ? 0f - 1f : 1f;
    }

    private static float wrap(float deg) {
        float d = deg % 360f;
        if (d > 180f) d -= 360f;
        if (d < 0f - 180f) d += 360f;
        return d;
    }

    private static float smooth(float t) {
        return t * t * (3f - 2f * t);
    }

    private static boolean allZero(float[] v, int from, int to) {
        for (int i = from; i < to; i++) if (Math.abs(v[i]) > 1.0e-4f) return false;
        return true;
    }
}
