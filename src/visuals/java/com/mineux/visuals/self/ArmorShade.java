package com.mineux.visuals.self;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mineux.visuals.self.model.Types;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Armour shaders: a player's armour drawn in a material of its own -- glass, crystal, hologram,
 * molten, void, chrome, neon wire or frost -- in the material's colour or one you pick, as see-through
 * as you like; yours, everyone else's, or both. Your screen only.
 * <p>
 * The armour layer draws each piece with a cutout armour render type and a tint. For a player
 * whose armour is shaded, the piece's texture goes into one of this client's render types instead,
 * whose pipeline runs the material's shader ({@link ArmorGlsl}) over the armour's own texture, and
 * the tint becomes the material's colour and opacity. Glints and trims are left as they are.
 */
final class ArmorShade {

    static final List<String> MATERIALS = Arrays.asList("glass", "crystal", "hologram", "molten", "void", "chrome", "neon", "frost");
    private static final int[] NATURAL = {0xBFE6FF, 0xB07CFF, 0x4CF2FF, 0xFF6A1A, 0x9A4DFF, 0xD8E4FF, 0x33FF99, 0xC2EAFF};

    private static final RenderPipeline[] PIPELINES = new RenderPipeline[MATERIALS.size()];
    /** Per texture, the render type of each material, made the first time it is drawn. */
    private static final Map<Identifier, RenderType[]> TYPES = new HashMap<>();

    /** The armour piece being drawn now: its material (-1 for the game's own), tint and texture. */
    private static int material = 0 - 1;
    private static int tint;
    private static Identifier texture;

    private static boolean broken;

    private ArmorShade() {}

    static int materialIndex(String name) {
        return MATERIALS.indexOf(name);
    }

    /** Head of the armour layer, for whichever entity it is drawing. */
    static void begin(Object state) {
        material = 0 - 1;
        texture = null;
        SelfConfig config = SelfConfig.get();
        if (!config.armor || broken || !AvatarRenderState.class.isInstance(state)) return;
        LocalPlayer me = Minecraft.getInstance().player;
        boolean self = me != null && ((AvatarRenderState) state).id == me.getId();
        if (self ? !config.armorSelf : !config.armorOthers) return;
        int m = materialIndex(config.armorMaterial);
        if (m < 0) return;
        int rgb = config.armorNatural ? NATURAL[m] : SelfConfig.rgb(config.armorColor, NATURAL[m]);
        int alpha = Math.max(1, Math.round(config.armorOpacity * 255.0F));
        material = m;
        tint = (alpha << 24) | rgb;
    }

    static void texture(Object id) {
        texture = Identifier.class.isInstance(id) ? (Identifier) id : null;
    }

    /** The piece's render type: the material's, while a shaded player's armour is drawn. */
    static Object type(Object vanilla) {
        if (material < 0 || texture == null) return vanilla;
        RenderType shaded = shaded(texture, material);
        return shaded == null ? vanilla : shaded;
    }

    static Object tint(Object vanilla) {
        if (material < 0 || texture == null || broken) return vanilla;
        return Integer.valueOf(tint);
    }

    private static RenderType shaded(Identifier tex, int m) {
        try {
            if (!ready(m)) return null;
            RenderType[] row = TYPES.get(tex);
            if (row == null) {
                row = new RenderType[MATERIALS.size()];
                TYPES.put(tex, row);
            }
            if (row[m] == null) {
                RenderSetup setup = RenderSetup.builder(PIPELINES[m])
                    .withTexture("Sampler0", tex)
                    .useLightmap()
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .sortOnUpload()
                    .createRenderSetup();
                row[m] = Types.create("mineux_armor_" + MATERIALS.get(m), setup);
            }
            return row[m];
        } catch (Exception | LinkageError e) {
            broken = true;
            material = 0 - 1;
            org.slf4j.LoggerFactory.getLogger("mineux/self").warn("armour shaders off: {}", e.toString());
            return null;
        }
    }

    /**
     * The material's pipeline, compiled from the client's own source; false if it will not
     * compile, which turns the shaders off rather than drawing the armour with nothing.
     */
    static boolean ready(int m) {
        if (PIPELINES[m] == null) {
            PIPELINES[m] = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("mineux", "pipeline/armor_" + MATERIALS.get(m)))
                .withVertexShader(ArmorGlsl.SHADER)
                .withFragmentShader(ArmorGlsl.SHADER)
                .withShaderDefine("MATERIAL", m)
                .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER2)
                .withVertexBinding(0, DefaultVertexFormat.ENTITY)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withDepthStencilState(DepthStencilState.DEFAULT)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withCull(false)
                .build();
        }
        if (RenderSystem.getDevice().precompilePipeline(PIPELINES[m], ArmorGlsl.INSTANCE).isValid()) return true;
        broken = true;
        org.slf4j.LoggerFactory.getLogger("mineux/self").warn("armour shader {} would not compile; armour shaders off", MATERIALS.get(m));
        return false;
    }
}
