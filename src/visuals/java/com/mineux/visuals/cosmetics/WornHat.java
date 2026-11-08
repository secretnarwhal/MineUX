package com.mineux.visuals.cosmetics;

import com.mineux.visuals.cosmetics.hats.HatParams;
import com.mineux.visuals.cosmetics.hats.Hats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;

/** The hat on your head: ears, horns, a halo, a crown or the china hat. */
final class WornHat {

    private WornHat() {}

    static void draw(Minecraft mc, LocalPlayer me, float partialTick, CosmeticsConfig config) {
        int index = Hats.NAMES.indexOf(config.hat);
        if (index <= 0) return;
        int c1 = Cosmetics.color(config);
        int c2 = Cosmetics.color2(config);
        boolean helmet = !me.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
        HatParams params = new HatParams(new int[] {0xFF000000 | c1, 0xFF000000 | c2}, 1.0, 0.4, 0.0 - 0.2, 0.15, helmet, config.animate);
        Cosmetic hat = Hats.create(index, params);
        if (hat == null) return;
        Attach.Subject subject = Cosmetics.subject(me, partialTick, 1.0);
        Attach.Frame mount = Attach.frame(Attach.HEAD, subject).shifted(0.0, Attach.HEAD_TOP + config.hatHeight, 0.0);
        QuadBatch batch = new QuadBatch();
        Builder b = Cosmetics.builder(mc, batch, c1, c2, config, Cosmetics.seconds());
        b.frame(mount, config.scale);
        hat.build(b);
        batch.submit(false);
    }
}
