package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Makes the remote's Globoplay button open another app. On this TCL the button sends the vendor keycode
 * 4097, which the system treats as a global key and TCL's KeyMultipleService turns into "open Globoplay";
 * accessibility key filtering sees it first, so consuming it here replaces the action.
 *
 * Only that key is consumed. Every other key returns false at once, so the cost per key press is the one
 * binder call Android makes to any key-filtering service. No screen content is read.
 */
public class KeyRemapService extends AccessibilityService {

    static final int KEY_GLOBOPLAY = 4097;

    private volatile String target = "";
    /** Kept as a field: SharedPreferences holds change listeners weakly. */
    private SharedPreferences.OnSharedPreferenceChangeListener prefsListener;

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
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KEY_GLOBOPLAY) return false;
        String pkg = target;
        if (pkg.isEmpty()) return false;   // remap off: TCL opens Globoplay as usual
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) launch(pkg);
        return true;                       // consume both down and up so the system never sees the key
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
            // App uninstalled or disabled: the press is simply lost, like a dead button.
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
