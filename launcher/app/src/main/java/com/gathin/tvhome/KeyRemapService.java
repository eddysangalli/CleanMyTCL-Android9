package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityManager;
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
 * key filter. So instead of the key, this watches for Globoplay's entry screen coming to the front, switches
 * to the chosen app and then closes Globoplay once it is in the background.
 *
 * Globoplay opened from an Eddy TV tile is left alone (see {@link #allowSource()}). The service gets a single
 * event type from a single package (key_remap.xml), filters no keys and reads no screen content, so other apps
 * and key presses pay nothing for it.
 */
public class KeyRemapService extends AccessibilityService {

    static final String SOURCE = "com.globo.globotv";
    /** Globoplay's launch screen; the button always opens it (cmp=com.globo.globotv/.splashtv.SplashActivity). */
    private static final String SOURCE_ENTRY = "Splash";
    /** After a redirect, any Globoplay screen in this window is the same launch going on (splash -> main). */
    private static final long FOLLOW_MS = 8000;
    /** Room for a cold Globoplay start after the user picked it on the Home screen. */
    private static final long ALLOW_MS = 10000;
    private static final long CLOSE_DELAY_MS = 1500;

    /** Uptime until which Globoplay was opened from Eddy TV and must not be redirected. Same process as the Home. */
    private static volatile long allowUntil;

    private volatile String target = "";
    private long followUntil;
    private final Handler handler = new Handler(Looper.getMainLooper());
    /** Kept as a field: SharedPreferences holds change listeners weakly. */
    private SharedPreferences.OnSharedPreferenceChangeListener prefsListener;

    /** Called by the Home just before it opens Globoplay itself. */
    static void allowSource() {
        allowUntil = SystemClock.uptimeMillis() + ALLOW_MS;
    }

    private final Runnable closeSource = () -> {
        ActivityManager am = getSystemService(ActivityManager.class);
        if (am != null) am.killBackgroundProcesses(SOURCE);
    };

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
        long now = SystemClock.uptimeMillis();
        if (to.isEmpty() || now < allowUntil) return;   // remap off, or opened from the Home
        CharSequence cls = event.getClassName();
        boolean entry = cls != null && cls.toString().contains(SOURCE_ENTRY);
        if (!entry && now > followUntil) return;        // the user is inside Globoplay
        followUntil = now + FOLLOW_MS;
        launch(to);
        handler.removeCallbacks(closeSource);
        handler.postDelayed(closeSource, CLOSE_DELAY_MS);
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
}
