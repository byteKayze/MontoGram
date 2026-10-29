/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.ui.Components;

/** M3 small-app-bar geometry. Inputs are already converted from dp/sp to px. */
public final class MontogramChatBarLayout {
    private MontogramChatBarLayout(){}
    public static int height(float density,float titleLine,float subtitleLine){
        if(!(density>0)||(Float.isNaN(density)||Float.isInfinite(density))||!(titleLine>0)||(Float.isNaN(titleLine)||Float.isInfinite(titleLine))||!(subtitleLine>0)||(Float.isNaN(subtitleLine)||Float.isInfinite(subtitleLine)))throw new IllegalArgumentException("Invalid bar metrics");
        return (int)Math.ceil(Math.max(64*density,16*density+titleLine+subtitleLine));
    }
    public static int singleTitleHeight(float density,float titleLine){
        if(!(density>0)||Float.isNaN(density)||Float.isInfinite(density)||!(titleLine>0)||Float.isNaN(titleLine)||Float.isInfinite(titleLine))throw new IllegalArgumentException("Invalid bar metrics");
        return (int)Math.ceil(Math.max(64*density,16*density+titleLine));
    }
    public static float titleTop(int height,float titleLine,float subtitleLine){return (height-titleLine-subtitleLine)/2f;}
    public static float subtitleTop(int height,float titleLine,float subtitleLine){return titleTop(height,titleLine,subtitleLine)+titleLine;}
    public static float singleTitleTop(int height,float titleHeight){return (height-titleHeight)/2f;}
}
