package com.gathin.tvhome;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextClock;

/**
 * Keeps TextClocks moving on pre-Android 12 firmware that stops ACTION_TIME_TICK after standby.
 * A TCL Android 9 set exhibited that upstream (#3044), so redraw on the real minute boundary and
 * immediately when this display comes back on.
 */
final class ClockRefresh implements Runnable, DisplayManager.DisplayListener {
    private static final long MARGIN_MS = 250L;

    private final View root;
    private final DisplayManager displays;
    private boolean registered;
    private boolean on = true;

    ClockRefresh(View root) {
        this.root = root;
        displays = (DisplayManager) root.getContext().getSystemService(Context.DISPLAY_SERVICE);
    }

    void start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return;
        if (!registered) {
            displays.registerDisplayListener(this, null);
            registered = true;
        }
        schedule();
    }

    void stop() {
        root.removeCallbacks(this);
        if (registered) {
            displays.unregisterDisplayListener(this);
            registered = false;
        }
    }

    @Override
    public void run() {
        redraw(root);
        schedule();
    }

    @Override
    public void onDisplayChanged(int displayId) {
        Display display = root.getDisplay();
        if (display == null || display.getDisplayId() != displayId) return;
        boolean nowOn = display.getState() == Display.STATE_ON;
        if (nowOn && !on) run();
        on = nowOn;
    }

    @Override
    public void onDisplayAdded(int displayId) {
    }

    @Override
    public void onDisplayRemoved(int displayId) {
    }

    private void schedule() {
        root.removeCallbacks(this);
        root.postDelayed(this, 60_000L - System.currentTimeMillis() % 60_000L + MARGIN_MS);
    }

    private static void redraw(View view) {
        if (view instanceof TextClock) {
            TextClock clock = (TextClock) view;
            clock.setFormat12Hour(clock.getFormat12Hour());
            return;
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) redraw(group.getChildAt(i));
    }
}
