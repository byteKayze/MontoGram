/* Montogram modifications, GPL-2.0-or-later. */
package org.telegram.messenger;

/** Explicit absence of an FCM project, not a claim of background delivery. */
public final class UnconfiguredPushProvider implements PushListenerController.IPushListenerServiceProvider {
    public static final UnconfiguredPushProvider INSTANCE = new UnconfiguredPushProvider();
    private UnconfiguredPushProvider() {}
    @Override public boolean hasServices() { return false; }
    @Override public String getLogTitle() { return "push provider unavailable"; }
    @Override public int getPushType() { return PushListenerController.PUSH_TYPE_FIREBASE; }
    @Override public void onRequestPushToken() {
        // Fail closed even if a caller ignores hasServices(). Never initialize Firebase.
        PushListenerController.sendRegistrationToServer(getPushType(), null);
    }
}
