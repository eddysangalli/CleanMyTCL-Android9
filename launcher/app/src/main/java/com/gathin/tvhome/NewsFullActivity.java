package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The news in full screen: one headline at a time over its lead photo, rotating on a timer, like a
 * screensaver made of today's front page. It is opened two ways (NewsFeed): from the settings menu,
 * and by MainActivity after the remote has been idle for a while. Left and Right step by hand, OK
 * opens the article, Back leaves. Photos come from the feed and are cached on disk (NewsFeed.imageFile),
 * decoded at screen size in RGB_565 so a low-RAM TV never holds a full ARGB picture.
 */
public final class NewsFullActivity extends Activity {
    /** One headline per this long while the view is left alone. */
    private static final long SLIDE_MS = 12_000;

    private final List<NewsFeed.Headline> items = new ArrayList<>();
    private final Handler timer = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private ImageView photo;
    private TextView kicker, title, age;
    private View dots;
    private int index;
    /** Bumped on every slide, so a photo still decoding for an older one is dropped, not shown. */
    private int generation;

    private final Runnable advance = this::advanceSlide;

    private void advanceSlide() {
        if (items.isEmpty()) return;
        index = (index + 1) % items.size();
        show();
        timer.postDelayed(advance, SLIDE_MS);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_news_full);
        photo = findViewById(R.id.fullPhoto);
        kicker = findViewById(R.id.fullKicker);
        title = findViewById(R.id.fullTitle);
        age = findViewById(R.id.fullAge);
        dots = findViewById(R.id.fullDots);
        goImmersive();

        items.addAll(NewsFeed.cached(this));
        if (items.isEmpty()) {
            // Nothing cached yet: fetch once and show whatever comes back, else leave.
            NewsFeed.fetch(this, headlines -> {
                items.addAll(headlines);
                if (items.isEmpty()) { finish(); return; }
                buildDots();
                show();
                timer.postDelayed(advance, SLIDE_MS);
            });
            return;
        }
        buildDots();
        show();
        timer.postDelayed(advance, SLIDE_MS);

        // Left and Right step by hand; OK opens the article; Back leaves. Held on the whole screen.
        View root = findViewById(R.id.fullRoot);
        root.setFocusable(true);
        root.requestFocus();
        root.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_UP:
                    step(-1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    step(1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    open();
                    return true;
                default:
                    finish();       // Back, menu, anything else: out.
                    return true;
            }
        });
    }

    /** Steps by hand in either direction and restarts the clock, so a read is never cut short. */
    private void step(int delta) {
        if (items.isEmpty()) return;
        timer.removeCallbacks(advance);
        index = (index + delta + items.size()) % items.size();
        show();
        timer.postDelayed(advance, SLIDE_MS);
    }

    private void open() {
        NewsFeed.Headline h = items.get(index);
        if (h.link.isEmpty()) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(h.link)));
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.cant_open, h.source), Toast.LENGTH_SHORT).show();
        }
    }

    /** Puts one headline on screen and asks for its photo, off the main thread. */
    private void show() {
        NewsFeed.Headline h = items.get(index);
        kicker.setText(h.source);
        title.setText(h.title);
        if (h.published > 0) {
            age.setText(android.text.format.DateUtils.getRelativeTimeSpanString(
                    h.published, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS));
            age.setVisibility(View.VISIBLE);
        } else {
            age.setVisibility(View.GONE);
        }
        markDot(index);
        fade(kicker, title, age);

        final int gen = ++generation;
        final String url = h.image;
        photo.setImageDrawable(null);
        if (url.isEmpty()) return;                      // a headline without a photo stands on the black.
        io.execute(() -> {
            File file = NewsFeed.imageFile(getApplicationContext(), url);
            Bitmap bmp = file == null ? null : decode(file);
            runOnUiThread(() -> {
                if (gen != generation || isFinishing() || isDestroyed() || bmp == null) return;
                photo.setImageDrawable(new BitmapDrawable(getResources(), bmp));
                photo.setAlpha(0f);
                photo.animate().alpha(1f).setDuration(500).start();
            });
        });
    }

    /** Decodes a photo down to about one screen, in RGB_565: half the memory, and news photos have no alpha. */
    private Bitmap decode(File file) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            int target = getResources().getDisplayMetrics().widthPixels;
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= target) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
        } catch (OutOfMemoryError ignored) {
            return null;                                // the slide simply shows its text.
        }
    }

    private void fade(View... views) {
        for (View v : views) {
            v.setAlpha(0f);
            v.animate().alpha(1f).setDuration(400).start();
        }
    }

    private void buildDots() {
        dots.setVisibility(items.size() > 1 ? View.VISIBLE : View.GONE);
    }

    /** The dot strip mirrors the position in the rotation; it is filled in by markDot. */
    private void markDot(int position) {
        android.widget.LinearLayout strip = (android.widget.LinearLayout) dots;
        while (strip.getChildCount() < items.size()) {
            View d = new View(this);
            int size = (int) (7 * getResources().getDisplayMetrics().density);
            android.widget.LinearLayout.LayoutParams lp =
                    new android.widget.LinearLayout.LayoutParams(size, size);
            lp.leftMargin = lp.rightMargin = size / 2;
            d.setLayoutParams(lp);
            strip.addView(d);
        }
        for (int i = 0; i < strip.getChildCount(); i++) {
            strip.getChildAt(i).setBackgroundColor(
                    i == position ? 0xCCFFFFFF : 0x40FFFFFF);
        }
    }

    /** True full screen: no clock, no nav bar — a screensaver should own the whole panel. */
    private void goImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        timer.removeCallbacks(advance);                 // never rotate while another app is in front.
    }

    @Override
    protected void onResume() {
        super.onResume();
        goImmersive();
        if (!items.isEmpty()) {
            timer.removeCallbacks(advance);
            timer.postDelayed(advance, SLIDE_MS);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timer.removeCallbacks(advance);
        io.shutdownNow();
    }
}
