/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.ui.Components;

/** Baseline M3 one/two-line rows. Line metrics are scaled px, not unscaled dp. */
public final class MontogramListItemLayout {
    private MontogramListItemLayout() {}

    public static int height(float density, int titleLine, int supportingLine, int trailingLine, boolean icon) {
        if (!(density > 0) || Float.isInfinite(density) || titleLine <= 0 || supportingLine < 0 || trailingLine < 0) {
            throw new IllegalArgumentException("Invalid list metrics");
        }
        double content = Math.max((double) titleLine + supportingLine,
                Math.max(trailingLine, icon ? Math.ceil(24d * density) : 0d));
        return (int) Math.ceil(Math.max((supportingLine == 0 ? 56d : 72d) * density,
                content + 2d * Math.ceil(8d * density)));
    }

    public static int trailingWidth(int width, int horizontalPadding, int leadingSlot, int trailingGap) {
        if (width < 0 || horizontalPadding < 0 || leadingSlot < 0 || trailingGap < 0) {
            throw new IllegalArgumentException("Invalid list width");
        }
        long available = (long) width - horizontalPadding - leadingSlot - trailingGap;
        return (int) (Math.max(0L, available) * 2L / 5L);
    }
}
