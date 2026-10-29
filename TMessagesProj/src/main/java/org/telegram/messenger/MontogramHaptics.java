/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.messenger;

import android.os.Build;
import android.os.SystemClock;
import android.os.Vibrator;
import android.provider.Settings;
import android.view.View;
import java.util.Objects;

/** UI feedback only; incoming calls and notification vibration never route here. */
public final class MontogramHaptics {
    private static final MontogramHapticPolicy POLICY = new MontogramHapticPolicy();
    private MontogramHaptics() {}
    public static boolean allowInteraction() {
        int mode = MontogramConfig.hapticsMode();
        if (mode == 0) return true; // Preserve native flags and permissions exactly.
        if (mode != 2 || ApplicationLoader.applicationContext == null) return false;
        boolean enabled;
        try {
            enabled = Settings.System.getInt(ApplicationLoader.applicationContext.getContentResolver(),
                Settings.System.HAPTIC_FEEDBACK_ENABLED, 0) != 0;
        } catch (RuntimeException unavailable) { return false; }
        return POLICY.allow(mode, enabled, SystemClock.elapsedRealtime());
    }
    public static boolean perform(View view, int type) {
        Objects.requireNonNull(view);
        return allowInteraction() && view.performHapticFeedback(type);
    }
    public static boolean perform(View view, int type, int flags) {
        Objects.requireNonNull(view);
        return allowInteraction() && view.performHapticFeedback(type, flags);
    }
    public static void vibrate(Vibrator vibrator, long duration) {
        Objects.requireNonNull(vibrator);
        if (duration <= 0) throw new IllegalArgumentException("Positive duration required");
        if (allowInteraction()) vibrator.vibrate(duration);
    }
    // Object keeps API26-only VibrationEffect out of this widely loaded class's signature.
    public static void vibrate(Vibrator vibrator, Object effect) {
        Objects.requireNonNull(vibrator);
        Objects.requireNonNull(effect);
        if (allowInteraction() && Build.VERSION.SDK_INT >= 26) Api26.vibrate(vibrator, effect);
    }
    private static final class Api26 {
        static void vibrate(Vibrator vibrator, Object effect) {
            vibrator.vibrate((android.os.VibrationEffect) effect);
        }
    }
}
