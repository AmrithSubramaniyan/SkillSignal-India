package in.skillsignal.session;

import java.util.UUID;

/**
 * Thread-local holder for the active user session ID extracted from
 * the {@code X-Session-Id} header or JWT {@code sessionId} claim.
 */
public final class SessionContextHolder {

    private static final ThreadLocal<UUID> CURRENT_SESSION = new ThreadLocal<>();

    private SessionContextHolder() {}

    public static void setSessionId(UUID sessionId) {
        CURRENT_SESSION.set(sessionId);
    }

    public static UUID getSessionId() {
        return CURRENT_SESSION.get();
    }

    public static void clear() {
        CURRENT_SESSION.remove();
    }
}

