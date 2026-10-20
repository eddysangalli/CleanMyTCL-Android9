package com.gathin.tvhome;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Maps the remote's Input button to the launcher's own Inputs menu, from any app. Different remotes send
 * different key codes for that button: KEYCODE_TV (Chromecast voice remote), KEYCODE_TV_INPUT, or
 * KEYCODE_MEDIA_RECORD (the Xiaomi Mi TV croods remote's "Input/Source" button, which the kernel reports
 * as KEY_RECORD). The stock launcher used to handle these; with it disabled the button does nothing, so
 * this service catches them and opens MainActivity's Inputs dropdown. Every other key passes through
 * untouched. Opt-in: this service is declared in AndroidManifest.xml but is only bound when the user
 * enables it from Accessibility settings; it does not show a tile of its own.
 */
public class InputKeyService extends AccessibilityService {

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int kc = event.getKeyCode();
        if (kc != KeyEvent.KEYCODE_TV
                && kc != KeyEvent.KEYCODE_TV_INPUT
                && kc != KeyEvent.KEYCODE_MEDIA_RECORD) {
            return false;
        }
        if (event.getAction() == KeyEvent.ACTION_UP) {
            try {
                startActivity(new Intent(this, MainActivity.class)
                        .setAction(MainActivity.ACTION_OPEN_INPUTS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {
            }
        }
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
