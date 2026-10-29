/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.messenger;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.Arrays;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/** Process-only owner-scoped immutable data storage. No files, preferences, keys, callbacks or queues.
 * Typed callers must validate active/selected account, self ID, lock and UI lifecycle
 * before capturing an owner and again before presenting or inserting data.
 */
final class MontogramOwnerMemory<T> {
    private static final ClearToken[] EMPTY_CLEARS = new ClearToken[0];

    private static final class Cell<T> {
        final long epoch, self;
        final int clearing;
        final boolean failed;
        final T data;
        final ClearToken[] liveClears;
        Cell(long epoch, long self, int clearing, boolean failed, T data) {
            this(epoch, self, EMPTY_CLEARS, failed, data);
            if (clearing != 0) throw new IllegalArgumentException("Missing clear generations");
        }
        Cell(long epoch, long self, ClearToken[] liveClears, boolean failed, T data) {
            this.epoch = epoch; this.self = self; this.clearing = liveClears.length; this.failed = failed; this.data = data;
            this.liveClears = liveClears;
        }
    }
    static final class Snapshot<T> {
        private final MontogramOwnerMemory<T> owner;
        final int account;
        final long self;
        private final long epoch;
        final T data;
        private Snapshot(MontogramOwnerMemory<T> owner, int account, Cell<T> cell) {
            this.owner = owner; this.account = account; self = cell.self; epoch = cell.epoch; data = cell.data;
        }
    }
    public static final class ClearToken {
        private final MontogramOwnerMemory<?> owner;
        private final int account;
        private final long epoch;
        private final AtomicBoolean finished = new AtomicBoolean();
        private ClearToken(MontogramOwnerMemory<?> owner, int account, long epoch) { this.owner = owner; this.account = account; this.epoch = epoch; }
    }
    private final AtomicReferenceArray<Cell<T>> slots;

    private final Supplier<T> empty;
    MontogramOwnerMemory(int accounts, Supplier<T> empty) {
        if(empty==null||empty.get()==null)throw new IllegalArgumentException("Missing empty data");
        this.empty=empty;
        if (accounts < 1 || accounts > 256) throw new IllegalArgumentException("Invalid account bound");
        slots = new AtomicReferenceArray<>(accounts);
        for (int i = 0; i < accounts; i++) slots.set(i, new Cell<>(0, 0, 0, false, empty.get()));
    }
    private boolean valid(int account) { return account >= 0 && account < slots.length(); }

    /** CAS model only. Typed access gateways validate Android authorization.
     * Completion of a native clear never creates an owner capability itself.
     */
    Snapshot<T> bind(int account, long self) {
        if (!valid(account) || self <= 0) return null;
        while (true) {
            Cell<T> previous = slots.get(account);
            if (previous.failed || previous.clearing != 0 || previous.epoch >= Long.MAX_VALUE - 1) return null;
            if (previous.self == self) return new Snapshot<>(this, account, previous);
            Cell<T> bound = new Cell<>(previous.epoch + 1, self, 0, false, empty.get());
            if (slots.compareAndSet(account, previous, bound)) return new Snapshot<>(this, account, bound);
        }
    }
    boolean isCurrent(Snapshot<T> snapshot) {
        if (snapshot == null || snapshot.owner != this || !valid(snapshot.account)) return false;
        Cell<T> cell = slots.get(snapshot.account);
        return !cell.failed && cell.clearing == 0 && cell.self == snapshot.self && cell.epoch == snapshot.epoch && cell.data == snapshot.data;
    }
    Snapshot<T> mutate(Snapshot<T> previous, UnaryOperator<T> change) {
        if (!isCurrent(previous)) return null;
        Cell<T> current = slots.get(previous.account);
        if (current.data != previous.data || current.epoch != previous.epoch || current.self != previous.self || current.clearing != 0 || current.failed) return null;
        T target = change.apply(current.data);
        if (target == current.data) return isCurrent(previous) ? previous : null;
        Cell<T> updated = new Cell<>(current.epoch, current.self, 0, false, target);
        return slots.compareAndSet(previous.account, current, updated) ? new Snapshot<>(this, previous.account, updated) : null;
    }
    /** Used only after a separately verified retained-data read, never as owner validation. */
    Snapshot<T> replace(Snapshot<T> previous, T target) {
        if (target == null) throw new IllegalArgumentException("Missing data");
        if (!isCurrent(previous)) return null;
        Cell<T> current = slots.get(previous.account);
        if (current.data != previous.data || current.epoch != previous.epoch || current.self != previous.self ||
                current.clearing != 0 || current.failed || current.epoch >= Long.MAX_VALUE - 1) return null;
        // A retained read may have a lower revision than memory. Advance the
        // capability generation even for a mode transition with the same data.
        Cell<T> loaded = new Cell<>(current.epoch + 1, current.self, 0, false, target);
        return slots.compareAndSet(previous.account, current, loaded) ? new Snapshot<>(this, previous.account, loaded) : null;
    }

    /** Revoke UI capabilities while retaining process-local data for fresh auth. */
    void revoke(Snapshot<T> previous) {
        if (!isCurrent(previous)) return;
        while (true) {
            Cell<T> current = slots.get(previous.account);
            if (current.epoch != previous.epoch || current.self != previous.self || current.data != previous.data || current.clearing != 0 || current.failed) return;
            boolean exhausted = current.epoch >= Long.MAX_VALUE - 1;
            Cell<T> revoked = new Cell<>(exhausted ? Long.MAX_VALUE : current.epoch + 1,
                exhausted ? 0 : current.self, 0, exhausted, exhausted ? empty.get() : current.data);
            if (slots.compareAndSet(previous.account, current, revoked)) return;
        }
    }

    /** At most one small Cell replacement per active account, no I/O or waiting. */
    void revokeAll() {
        for (int account = 0; account < slots.length(); account++) {
            while (true) {
                Cell<T> previous = slots.get(account);
                if (previous.self <= 0 || previous.failed || previous.clearing != 0) break;
                boolean exhausted = previous.epoch >= Long.MAX_VALUE - 1;
                Cell<T> revoked = new Cell<>(exhausted ? Long.MAX_VALUE : previous.epoch + 1,
                    exhausted ? 0 : previous.self, 0, exhausted, exhausted ? empty.get() : previous.data);
                if (slots.compareAndSet(account, previous, revoked)) break;
            }
        }
    }

    /** First operation of native clear: revoke all leases and drop memory synchronously. */
    public ClearToken beginClear(int account) {
        if (!valid(account)) return null;
        while (true) {
            Cell<T> previous = slots.get(account);
            boolean exhausted = previous.epoch == Long.MAX_VALUE || previous.clearing >= 64;
            long epoch = exhausted ? Long.MAX_VALUE : previous.epoch + 1;
            ClearToken token = exhausted ? null : new ClearToken(this, account, epoch);
            ClearToken[] live = exhausted ? previous.liveClears : Arrays.copyOf(previous.liveClears, previous.clearing + 1);
            if (token != null) live[live.length - 1] = token;
            Cell<T> cleared = new Cell<>(epoch, 0, live, previous.failed || exhausted, empty.get());
            if (slots.compareAndSet(account, previous, cleared)) return token;
        }
    }
    /** Cleanup acknowledgement only: leaves self=0/data empty, never authorizes access.
     * Nested/repeated clears stay unavailable until every live clear completes.
     * Any unexpected native failure poisons the slot for this process.
     */
    public boolean finishClear(ClearToken token, boolean nativeCompleted) {
        if (token == null || token.owner != this || !token.finished.compareAndSet(false, true)) return false;
        while (true) {
            Cell<T> previous = slots.get(token.account);
            int index = -1;
            for (int i = 0; i < previous.liveClears.length; i++) {
                if (previous.liveClears[i] == token && previous.liveClears[i].epoch == token.epoch) { index = i; break; }
            }
            if (index < 0) return false;
            ClearToken[] remaining = new ClearToken[previous.clearing - 1];
            System.arraycopy(previous.liveClears, 0, remaining, 0, index);
            System.arraycopy(previous.liveClears, index + 1, remaining, index, remaining.length - index);
            Cell<T> cleaned = new Cell<>(previous.epoch, 0, remaining,
                previous.failed || !nativeCompleted, empty.get());
            if (slots.compareAndSet(token.account, previous, cleaned)) return true;
        }
    }
}
