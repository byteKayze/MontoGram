/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.messenger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.telegram.messenger.MontogramPreferences.Key;
import org.telegram.messenger.MontogramPreferences.State;

public final class MontogramSettingsChange {
    public enum Result { APPLIED, NO_CHANGE, STALE, SAVE_FAILED }
    public interface Committer { boolean commit(boolean material3,boolean messageDetails); }
    public interface StateCommitter { boolean commit(State state); }
    public final State previous,target;
    public final boolean previousMaterial3,previousDetails,material3,messageDetails;
    public final long revision;
    public final List<Key> changedFields;
    public MontogramSettingsChange(State previous,long revision,State target){
        if(previous==null||target==null||revision<0)throw new IllegalArgumentException("Invalid change");
        this.previous=previous;this.target=target;this.revision=revision;
        previousMaterial3=previous.bool(Key.MATERIAL3);previousDetails=previous.bool(Key.MESSAGE_DETAILS);
        material3=target.bool(Key.MATERIAL3);messageDetails=target.bool(Key.MESSAGE_DETAILS);
        ArrayList<Key> changed=new ArrayList<>();
        for(Key key:Key.values())if(!previous.get(key).equals(target.get(key)))changed.add(key);
        changedFields=Collections.unmodifiableList(changed);
    }
    public MontogramSettingsChange(boolean a,boolean b,long revision,boolean c,boolean d){
        this(State.defaults().with(Key.MATERIAL3,a).with(Key.MESSAGE_DETAILS,b),revision,
            State.defaults().with(Key.MATERIAL3,c).with(Key.MESSAGE_DETAILS,d));
    }
    public boolean requiresLayoutReload(){for(Key key:changedFields)if(key.layoutReload)return true;return false;}
    public boolean isCurrent(State current,long revision){return this.revision==revision&&previous.equals(current);}
    public boolean isCurrent(boolean a,boolean b,long revision){return isCurrent(State.defaults().with(Key.MATERIAL3,a).with(Key.MESSAGE_DETAILS,b),revision);}
    public Result apply(State current,long revision,StateCommitter writer){
        if(!isCurrent(current,revision))return Result.STALE;
        if(changedFields.isEmpty())return Result.NO_CHANGE;
        if(writer==null)throw new IllegalArgumentException("Missing committer");
        try{return writer.commit(target)?Result.APPLIED:Result.SAVE_FAILED;}catch(RuntimeException failed){return Result.SAVE_FAILED;}
    }
    public Result apply(boolean a,boolean b,long revision,Committer writer){
        return apply(State.defaults().with(Key.MATERIAL3,a).with(Key.MESSAGE_DETAILS,b),revision,
            state->writer.commit(state.bool(Key.MATERIAL3),state.bool(Key.MESSAGE_DETAILS)));
    }
}
