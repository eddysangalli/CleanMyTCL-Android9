package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The photo stage of the RSS carousel (news_hero): the featured headline's lead photo inside the
 * carousel at the top of Home, with a soft fade into black towards the bottom edge (the headline's
 * own words live on that fade) and a thin ring while the carousel has the focus. Photo, fade and
 * ring are drawn in this one pass, so the carousel costs a single small draw instead of three
 * stacked views; on the 1.75 GB TV that is the difference between a smooth focus move and a
 * stuttering one.
 *
 * <p>The photo is decoded off the main thread at the carousel's own size in RGB_565 (never 4K),
 * only the file this launcher already cached is read, and the previous photo is dropped the moment
 * a crossfade ends, so at most two bitmaps are ever alive. A headline set while a decode is still
 * running wins: the late result is thrown away, so a slow photo can never stall or overwrite
 * navigation. Nothing is drawn while the view is GONE, which is how an RSS-off Home pays nothing.
 */
public final class NewsBackdropView extends View {

    /** How long one photo dissolves into the next; skipped entirely when motion is reduced. */
    private static final long FADE_MS = 420;
    /** Corner rounding of the carousel, in dp: the same softness as the cards below it. */
    private static final float RADIUS_DP = 14f;

    private final Paint photoPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint fillPaint = new Paint();
    private final Paint scrimPaint = new Paint();
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix crop = new Matrix();
    /** The carousel's rounded rectangle: everything but the focus ring is clipped to it. */
    private final Path clip = new Path();
    private final RectF bounds = new RectF();
    private float radius;

    /** The photo on screen, and the one it is dissolving away from (null outside a fade). */
    private Bitmap shown;
    private Bitmap outgoing;
    /** True while a photo is on screen, so the fill can be skipped and the fade can land cleanly. */
    private boolean hasPhoto;
    /** While the carousel owns the focus, a thin white ring stands around it. */
    private boolean focused;

    private final AtomicInteger generation = new AtomicInteger();
    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private ValueAnimator fade;
    private float fadeFraction = 1f;
    private boolean reducedMotion;
    private String shownUrl = "";
    /** What the no-photo backdrop is derived from: the headline itself, not its URL. */
    private String seed = "";

    private int scrimColorTop, scrimColorBottom;
    private float scrimTopEnd, scrimBottomStart;
    private float ringInset;

    public NewsBackdropView(Context context) {
        this(context, null);
    }

    public NewsBackdropView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        // The fades are not glass and the photo is not a panel: alpha here is one flat fade of
        // the whole layer, so the GPU can flatten it instead of layering.
        setLayerType(LAYER_TYPE_NONE, null);
    }

    /** Reduced motion swaps photos without the dissolve, like the rest of the launcher. */
    void setReducedMotion(boolean value) {
        reducedMotion = value;
    }

    /** Shows or clears the focus ring: only the colour of the ring changes, one small redraw. */
    void setFocused(boolean value) {
        if (focused == value) return;
        focused = value;
        invalidate();
    }

    /** Frees both photos: Home is going away, so the memory goes with it. */
    void release() {
        generation.incrementAndGet();          // any decode still running is now stale
        cancelFade();
        outgoing = null;
        shown = null;
        hasPhoto = false;
        shownUrl = "";
        seed = "";
        invalidate();
    }

    /**
     * Shows a headline's photo in the carousel. Setting the same photo twice is free, so browsing
     * the stories never reloads anything.
     */
    void show(NewsFeed.Headline headline, boolean animate) {
        if (headline == null) return;
        seed = headline.source + "|" + headline.title;
        String url = headline.image;
        boolean samePhoto = url.equals(shownUrl);
        shownUrl = url;
        if (url.isEmpty()) {                    // no photo: the derived colour is the stage
            generation.incrementAndGet();
            cancelFade();
            dropPhotos();
            hasPhoto = false;
            invalidate();
            return;
        }
        if (samePhoto && hasPhoto) return;       // already showing it
        final int token = generation.incrementAndGet();
        final Context app = getContext().getApplicationContext();
        final int target = decodeTarget();
        decoder.execute(() -> {
            // The disk cache is NewsFeed's: already downloaded, never refetched while it is fresh.
            File file = NewsFeed.imageFile(app, url);
            Bitmap decoded = file == null ? null : decode(file, target);
            if (decoded == null) return;         // offline or not an image: keep the colour
            post(() -> {
                if (token != generation.get()) return;   // a newer headline took over: drop it
                swap(decoded, animate && !reducedMotion && isShown());
            });
        });
    }

    /** Puts the decoded photo on screen, dissolving from the previous one when asked to. */
    private void swap(Bitmap next, boolean animate) {
        cancelFade();
        if (!animate || shown == null) {
            dropPhotos();
            shown = next;
            hasPhoto = true;
            invalidate();
            return;
        }
        outgoing = shown;                       // kept only until the fade lands
        shown = next;
        hasPhoto = true;
        fadeFraction = 0f;
        fade = ValueAnimator.ofFloat(0f, 1f).setDuration(FADE_MS);
        fade.setInterpolator(new LinearInterpolator());
        fade.addUpdateListener(a -> {
            fadeFraction = (float) a.getAnimatedValue();
            invalidate();
        });
        fade.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                outgoing = null;                // the old photo's memory goes back to the pool
                fadeFraction = 1f;
                fade = null;
                invalidate();
            }
        });
        fade.start();
    }

    private void cancelFade() {
        if (fade != null) {
            fade.cancel();
            fade = null;
        }
        outgoing = null;
        fadeFraction = 1f;
    }

    private void dropPhotos() {
        outgoing = null;
        shown = null;
        fadeFraction = 1f;
    }

    /** The carousel width in pixels: the photo never needs more than the banner can show. */
    private int decodeTarget() {
        int width = getWidth();
        if (width > 0) return width;
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        return metrics.widthPixels;
    }

    /**
     * Decodes to at most the carousel's width, in RGB_565: a 4000x3000 news photo becomes a strip the
     * banner can actually draw, about a fifth of the memory of ARGB_8888, with no visible loss behind
     * the fade.
     */
    private static Bitmap decode(File file, int target) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= target) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            options.inDither = true;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (OutOfMemoryError e) {
            return null;                        // the headline falls back to its colour
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float density = getResources().getDisplayMetrics().density;
        radius = RADIUS_DP * density;
        ringInset = Math.max(2f, 2f * density);
        bounds.set(0, 0, w, h);
        clip.reset();
        clip.addRoundRect(bounds, radius, radius, Path.Direction.CW);
        // A light scrim at the top so the photo melts into the black under the clock, and a long
        // ramp into black at the bottom: the headline's words stand on it without any panel.
        scrimTopEnd = h * 0.20f;
        scrimBottomStart = h * 0.34f;
        scrimColorTop = 0x33000000;             // 20% black
        scrimColorBottom = 0xF2000000;          // 95% black at the very edge
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;

        int save = canvas.save();
        canvas.clipPath(clip);
        if (!hasPhoto || shown == null || shown.isRecycled()) {
            // The elegant fallback: a deep colour derived from the headline itself, never black.
            fillPaint.setColor(derivedColor(seed));
            canvas.drawRect(0, 0, w, h, fillPaint);
        } else {
            drawPhoto(canvas, shown, fadeFraction, w, h);
            if (outgoing != null && !outgoing.isRecycled() && fadeFraction < 1f) {
                drawPhoto(canvas, outgoing, 1f - fadeFraction, w, h);
            }
        }

        // The fades, over whatever is behind: they are what makes the text readable on a bright
        // photo and stop the banner from ending in a hard edge against the black Home.
        scrimPaint.setShader(new LinearGradient(0, 0, 0, scrimTopEnd,
                scrimColorTop, 0x00000000, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, scrimTopEnd, scrimPaint);
        scrimPaint.setShader(new LinearGradient(0, scrimBottomStart, 0, h,
                0x00000000, scrimColorBottom, Shader.TileMode.CLAMP));
        canvas.drawRect(0, scrimBottomStart, w, h, scrimPaint);
        canvas.restoreToCount(save);

        // The ring is drawn outside the clip, so its inner half rounds with the corners cleanly.
        if (focused) {
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeWidth(ringInset * 2f);
            ringPaint.setColor(0x66FFFFFF);
            RectF ring = new RectF(bounds);
            ring.inset(ringInset, ringInset);
            canvas.drawRoundRect(ring, radius - ringInset, radius - ringInset, ringPaint);
        }
    }

    /** centerCrop of the photo, faded by alpha: the composition fills the banner and never distorts. */
    private void drawPhoto(Canvas canvas, Bitmap bitmap, float alpha, int w, int h) {
        if (alpha <= 0f) return;
        crop.reset();
        float scale = Math.max((float) w / bitmap.getWidth(), (float) h / bitmap.getHeight());
        float dx = (w - bitmap.getWidth() * scale) / 2f;
        float dy = (h - bitmap.getHeight() * scale) / 2f;
        crop.setScale(scale, scale);
        crop.postTranslate(dx, dy);
        photoPaint.setAlpha(Math.round(255 * Math.min(1f, alpha)));
        // The crop matrix is applied only for the bitmap; save/restore puts the canvas back in
        // carousel space, so the fades that follow are positioned against the banner, not the
        // photo's own coordinates.
        int save = canvas.save();
        canvas.concat(crop);
        canvas.drawBitmap(bitmap, 0, 0, photoPaint);
        canvas.restoreToCount(save);
    }

    /**
     * A deep, quiet colour with the headline's own hue, used when there is no photo. It is derived,
     * not chosen: the same story always gets the same stage, so an offline TV still looks
     * composed instead of random.
     */
    private static int derivedColor(String seed) {
        if (seed == null || seed.isEmpty()) return 0xFF14171C;
        float hue = Math.abs(seed.hashCode() % 360);
        return Color.HSVToColor(new float[]{hue, 0.20f, 0.15f});
    }
}
