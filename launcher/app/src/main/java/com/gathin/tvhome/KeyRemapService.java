package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

/**
 * Makes the remote's Globoplay button open another app. On this TCL the button is not a hardware key: TCL's
 * key service injects keycode 4097 and starts Globoplay itself, and injected keys never reach an accessibility
 * key filter. So instead of the key, this watches for Globoplay coming to the front and switches to the chosen app.
 * Globoplay is left in the background on purpose: closing it made the next press a cold start, ~4 s of Globoplay
 * loading before the switch. We wait for its startup window changes to settle (~0.8 s) before switching; an
 * immediate switch was taken back by Globoplay's own main screen and account chooser. Android drops a cached app
 * first anyway when memory runs low.
 *
 * Any Globoplay screen counts: when its task already exists, the button brings back whatever screen was on top
 * (the profile chooser, a show), not the splash. Globoplay opened from an Eddy TV tile is left alone until the
 * Home screen is back ({@link #allowSource()}, {@link #homeResumed()}); opened any other way (the button, voice,
 * another app) it is redirected.
 *
 * The service gets a single event type from a single package (key_remap.xml), filters no keys and reads no
 * screen content, so other apps and key presses pay nothing for it. It runs in the Home's own process.
 */
public class KeyRemapService extends AccessibilityService {

    static final String SOURCE = "com.globo.globotv";
    /**
     * Globoplay emits several window changes while it starts (splash, main screen, account chooser).
     * Redirecting on the first one was too early: the later windows took focus back. Wait until the
     * source has been quiet for this long, then switch once.
     */
    private static final long SETTLE_MS = 800;

    /** Globoplay was opened from Eddy TV. Static: the Home and this service share the process. */
    private static volatile boolean allowed;

    private volatile String target = "";
    /** Kept as a field: SharedPreferences holds change listeners weakly. */
    private SharedPreferences.OnSharedPreferenceChangeListener prefsListener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastSourceEvent;

    /** Runs after Globoplay's startup window changes have settled. */
    private final Runnable redirect = new Runnable() {
        @Override
        public void run() {
            if (SystemClock.uptimeMillis() - lastSourceEvent < SETTLE_MS) return;
            String to = target;
            if (to.isEmpty() || allowed) return;
            launch(to);
        }
    };

    /** Called by the Home just before it opens Globoplay itself. */
    static void allowSource() {
        allowed = true;
    }

    /** The Home is on screen again, so whatever brings Globoplay back next is not the user's tile. */
    static void homeResumed() {
        allowed = false;
    }

    @Override
    protected void onServiceConnected() {
        HomePrefs prefs = new HomePrefs(this);
        target = prefs.remapTarget();
        prefsListener = (sp, key) -> {
            if (HomePrefs.KEY_REMAP.equals(key)) target = prefs.remapTarget();
        };
        prefs.listen(prefsListener);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = event.getPackageName();
        if (pkg == null || !SOURCE.contentEquals(pkg)) return;
        String to = target;
        if (to.isEmpty() || allowed) return;     // remap off, or opened from the Home
        lastSourceEvent = SystemClock.uptimeMillis();
        handler.removeCallbacks(redirect);
        handler.postDelayed(redirect, SETTLE_MS);
    }

    private void launch(String pkg) {
        PackageManager pm = getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) return;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(i);
        } catch (Exception ignored) {
            // Target uninstalled or disabled: Globoplay simply stays, as without the remap.
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(redirect);
        super.onDestroy();
    }
}
