/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.messenger;

/** Presentation only. Never changes Telegram send limits, splitting, edits or message content. */
public final class MontogramDraftCounter {
    public static final int NOT_CHAT=1, DESTROYED=2, EDITING=4, RECORDING=8, RECORDED_PREVIEW=16,
        TOP_PREVIEW=32, BOT_CONTROLS=64, SENDER_SELECTOR=128, PAID=256, LIVE=512,
        RICH_DRAFT=1024, NATIVE_LIMIT=2048, CAPTION_ANIMATION=4096, DISABLED_INPUT=8192, NARROW=16384;
    private MontogramDraftCounter(){}
    public static boolean visible(boolean enabled,int modeFlags){return enabled&&modeFlags==0;}
    public static int displayLimit(int nativeLimit,int messageLimit){return nativeLimit>0?nativeLimit:Math.max(0,messageLimit);}
    /** Normal Telegram text splitting uses String.length(): UTF-16 units, not caption code points. */
    public static int initialCount(CharSequence text){return text==null?0:text.length();}
}
