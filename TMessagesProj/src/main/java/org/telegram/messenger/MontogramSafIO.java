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

/** One shared byte-only SAF worker. No codecs, owner policy, merges or URI persistence. */
public final class MontogramSafIO {
    public enum Status { SUCCESS, FAILED, BUSY, CANCELLED, TIMED_OUT, UNAVAILABLE_AFTER_TIMEOUT }
    /** Owner calls cancel on the UI thread; cleanup is isolated from that thread. */
    public interface Handle { void cancel(); }
    private static final Handle NOOP = () -> {};
    public interface Callback { void complete(Status status, byte[] bytes); }
    private static ThreadFactory threads(String name) {
        return task -> { Thread thread = new Thread(task, name); thread.setDaemon(true); return thread; };
    }
    private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS,
        new SynchronousQueue<>(), threads("MontogramSafIO"));
    private static final ScheduledThreadPoolExecutor TIMER = new ScheduledThreadPoolExecutor(1, threads("MontogramSafDeadline"));
    private static final AtomicBoolean unavailable = new AtomicBoolean();
    private static final AtomicBoolean cancelCleanupPending = new AtomicBoolean();
    static {
        TIMER.setRemoveOnCancelPolicy(true);
        TIMER.setKeepAliveTime(30, TimeUnit.SECONDS);
        TIMER.allowCoreThreadTimeOut(true);
    }
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    private MontogramSafIO() {}
    public static boolean isAvailable() { return !unavailable.get(); }
    public static boolean isBusy() { return cancelCleanupPending.get() || IO.getActiveCount() > 0; }

    private static final class WipeBuffer extends java.io.ByteArrayOutputStream {
        WipeBuffer(int capacity){super(capacity);}
        void wipe(){java.util.Arrays.fill(buf,(byte)0);reset();}
    }

    private static final class Request implements Handle {
        final AtomicBoolean terminal = new AtomicBoolean();
        final CancellationSignal signal = new CancellationSignal();
        final AtomicReference<ParcelFileDescriptor> descriptor = new AtomicReference<>();
        final Callback callback;
        final Object buffers = new Object();
        final byte[] payload, chunk = new byte[8192];
        final WipeBuffer accumulated;
        byte[] result;
        volatile ScheduledFuture<?> timeout;
        Request(Callback callback, byte[] outgoing, int readLimit) { this.callback = callback; payload = outgoing == null ? null : outgoing.clone(); accumulated = new WipeBuffer(outgoing == null ? readLimit + 1 : 0); }
        void wipeLocked() {
            if(payload != null) java.util.Arrays.fill(payload,(byte)0);
            java.util.Arrays.fill(chunk,(byte)0); accumulated.wipe();
            if(result != null) java.util.Arrays.fill(result,(byte)0); result = null;
        }
        void wipeRaw() { synchronized(buffers) { wipeLocked(); } }
        void deliver(Status status, byte[] value) {
            AndroidUtilities.runOnUIThread(() -> callback.complete(status, value));
        }
        void finish(Status status) {
            byte[] value = null;
            synchronized(buffers) {
                if(!terminal.compareAndSet(false,true)) { wipeLocked(); return; }
                if(status == Status.SUCCESS) { value = result; result = null; }
                wipeLocked();
            }
            ScheduledFuture<?> pending = timeout;
            if(pending != null) pending.cancel(false);
            deliver(status,value);
        }
        void closeDescriptor() {
            ParcelFileDescriptor fd = descriptor.getAndSet(null);
            if (fd != null) try { fd.close(); } catch (Exception ignored) { }
        }
        @Override public void cancel() {
            if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("UI thread required");
            if (!terminal.compareAndSet(false, true)) return;
            wipeRaw(); // Independent of a provider that never returns or closes.
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
            wipeRaw();
            unavailable.set(true); // Containment: require process restart, never spawn another IO worker.
            deliver(Status.TIMED_OUT, null);
            closeDescriptor();
            try { signal.cancel(); } catch (Exception ignored) { }
        }
    }

    public static Handle transfer(Uri uri, byte[] outgoing, int readLimit, Callback callback) {
        if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("UI thread required");
        if (callback == null) throw new IllegalArgumentException("Missing callback");
        final boolean exporting = outgoing != null;
        if (uri == null || !"content".equals(uri.getScheme()) || readLimit < 1 || readLimit > MAX_BYTES || (exporting && outgoing.length > MAX_BYTES)) {
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
        Request request = new Request(callback,outgoing,readLimit);
        request.timeout = TIMER.schedule(request::expire, 15, TimeUnit.SECONDS);
        try {
            IO.execute(() -> {
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
                        try (OutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(fd)) {
                            for(int offset=0;offset<request.payload.length;) {
                                if(request.terminal.get())return;
                                int length=Math.min(16384,request.payload.length-offset);output.write(request.payload,offset,length);offset+=length;
                            }
                        }
                    } else {
                        try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(fd)) {
                            for(;;) {
                                int remaining;
                                synchronized(request.buffers) {
                                    if(request.terminal.get()) return;
                                    remaining=Math.min(request.chunk.length,readLimit+1-request.accumulated.size());
                                }
                                int count=input.read(request.chunk,0,remaining); // Never hold the wipe lock across provider IO.
                                synchronized(request.buffers) {
                                    if(request.terminal.get()) { request.wipeLocked(); return; }
                                    if(count == -1) { request.result=request.accumulated.toByteArray(); break; }
                                    if(count == 0) throw new java.io.IOException("Provider made no progress");
                                    request.accumulated.write(request.chunk,0,count);
                                    java.util.Arrays.fill(request.chunk,(byte)0);
                                    if(request.accumulated.size()>readLimit) throw new java.io.IOException("Document too large");
                                }
                            }
                        }
                    }
                    status = Status.SUCCESS;
                } catch (Exception invalid) { /* Never log URI, document contents or provider exceptions. */ }
                finally {
                    request.closeDescriptor();
                    request.finish(status);
                }
            });
        } catch (RejectedExecutionException busy) { request.finish(Status.BUSY); }
        return request;
    }
}
