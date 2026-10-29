/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.messenger;

/** Snap policy: no guessed partial-animation height; preserve already-hidden search offsets. */
public final class MontogramStoryStrip {
    private MontogramStoryStrip(){}
    public static boolean needsReset(boolean applied,int visibilityFlags,float shown,float cellProgress,
            boolean animating,float pendingOffset){
        return !applied||visibilityFlags!=0||shown!=0f||cellProgress!=0f||animating
            ||Float.isNaN(pendingOffset)||Float.isInfinite(pendingOffset);
    }
    public static float offsetAfter(boolean reset,float pendingOffset){return reset?0f:pendingOffset;}
}
