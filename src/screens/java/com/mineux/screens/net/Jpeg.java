package com.mineux.screens.net;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;

/**
 * Shared frames as JPEG, with the JDK's own codec (javax.imageio): RGBA8 in, scaled down to a
 * width cap with a 2x2 box filter, JPEG out; JPEG in, RGBA8 (alpha 255) out. Off the render
 * thread only. One encoder per thread ({@link Encoder}).
 */
public final class Jpeg {

    private Jpeg() {}

    /** The size a frame of {@code w}x{@code h} is sent at, at most {@code maxWidth} wide; even sides. */
    public static int scaledWidth(int w, int maxWidth) {
        return Math.max(2, Math.min(w, maxWidth) & ~1);
    }

    public static int scaledHeight(int w, int h, int maxWidth) {
        int sw = scaledWidth(w, maxWidth);
        return Math.max(2, (int) Math.round((double) h * sw / w) & ~1);
    }

    /** A reusable JPEG writer and its image. Not thread-safe: one per encoding thread. */
    public static final class Encoder {
        private final ImageWriter writer;
        private BufferedImage image;

        public Encoder() {
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
            if (!writers.hasNext()) throw new IllegalStateException("no JPEG writer in this JDK");
            writer = writers.next();
        }

        /** Tightly packed RGBA8 {@code src} of w x h, scaled to at most {@code maxWidth} wide, as JPEG. */
        public byte[] encode(ByteBuffer src, int w, int h, int maxWidth, float quality) throws IOException {
            int tw = scaledWidth(w, maxWidth);
            int th = scaledHeight(w, h, maxWidth);
            if (image == null || image.getWidth() != tw || image.getHeight() != th) {
                image = new BufferedImage(tw, th, BufferedImage.TYPE_3BYTE_BGR);
            }
            byte[] out = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
            scale(src, w, h, out, tw, th);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(64 * 1024);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(Math.clamp(quality, 0.05f, 1.0f));
            try (MemoryCacheImageOutputStream stream = new MemoryCacheImageOutputStream(bytes)) {
                writer.setOutput(stream);
                writer.write(null, new IIOImage(image, null, null), param);
            } finally {
                writer.reset();
            }
            return bytes.toByteArray();
        }
    }

    /** RGBA {@code src} (w x h) into BGR {@code dst} (tw x th): each pixel the mean of a 2x2 block. */
    static void scale(ByteBuffer src, int w, int h, byte[] dst, int tw, int th) {
        int o = 0;
        for (int ty = 0; ty < th; ty++) {
            int y0 = (int) ((long) ty * h / th);
            int y1 = Math.min(h - 1, y0 + (h > th ? 1 : 0));
            for (int tx = 0; tx < tw; tx++) {
                int x0 = (int) ((long) tx * w / tw);
                int x1 = Math.min(w - 1, x0 + (w > tw ? 1 : 0));
                int a = (y0 * w + x0) * 4;
                int b = (y0 * w + x1) * 4;
                int c = (y1 * w + x0) * 4;
                int d = (y1 * w + x1) * 4;
                dst[o++] = mean(src, a + 2, b + 2, c + 2, d + 2);
                dst[o++] = mean(src, a + 1, b + 1, c + 1, d + 1);
                dst[o++] = mean(src, a, b, c, d);
            }
        }
    }

    private static byte mean(ByteBuffer s, int a, int b, int c, int d) {
        int sum = (s.get(a) & 0xFF) + (s.get(b) & 0xFF) + (s.get(c) & 0xFF) + (s.get(d) & 0xFF);
        return (byte) ((sum + 2) >> 2);
    }

    /** Decodes {@code jpeg}; null when it is not one. */
    public static BufferedImage decode(byte[] jpeg) {
        try {
            return ImageIO.read(new ByteArrayInputStream(jpeg));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** {@code image} as tightly packed RGBA8, alpha 255, into {@code dst} (w*h*4 bytes from its position 0). */
    public static void toRgba(BufferedImage image, ByteBuffer dst) {
        int w = image.getWidth();
        int h = image.getHeight();
        int[] row = new int[w];
        int o = 0;
        for (int y = 0; y < h; y++) {
            image.getRGB(0, y, w, 1, row, 0, w);
            for (int x = 0; x < w; x++) {
                int p = row[x];
                dst.put(o, (byte) (p >> 16));
                dst.put(o + 1, (byte) (p >> 8));
                dst.put(o + 2, (byte) p);
                dst.put(o + 3, (byte) 0xFF);
                o += 4;
            }
        }
    }
}
