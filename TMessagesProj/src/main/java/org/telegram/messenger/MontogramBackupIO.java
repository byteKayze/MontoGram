/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.messenger;

import android.net.Uri;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.os.Looper;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Isolates an untrusted document provider from Telegram queues. No replacement hung workers. */
public final class MontogramBackupIO {
    public enum Status { SUCCESS, FAILED, BUSY, CANCELLED, TIMED_OUT, UNAVAILABLE_AFTER_TIMEOUT }
    /** Owner calls cancel on the UI thread; cleanup is isolated from that thread. */
    public interface Handle { void cancel(); }
    private static final Handle NOOP = () -> {};
    public interface Callback { void complete(Status status, MontogramSettingsBackup.Snapshot snapshot); }
    private static ThreadFactory threads(String name) {
        return task -> { Thread thread = new Thread(task, name); thread.setDaemon(true); return thread; };
    }
    private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS,
        new SynchronousQueue<>(), threads("MontogramBackupIO"));
    private static final ScheduledThreadPoolExecutor TIMER = new ScheduledThreadPoolExecutor(1, threads("MontogramBackupDeadline"));
    private static final AtomicBoolean unavailable = new AtomicBoolean();
    private static final AtomicBoolean cancelCleanupPending = new AtomicBoolean();
    static {
        TIMER.setRemoveOnCancelPolicy(true);
        TIMER.setKeepAliveTime(30, TimeUnit.SECONDS);
        TIMER.allowCoreThreadTimeOut(true);
    }
    private MontogramBackupIO() {}
    public static boolean isAvailable() { return !unavailable.get(); }
    public static boolean isBusy() { return cancelCleanupPending.get() || IO.getActiveCount() > 0; }

    private static final class Request implements Handle {
        final AtomicBoolean terminal = new AtomicBoolean();
        final CancellationSignal signal = new CancellationSignal();
        final AtomicReference<ParcelFileDescriptor> descriptor = new AtomicReference<>();
        final Callback callback;
        volatile ScheduledFuture<?> timeout;
        Request(Callback callback) { this.callback = callback; }
        void deliver(Status status, MontogramSettingsBackup.Snapshot value) {
            AndroidUtilities.runOnUIThread(() -> callback.complete(status, value));
        }
        void finish(Status status, MontogramSettingsBackup.Snapshot value) {
            if (!terminal.compareAndSet(false, true)) return;
            ScheduledFuture<?> pending = timeout;
            if (pending != null) pending.cancel(false);
            deliver(status, value);
        }
        void closeDescriptor() {
            ParcelFileDescriptor fd = descriptor.getAndSet(null);
            if (fd != null) try { fd.close(); } catch (Exception ignored) { }
        }
        @Override public void cancel() {
            if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("UI thread required");
            if (!terminal.compareAndSet(false, true)) return;
            ScheduledFuture<?> pending = timeout;
            if (pending != null) pending.cancel(false);
            cancelCleanupPending.set(true);
            // Admit no new work until cleanup leaves the deadline thread available again.
            TIMER.execute(() -> {
                try { closeDescriptor(); signal.cancel(); }
                catch (Exception ignored) { }
                finally { cancelCleanupPending.set(false); }
            });
            deliver(Status.CANCELLED, null);
        }
        void expire() {
            if (!terminal.compareAndSet(false, true)) return;
            unavailable.set(true); // Containment: require process restart, never spawn another IO worker.
            deliver(Status.TIMED_OUT, null);
            closeDescriptor();
            try { signal.cancel(); } catch (Exception ignored) { }
        }
    }

    public static Handle transfer(Uri uri, boolean exporting, MontogramSettingsBackup.Snapshot snapshot, Callback callback) {
        if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("UI thread required");
        if (callback == null) throw new IllegalArgumentException("Missing callback");
        if (uri == null || !"content".equals(uri.getScheme()) || (exporting && snapshot == null)) {
            AndroidUtilities.runOnUIThread(() -> callback.complete(Status.FAILED, null));
            return NOOP;
        }
        if (unavailable.get()) {
            AndroidUtilities.runOnUIThread(() -> callback.complete(Status.UNAVAILABLE_AFTER_TIMEOUT, null));
            return NOOP;
        }
        if (cancelCleanupPending.get()) {
            AndroidUtilities.runOnUIThread(() -> callback.complete(Status.BUSY, null));
            return NOOP;
        }
        Request request = new Request(callback);
        request.timeout = TIMER.schedule(request::expire, 15, TimeUnit.SECONDS);
        try {
            IO.execute(() -> {
                MontogramSettingsBackup.Snapshot result = null;
                Status status = Status.FAILED;
                try {
                    if (request.terminal.get()) return;
                    if (unavailable.get()) { status = Status.UNAVAILABLE_AFTER_TIMEOUT; return; }
                    ParcelFileDescriptor fd = ApplicationLoader.applicationContext.getContentResolver()
                        .openFileDescriptor(uri, exporting ? "wt" : "r", request.signal);
                    if (fd == null) throw new java.io.IOException("Missing descriptor");
                    request.descriptor.set(fd);
                    if (request.terminal.get()) return;
                    if (exporting) {
                        if (snapshot == null) throw new java.io.IOException("Missing snapshot");
                        try (OutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(fd)) {
                            output.write(snapshot.encode());
                        }
                    } else {
                        try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(fd)) {
                            result = MontogramSettingsBackup.read(input);
                        }
                    }
                    status = Status.SUCCESS;
                } catch (Exception invalid) { /* Never log URI, document contents or provider exceptions. */ }
                finally {
                    request.closeDescriptor();
                    request.finish(status, result);
                }
            });
        } catch (RejectedExecutionException busy) { request.finish(Status.BUSY, null); }
        return request;
    }
}
