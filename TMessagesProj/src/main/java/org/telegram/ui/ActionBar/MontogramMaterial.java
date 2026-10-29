/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.ui.ActionBar;

import org.telegram.messenger.MontogramConfig;
import org.telegram.messenger.AndroidUtilities;

/** Component-scoped M3 baseline roles. Reference: Google MDC Android 1.13.0,
 * color/res/values/{colors,tokens}.xml (Apache-2.0). No global theme mutation. */
public final class MontogramMaterial {
    private MontogramMaterial() {}
    public static final int SURFACE = 0, CONTAINER_LOW = 1, CONTAINER = 2,
        CONTAINER_HIGH = 3, CONTAINER_HIGHEST = 4, ON_SURFACE = 5,
        ON_SURFACE_VARIANT = 6, PRIMARY = 7, ON_PRIMARY = 8,
        PRIMARY_CONTAINER = 9, ON_PRIMARY_CONTAINER = 10, SECONDARY = 11,
        SECONDARY_CONTAINER = 12, ON_SECONDARY_CONTAINER = 13, TERTIARY = 14,
        OUTLINE = 15, OUTLINE_VARIANT = 16, ERROR = 17, ON_ERROR = 18;
    private static final int[] LIGHT = {
        0xfffef7ff, 0xfff7f2fa, 0xfff3edf7, 0xffece6f0, 0xffe6e0e9,
        0xff1d1b20, 0xff49454f, 0xff6750a4, 0xffffffff, 0xffeaddff,
        0xff21005d, 0xff625b71, 0xffe8def8, 0xff1d192b, 0xff7d5260,
        0xff79747e, 0xffcac4d0, 0xffb3261e, 0xffffffff
    };
    private static final int[] DARK = {
        0xff141218, 0xff1d1b20, 0xff211f26, 0xff2b2930, 0xff36343b,
        0xffe6e0e9, 0xffcac4d0, 0xffd0bcff, 0xff381e72, 0xff4f378b,
        0xffeaddff, 0xffccc2dc, 0xff4a4458, 0xffe8def8, 0xffefb8c8,
        0xff938f99, 0xff49454f, 0xfff2b8b5, 0xff601410
    };
    public static int color(int role) {
        return color(role, Theme.isCurrentThemeDark());
    }
    public static int color(int role, boolean dark) {
        return (dark ? DARK : LIGHT)[role];
    }
    /** Per-component palette cache; respects per-chat changes without caching global theme state. */
    public static final class ComposerPalette {
        private final Theme.ResourcesProvider provider;
        private int lastPanelColor;
        private boolean resolved, dark;
        public ComposerPalette(Theme.ResourcesProvider provider) { this.provider = provider; }
        public int color(int role) {
            int panel = Theme.getColor(Theme.key_chat_messagePanelBackground, provider);
            if (!resolved || panel != lastPanelColor) {
                dark = AndroidUtilities.computePerceivedBrightness(panel) < 0.5f;
                lastPanelColor = panel;
                resolved = true;
            }
            return MontogramMaterial.color(role, dark);
        }
    }

    /** Four app-bar roles only; this provider must never be installed fragment-wide. */
    public static Theme.ResourcesProvider chatHeaderProvider(Theme.ResourcesProvider base,ComposerPalette palette){
        if(base==null||palette==null)throw new IllegalArgumentException("Header provider requires its owner palette");
        return new Theme.ResourcesProvider(){
            private int role(int key){
                if(key==Theme.key_actionBarDefault)return SURFACE;
                if(key==Theme.key_actionBarDefaultTitle)return ON_SURFACE;
                if(key==Theme.key_actionBarDefaultSubtitle||key==Theme.key_actionBarDefaultIcon)return ON_SURFACE_VARIANT;
                return -1;
            }
            @Override public int getColor(int key){int role=role(key);return role<0?base.getColor(key):palette.color(role);}
            @Override public int getCurrentColor(int key){int role=role(key);return role<0?base.getCurrentColor(key):palette.color(role);}
            // Header animation callbacks must not write scoped role colors into the chat provider.
            @Override public void setAnimatedColor(int key,int color){if(role(key)<0)base.setAnimatedColor(key,color);}
            @Override public boolean isDark(){return base.isDark();}
            @Override public android.graphics.drawable.Drawable getDrawable(String key){return base.getDrawable(key);}
            @Override public android.graphics.Paint getPaint(String key){return base.getPaint(key);}
        };
    }

    private static final Theme.ResourcesProvider PROVIDER = new Theme.ResourcesProvider() {
        @Override public int getColor(int key) {
            if (!MontogramConfig.isMaterial3()) return Theme.getColor(key);
            if (key == Theme.key_windowBackgroundWhite || key == Theme.key_actionBarDefault)
                return color(SURFACE);
            if (key == Theme.key_windowBackgroundGray) return color(CONTAINER_LOW);
            if (key == Theme.key_actionBarDefaultTitle || key == Theme.key_actionBarDefaultIcon ||
                key == Theme.key_windowBackgroundWhiteBlackText) return color(ON_SURFACE);
            if (key == Theme.key_actionBarDefaultSubtitle || key == Theme.key_windowBackgroundWhiteGrayText ||
                key == Theme.key_windowBackgroundWhiteGrayText2 || key == Theme.key_windowBackgroundWhiteValueText ||
                key == Theme.key_radioBackground) return color(ON_SURFACE_VARIANT);
            if (key == Theme.key_windowBackgroundWhiteBlueText || key == Theme.key_windowBackgroundWhiteBlueText4 ||
                key == Theme.key_radioBackgroundChecked || key == Theme.key_switchTrackChecked)
                return color(PRIMARY);
            if (key == Theme.key_divider) return color(OUTLINE_VARIANT);
            if (key == Theme.key_listSelector || key == Theme.key_actionBarDefaultSelector)
                return (color(ON_SURFACE) & 0x00ffffff) | 0x14000000;
            return Theme.getColor(key);
        }
        @Override public int getColorOrDefault(int key) { return getColor(key); }
        @Override public int getCurrentColor(int key) { return getColor(key); }
        @Override public boolean isDark() { return Theme.isCurrentThemeDark(); }
    };

    /** An existing caller-supplied provider always wins, including theme previews. */
    public static Theme.ResourcesProvider scoped(Theme.ResourcesProvider existing) {
        return existing != null ? existing : MontogramConfig.isMaterial3() ? PROVIDER : null;
    }
    public static boolean isProvider(Theme.ResourcesProvider provider) {
        return provider == PROVIDER && MontogramConfig.isMaterial3();
    }
}
