/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.messenger;

/** Bottom spacing only for opted-in, ordinary two-line main-list rows. */
public final class MontogramDialogDensity {
    private MontogramDialogDensity() {}
    public static int heightDp(int mode, int nativeHeightDp, boolean eligible, float fontScale) {
        // Never reinterpret custom native heights, expanded rows or enlarged fonts.
        if (!eligible || nativeHeightDp != 70 || Float.isNaN(fontScale) || Float.isInfinite(fontScale)
                || fontScale <= 0 || fontScale > 1f) return nativeHeightDp;
        return mode == 1 ? 66 : mode == 2 ? 78 : nativeHeightDp;
    }
}
