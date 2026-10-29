/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.messenger;

/** Integer-only clipping policy; no allocations on a scrolling frame. */
public final class MontogramAvatarStyle {
    private MontogramAvatarStyle(){}
    public static int radius(int nativeRadius,int shape,boolean eligible){
        if(nativeRadius<0||shape<0||shape>2)throw new IllegalArgumentException("Invalid avatar geometry");
        return !eligible||shape==0?nativeRadius:shape==1?nativeRadius/2:0;
    }
    public static boolean eligible(boolean ordinaryAndPredictiveEmpty,boolean currentEmpty,
            boolean previousEmpty,float progress){
        return ordinaryAndPredictiveEmpty&&currentEmpty&&(previousEmpty||progress>=1f);
    }
    /** One per cell, not per frame. -1 means the renderer must not receive a radius write. */
    public static final class RadiusCache {
        private int nativeRadius,applied=-1;
        public void setNative(int value){if(value<0)throw new IllegalArgumentException("Invalid radius");nativeRadius=value;}
        public int nativeRadius(){return nativeRadius;}
        public int takeUpdate(int shape,boolean eligible){
            int value=radius(nativeRadius,shape,eligible);
            if(value==applied)return -1;
            applied=value;return value;
        }
    }

}
