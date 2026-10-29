/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.messenger;

/** Base durations only. Android ObjectAnimator applies nonzero platform duration scaling. */
public final class MontogramMotionPolicy {
    public static final int STANDARD = 0, OFF = 1;
    private MontogramMotionPolicy() {}
    public static long switchDuration(int mode, boolean systemAllows) {
        if (mode < STANDARD || mode > OFF) throw new IllegalArgumentException("Invalid motion mode");
        return systemAllows && mode == STANDARD ? 200L : 0L;
    }
}
