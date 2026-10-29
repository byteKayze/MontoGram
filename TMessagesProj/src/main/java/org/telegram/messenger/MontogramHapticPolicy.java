/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.messenger;

/** Reduced mode limits frequency, never synthesizes or amplifies an effect. */
public final class MontogramHapticPolicy {
    private long previous = -1;
    public synchronized boolean allow(int mode, boolean systemEnabled, long elapsedMillis) {
        if (mode == 0) return true;
        if (mode != 2 || !systemEnabled || elapsedMillis < 0) return false;
        if (previous >= 0 && elapsedMillis < previous) {
            return false;
        }
        if (previous >= 0 && elapsedMillis - previous < 100) return false;
        previous = elapsedMillis;
        return true;
    }
}
