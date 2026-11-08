package com.mineux.visuals.cosmetics;

import com.mojang.blaze3d.platform.NativeImage;

/**
 * Makes a cape texture out of any picture. A picture twice as wide as it is tall (64x32, 128x64,
 * 512x256...) is taken as a cape texture already, laid out like the built-in ones. Anything else
 * (a portrait, a screenshot) is cropped to fill the cape's back and both elytra wings, its middle
 * kept and its top favoured, with the edges and lining in its own darkened colour.
 */
final class CapeFit {

    /** Pixels a cape-layout unit (the 64x32 grid) in a made texture: 512x256. */
    private static final int S = 8;

    private CapeFit() {}

    /** {@code image} itself when it is a cape texture already, else a new one made from it (and {@code image} closed). */
    static NativeImage fit(NativeImage image) {
        if (image.getWidth() == image.getHeight() * 2 && image.getWidth() >= 64) return image;
        NativeImage out = new NativeImage(64 * S, 32 * S, true);
        int mean = mean(image);
        int edge = 0xFF000000 | scale(mean, 55);
        int lining = 0xFF000000 | scale(mean, 40);
        paintEdges(out, edge, lining);
        cover(image, out, 1, 1, 10, 16, false, lining);
        cover(image, out, 36, 2, 10, 20, false, lining);
        cover(image, out, 24, 2, 10, 20, true, lining);
        image.close();
        return out;
    }

    /** The cape's top, bottom, sides and lining, and the elytra's top and sides. */
    private static void paintEdges(NativeImage out, int edge, int lining) {
        out.fillRect(S, 0, 20 * S, S, edge);
        out.fillRect(0, S, S, 16 * S, edge);
        out.fillRect(11 * S, S, S, 16 * S, edge);
        out.fillRect(12 * S, S, 10 * S, 16 * S, lining);
        out.fillRect(22 * S, 0, 24 * S, 2 * S, edge);
        out.fillRect(22 * S, 2 * S, 2 * S, 20 * S, edge);
        out.fillRect(34 * S, 2 * S, 2 * S, 20 * S, edge);
    }

    /** Fills the unit box at ({@code ux},{@code uy}), {@code uw}x{@code uh} units, with the picture cropped to fit it. */
    private static void cover(NativeImage src, NativeImage out, int ux, int uy, int uw, int uh, boolean mirror, int under) {
        int w = uw * S, h = uh * S;
        double sw = src.getWidth(), sh = src.getHeight();
        double fit = Math.max(w / sw, h / sh);
        double cw = w / fit, ch = h / fit;
        double left = (sw - cw) * 0.5, top = (sh - ch) * 0.25;
        double bw = cw / w, bh = ch / h;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int from = mirror ? w - 1 - x : x;
                int argb = sample(src, left + from * bw, top + y * bh, bw, bh);
                out.setPixel(ux * S + x, uy * S + y, over(argb, under));
            }
        }
    }

    /** The mean of the picture's pixels in the box at (x, y), bw by bh: a few samples across it. */
    private static int sample(NativeImage src, double x, double y, double bw, double bh) {
        int n = (int) Math.max(1.0, Math.min(4.0, Math.ceil(Math.max(bw, bh))));
        int maxX = src.getWidth() - 1, maxY = src.getHeight() - 1;
        long a = 0, r = 0, g = 0, b = 0;
        for (int j = 0; j < n; j++) {
            int py = Math.min(maxY, Math.max(0, (int) (y + (j + 0.5) * bh / n)));
            for (int i = 0; i < n; i++) {
                int px = Math.min(maxX, Math.max(0, (int) (x + (i + 0.5) * bw / n)));
                int c = src.getPixel(px, py);
                a += (c >>> 24) & 0xFF;
                r += (c >>> 16) & 0xFF;
                g += (c >>> 8) & 0xFF;
                b += c & 0xFF;
            }
        }
        long count = (long) n * n;
        return (int) (a / count) << 24 | (int) (r / count) << 16 | (int) (g / count) << 8 | (int) (b / count);
    }

    /** The whole picture's mean colour, opaque pixels only, from a grid of samples. */
    private static int mean(NativeImage src) {
        long r = 0, g = 0, b = 0, count = 0;
        int stepX = Math.max(1, src.getWidth() / 32), stepY = Math.max(1, src.getHeight() / 32);
        for (int y = 0; y < src.getHeight(); y += stepY) {
            for (int x = 0; x < src.getWidth(); x += stepX) {
                int c = src.getPixel(x, y);
                if (((c >>> 24) & 0xFF) < 128) continue;
                r += (c >>> 16) & 0xFF;
                g += (c >>> 8) & 0xFF;
                b += c & 0xFF;
                count++;
            }
        }
        if (count == 0) return 0x303040;
        return (int) (r / count) << 16 | (int) (g / count) << 8 | (int) (b / count);
    }

    /** {@code rgb} at {@code percent} brightness. */
    static int scale(int rgb, int percent) {
        int r = ((rgb >>> 16) & 0xFF) * percent / 100;
        int g = ((rgb >>> 8) & 0xFF) * percent / 100;
        int b = (rgb & 0xFF) * percent / 100;
        return r << 16 | g << 8 | b;
    }

    /** {@code argb} laid over the opaque {@code under}: the cape is drawn solid, so see-through parts show the lining colour. */
    static int over(int argb, int under) {
        int a = (argb >>> 24) & 0xFF;
        int r = mixByte((argb >>> 16) & 0xFF, (under >>> 16) & 0xFF, a);
        int g = mixByte((argb >>> 8) & 0xFF, (under >>> 8) & 0xFF, a);
        int b = mixByte(argb & 0xFF, under & 0xFF, a);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int mixByte(int top, int bottom, int alpha) {
        return (top * alpha + bottom * (255 - alpha)) / 255;
    }
}
