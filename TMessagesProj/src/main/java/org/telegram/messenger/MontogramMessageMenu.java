/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.messenger;

import java.util.ArrayList;
import java.util.Arrays;

/** Reorders/removes existing tuples only. Unknown categories are always core. */
public final class MontogramMessageMenu {
    private MontogramMessageMenu() {}
    // Categories supplied only after Telegram has decided which actions are available.
    public static final int CORE=0, TRANSLATE=1, SHARE=2, SAVE=3, OTHER_TOOL=4;
    public static int[] indices(int[] categories, int mode, int hiddenMask) {
        if(categories==null || categories.length>256 || mode<0 || mode>2
                || hiddenMask<0 || hiddenMask>7 || mode==0&&hiddenMask==0) return null;
        int[] selected=new int[categories.length]; int count=0;
        for(int pass=0;pass<(mode==0?1:2);pass++) {
            for(int i=0;i<categories.length;i++) {
                int kind=categories[i];
                if(kind>=TRANSLATE && kind<=SAVE && (hiddenMask & (1<<(kind-1)))!=0) continue;
                boolean tool=kind>=TRANSLATE && kind<=OTHER_TOOL;
                if(mode!=0 && tool!=(mode==1 ? pass==0 : pass==1)) continue;
                selected[count++]=i;
            }
        }
        // Specialized menus must not become empty solely because of customization.
        if(count==0) return null;
        boolean unchanged=count==categories.length;
        for(int i=0;unchanged&&i<count;i++) unchanged=selected[i]==i;
        return unchanged?null:Arrays.copyOf(selected,count);
    }
    public static boolean apply(ArrayList<Integer> icons, ArrayList<CharSequence> titles,
            ArrayList<Integer> options, int[] categories, int mode, int hiddenMask) {
        if(icons==null || titles==null || options==null || categories==null
                || icons.size()!=options.size() || titles.size()!=options.size()
                || categories.length!=options.size() || icons==options
                || (Object)icons==titles || (Object)options==titles) return false;
        int[] plan=indices(categories,mode,hiddenMask);
        if(plan==null) return false;
        ArrayList<Integer> nextIcons=new ArrayList<>(plan.length),nextOptions=new ArrayList<>(plan.length);
        ArrayList<CharSequence> nextTitles=new ArrayList<>(plan.length);
        for(int i:plan) { nextIcons.add(icons.get(i));nextTitles.add(titles.get(i));nextOptions.add(options.get(i)); }
        icons.clear();icons.addAll(nextIcons);
        titles.clear();titles.addAll(nextTitles);
        options.clear();options.addAll(nextOptions);
        return true;
    }
}
