package com.mineux.visuals.self.model;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The render types the player model and the armour draw with. They run the game's own shaders --
 * except the armour's, which bring their own -- in pipelines of the client's, built here from the
 * game's public pieces, never registered with the game (so a resource reload has nothing of ours
 * to check), and compiled the first time they are drawn.
 */
public final class Types {

    private static Method create;
    private static RenderType rig;
    private static RenderType rigWalls;
    private static RenderPipeline entityWalls;
    private static final Map<Identifier, RenderType> WALLS = new HashMap<>();

    private Types() {}

    /** {@code RenderType.create}, which the game keeps to its own package. */
    public static RenderType create(String name, RenderSetup setup) throws ReflectiveOperationException {
        if (create == null) {
            Method m = RenderType.class.getDeclaredMethod("create", String.class, RenderSetup.class);
            m.setAccessible(true);
            create = m;
        }
        return (RenderType) create.invoke(null, name, setup);
    }

    /** Flat-coloured quads, back faces culled, depth tested but not written: the rig's outlines need all three. */
    static RenderType rig(boolean walls) throws ReflectiveOperationException {
        if (walls ? rigWalls == null : rig == null) {
            RenderPipeline pipe = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("mineux", walls ? "pipeline/self_rig_walls" : "pipeline/self_rig"))
                .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
                .withVertexShader("core/position_color")
                .withFragmentShader("core/position_color")
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withDepthStencilState(walls ? Optional.empty() : Optional.of(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false)))
                .withCull(true)
                .build();
            RenderType type = create(walls ? "mineux_rig_walls" : "mineux_rig", RenderSetup.builder(pipe)
                .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                .createRenderSetup());
            if (walls) rigWalls = type;
            else rig = type;
        }
        return walls ? rigWalls : rig;
    }

    /** The game's translucent entity type, or a copy of it drawn through walls. */
    static RenderType entity(Identifier texture, boolean walls) throws ReflectiveOperationException {
        if (!walls) return RenderTypes.entityTranslucent(texture);
        RenderType type = WALLS.get(texture);
        if (type != null) return type;
        if (entityWalls == null) {
            entityWalls = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("mineux", "pipeline/self_model_walls"))
                .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.FOG)
                .withBindGroupLayout(BindGroupLayouts.LIGHTING)
                .withVertexShader("core/entity")
                .withFragmentShader("core/entity")
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER2)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
                .withShaderDefine("ALPHA_CUTOUT", 0.1F)
                .withShaderDefine("PER_FACE_LIGHTING")
                .withVertexBinding(0, DefaultVertexFormat.ENTITY)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(Optional.empty())
                .withCull(false)
                .build();
        }
        type = create("mineux_model_walls", RenderSetup.builder(entityWalls)
            .withTexture("Sampler0", texture)
            .useLightmap()
            .useOverlay()
            .sortOnUpload()
            .createRenderSetup());
        WALLS.put(texture, type);
        return type;
    }
}
