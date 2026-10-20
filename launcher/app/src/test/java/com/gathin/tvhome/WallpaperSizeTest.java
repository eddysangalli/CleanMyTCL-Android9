package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The wallpaper is decoded at the size the screen shows it (#277). The bundled pictures are 1920x1080
 * and the BeyondTV2 is 1920x1080, so on this TV nothing is sampled or scaled; these cases pin the
 * sizes where something is, the sizes that are refused, and what each one costs in RAM.
 */
public class WallpaperSizeTest {

    /** What a decoded bitmap of this size costs in RAM, at RGB_565 and no alpha. */
    private static long bytes(int width, int height) {
        return (long) width * height * 2L;
    }

    @Test public void the_bundled_wallpaper_is_decoded_untouched_on_1080p() {
        assertTrue(WallpaperSize.accepted(1920, 1080));
        assertEquals(1, WallpaperSize.sampleSize(1920, 1080, 1920, 1080));
        assertNull(WallpaperSize.coverScale(1920, 1080, 1920, 1080));
        assertEquals(4_147_200L, bytes(1920, 1080));
    }

    /** A wallpaper smaller than the screen is upscaled by centerCrop, not decoded bigger for it. */
    @Test public void a_small_picture_is_not_scaled_up() {
        assertEquals(1, WallpaperSize.sampleSize(1280, 720, 1920, 1080));
        assertNull(WallpaperSize.coverScale(1280, 720, 1920, 1080));
    }

    /** 4K on a 1080p screen: one halving in each direction, a quarter of the pixels, a quarter of the RAM. */
    @Test public void four_k_on_1080p_samples_by_two() {
        assertTrue(WallpaperSize.accepted(3840, 2160));
        assertEquals(2, WallpaperSize.sampleSize(3840, 2160, 1920, 1080));
        assertEquals(4, WallpaperSize.sampleSize(7680, 4320, 1920, 1080));
        // The same picture, at the size the screen shows it.
        assertEquals(bytes(1920, 1080), bytes(3840 / 2, 2160 / 2));
        assertEquals(bytes(1920, 1080), bytes(7680 / 4, 4320 / 4));
        assertEquals(4_147_200L, bytes(3840 / 2, 2160 / 2));
        // What a plain decode of the 4K picture would have cost.
        assertEquals(16_588_800L, bytes(3840, 2160));
    }

    /** Between two sample sizes there is nothing to halve, so the decoder is asked to scale instead. */
    @Test public void photo_between_sample_sizes_is_scaled_to_the_screen() {
        // 3000x2000 keeps sample size 1; the cover is limited by the width, so it decodes 1920 wide.
        assertTrue(WallpaperSize.accepted(3000, 2000));
        assertEquals(1, WallpaperSize.sampleSize(3000, 2000, 1920, 1080));
        assertArrayEquals(new int[]{3000, 1920}, WallpaperSize.coverScale(3000, 2000, 1920, 1080));
    }

    /** A picture wider than the screen is cropped sideways, so its height is what has to come down. */
    @Test public void wide_picture_is_scaled_by_its_height() {
        // 4000x2000 into 1920x1080: centerCrop scales by 1080/2000, so 1080 is the screen height it
        // lands on and the decode is scaled to it. The extra width is cropped, not decoded for.
        assertArrayEquals(new int[]{2000, 1080}, WallpaperSize.coverScale(4000, 2000, 1920, 1080));
    }

    /** The other side of the same rule: a tall picture is limited by its width, and 1920 already is it. */
    @Test public void tall_picture_is_not_scaled_when_it_is_already_screen_wide() {
        // 1920x2000 into 1920x1080: centerCrop draws it 1:1 and crops the height, so the decode keeps
        // the full width and there is nothing to scale.
        assertNull(WallpaperSize.coverScale(1920, 2000, 1920, 1080));
        // 3000x4000 is limited by its width instead, and that one is scaled.
        assertArrayEquals(new int[]{3000, 1920}, WallpaperSize.coverScale(3000, 4000, 1920, 1080));
    }

    /** Upstream #166: a very wide picture kept sample size 1, which is about 283 MB of decode buffer. */
    @Test public void a_very_wide_picture_is_refused() {
        assertEquals(282_980_130L, bytes(65535, 2159));
        assertFalse(WallpaperSize.accepted(65535, 2159));
    }

    /** 4K is the last size that goes through; anything over 8.4 M pixels is not decoded at all. */
    @Test public void the_cap_sits_a_little_above_4k() {
        assertTrue(WallpaperSize.accepted(4096, 2048));
        assertFalse(WallpaperSize.accepted(5000, 2160));
    }

    @Test public void an_unreadable_picture_is_refused() {
        // BitmapFactory reports -1 when it cannot read the header, and 0 for a zero-sized image.
        assertFalse(WallpaperSize.accepted(-1, -1));
        assertFalse(WallpaperSize.accepted(0, 1080));
        assertFalse(WallpaperSize.accepted(1920, 0));
    }
}