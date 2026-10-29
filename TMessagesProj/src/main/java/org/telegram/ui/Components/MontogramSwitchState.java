/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.ui.Components;

/** Allocation-free M3 switch state roles, derived from Google MDC 1.13.0 tokens.
 * Drawing, checked-state animation and interaction ownership remain in Switch.
 */
final class MontogramSwitchState {
    enum Role { OUTLINE, ON_PRIMARY, PRIMARY_CONTAINER, ON_SURFACE_VARIANT,
        ON_SURFACE, SURFACE, PRIMARY, CONTAINER_HIGHEST }

    private MontogramSwitchState() {}

    static Role thumb(boolean enabled, boolean checked, boolean interacting) {
        if (!enabled) return checked ? Role.SURFACE : Role.ON_SURFACE;
        if (interacting) return checked ? Role.PRIMARY_CONTAINER : Role.ON_SURFACE_VARIANT;
        return checked ? Role.ON_PRIMARY : Role.OUTLINE;
    }

    static float thumbOpacity(boolean enabled, boolean checked) {
        return enabled || checked ? 1f : .38f;
    }

    static Role track(boolean enabled, boolean checked) {
        return checked ? enabled ? Role.PRIMARY : Role.ON_SURFACE : Role.CONTAINER_HIGHEST;
    }

    static Role outline(boolean enabled) { return enabled ? Role.OUTLINE : Role.ON_SURFACE; }
    static float trackOpacity(boolean enabled) { return enabled ? 1f : .12f; }

    static float stateOpacity(boolean enabled, boolean pressed, boolean focused, boolean hovered) {
        if (!enabled) return 0f;
        return pressed || focused ? .10f : hovered ? .08f : 0f;
    }
}
