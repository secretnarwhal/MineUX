package com.mineux.core.gpu;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

/**
 * A program's numbers: its {@link Glsl#BLOCK} block written here by name, in std140, then sent
 * to a uniform buffer on the GPU once before each draw. A name the program does not have is
 * passed over, as a raw-GL uniform location of -1 was.
 */
public final class Uniforms {

    private final Glsl.Program program;
    private final ByteBuffer data;
    private final String label;
    private GpuBuffer gpu;

    Uniforms(Glsl.Program program, String label) {
        this.program = program;
        this.label = label;
        this.data = ByteBuffer.allocateDirect(Math.max(16, program.size)).order(ByteOrder.nativeOrder());
    }

    /** The bytes as they stand, for tests. */
    ByteBuffer bytes() {
        return data;
    }

    public void set(String name, float v) {
        int at = program.offset(name);
        if (at < 0) return;
        if (isInt(name)) data.putInt(at, (int) v);
        else data.putFloat(at, v);
    }

    public void set(String name, float x, float y) {
        int at = program.offset(name);
        if (at < 0) return;
        data.putFloat(at, x).putFloat(at + 4, y);
    }

    public void set(String name, float x, float y, float z) {
        int at = program.offset(name);
        if (at < 0) return;
        data.putFloat(at, x).putFloat(at + 4, y).putFloat(at + 8, z);
    }

    public void set(String name, float x, float y, float z, float w) {
        int at = program.offset(name);
        if (at < 0) return;
        data.putFloat(at, x).putFloat(at + 4, y).putFloat(at + 8, z).putFloat(at + 12, w);
    }

    public void setInt(String name, int v) {
        int at = program.offset(name);
        if (at < 0) return;
        if (isInt(name)) data.putInt(at, v);
        else data.putFloat(at, v);
    }

    /** A 3x3 matrix, column by column as JOML's {@code get(float[])} gives it; std140 pads each column to four. */
    public void setMat3(String name, float[] m) {
        int at = program.offset(name);
        if (at < 0) return;
        for (int c = 0; c < 3; c++) {
            for (int r = 0; r < 3; r++) data.putFloat(at + c * 16 + r * 4, m[c * 3 + r]);
        }
    }

    /** A 4x4 matrix, column by column. */
    public void setMat4(String name, float[] m) {
        int at = program.offset(name);
        if (at < 0) return;
        for (int i = 0; i < 16; i++) data.putFloat(at + i * 4, m[i]);
    }

    private boolean isInt(String name) {
        String type = program.type(name);
        return Objects.equals(type, "int") || Objects.equals(type, "bool") || Objects.equals(type, "uint");
    }

    /** Sends the numbers to the GPU; outside any render pass, before the draw that reads them. */
    void upload(CommandEncoder encoder) {
        if (program.size <= 0) return;
        if (gpu == null || gpu.isClosed()) {
            gpu = RenderSystem.getDevice().createBuffer(new Label(label), GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, program.size);
        }
        data.clear();
        encoder.writeToBuffer(gpu.slice(), data);
    }

    /** Hands the buffer to a pass whose pipeline is this program's. */
    void bind(RenderPass pass) {
        if (program.size > 0 && gpu != null) pass.setUniform(Glsl.BLOCK, gpu);
    }
}
