/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.ui.Components;

import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.view.animation.PathInterpolator;
import org.telegram.messenger.MontogramConfig;
import org.telegram.messenger.MontogramMotionPolicy;

/** Scoped bridge; no global animation changes, observers, polling or per-frame I/O. */
public final class MontogramMotion {
    public static final TimeInterpolator STANDARD = new PathInterpolator(.2f, 0f, 0f, 1f);
    private MontogramMotion() {}
    public static boolean systemAllows(Context context) {
        if (context == null) return false;
        try {
            if (Build.VERSION.SDK_INT >= 26) return ValueAnimator.areAnimatorsEnabled();
            float scale = Settings.Global.getFloat(context.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
            return scale > 0f && !Float.isInfinite(scale);
        } catch (RuntimeException unavailable) { return false; }
    }
    public static long switchDuration(Context context) {
        return MontogramMotionPolicy.switchDuration(MontogramConfig.motionMode(), systemAllows(context));
    }
}
