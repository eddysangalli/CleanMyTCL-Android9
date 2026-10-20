package com.gathin.tvhome;

import android.graphics.Bitmap;

/**
 * BlurHash decoder (algorithm and alphabet from github.com/woltapp/blurhash, MIT). A ~30-character string
 * becomes a tiny bitmap of soft colour; drawn stretched with Android's bitmap filtering it reads as a blurred
 * photo, at no APK size and no decode cost worth measuring.
 */
final class BlurHash {

    private static final String CHARS =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~";

    private BlurHash() {
    }

    static Bitmap decode(String hash, int width, int height) {
        int size = decode83(hash, 0, 1);
        int numX = size % 9 + 1, numY = size / 9 + 1;
        float maxAc = (decode83(hash, 1, 2) + 1) / 166f;
        float[][] colors = new float[numX * numY][];
        int dc = decode83(hash, 2, 6);
        colors[0] = new float[]{toLinear(dc >> 16), toLinear((dc >> 8) & 255), toLinear(dc & 255)};
        for (int i = 1; i < colors.length; i++) {
            int ac = decode83(hash, 4 + i * 2, 6 + i * 2);
            colors[i] = new float[]{
                    signPow((ac / (19 * 19) - 9) / 9f, 2) * maxAc,
                    signPow((ac / 19 % 19 - 9) / 9f, 2) * maxAc,
                    signPow((ac % 19 - 9) / 9f, 2) * maxAc};
        }
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float r = 0, g = 0, b = 0;
                for (int j = 0; j < numY; j++) {
                    for (int i = 0; i < numX; i++) {
                        float basis = (float) (Math.cos(Math.PI * x * i / width) * Math.cos(Math.PI * y * j / height));
                        float[] c = colors[i + j * numX];
                        r += c[0] * basis;
                        g += c[1] * basis;
                        b += c[2] * basis;
                    }
                }
                pixels[x + y * width] = 0xFF000000 | toSrgb(r) << 16 | toSrgb(g) << 8 | toSrgb(b);
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
    }

    private static int decode83(String s, int from, int to) {
        int value = 0;
        for (int i = from; i < to; i++) value = value * 83 + CHARS.indexOf(s.charAt(i));
        return value;
    }

    private static float toLinear(int v) {
        float c = v / 255f;
        return c <= 0.04045f ? c / 12.92f : (float) Math.pow((c + 0.055f) / 1.055f, 2.4f);
    }

    private static int toSrgb(float v) {
        float c = Math.max(0f, Math.min(1f, v));
        return Math.round((c <= 0.0031308f ? c * 12.92f : (float) (1.055f * Math.pow(c, 1 / 2.4f) - 0.055f)) * 255);
    }

    private static float signPow(float v, float exp) {
        return Math.copySign((float) Math.pow(Math.abs(v), exp), v);
    }
}
