package top.yzljc.atribot.miniapp;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @Author YZ_Ljc_
 * @ClassName MiniappSessions
 * @Created_at 2026/09/19
 * @Project AtriMeow
 * @Package top.yzljc.atribot.miniapp
 */
public final class MiniappSessions implements AutoCloseable {
    public static final Duration TICKET_TTL = Duration.ofMinutes(5);
    public static final Duration IDLE_TTL = Duration.ofSeconds(90);
    public static final Duration SESSION_TTL = Duration.ofHours(2);
    private static final int CAPACITY = 4096;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Ticket> tickets = new HashMap<>();
    private final Map<String, Session> sessions = new HashMap<>();
    private final Map<String, Long> issueCooldowns = new HashMap<>();
    private final ScheduledExecutorService cleaner;
    private boolean closed;

    public record Identity(String userId, String displayName) {}
    public record Issued(String ticket, long expiresAt) {}
    public record Access(String token, Identity user, long expiresAt, long idleTimeoutMillis) {}
    private record Ticket(Identity user, long expiresAt) {}
    private record Session(Identity user, long expiresAt, long lastSeen) {}

    public MiniappSessions() { this(Clock.systemUTC(), true); }

    MiniappSessions(Clock clock, boolean scheduleCleanup) {
        this.clock = clock;
        cleaner = scheduleCleanup ? Executors.newSingleThreadScheduledExecutor(
                runnable -> Thread.ofPlatform().daemon().name("miniapp-expiry").unstarted(runnable)) : null;
        if (cleaner != null) cleaner.scheduleWithFixedDelay(this::cleanup, 30, 30, TimeUnit.SECONDS);
    }

    public synchronized Issued issue(String userId, String displayName) {
        cleanup();
        long now = clock.millis();
        if (closed || userId == null || userId.isBlank() || userId.length() > 256) return null;
        if (issueCooldowns.containsKey(userId) || issueCooldowns.size() >= CAPACITY) return null;
        if (tickets.size() >= CAPACITY) return null;
        // A newly requested link replaces this user's still-unused links, not active pages.
        tickets.values().removeIf(ticket -> ticket.user().userId().equals(userId));
        String token = token();
        long expiresAt = now + TICKET_TTL.toMillis();
        tickets.put(token, new Ticket(new Identity(userId, displayName == null ? "" : displayName), expiresAt));
        issueCooldowns.put(userId, now + 10_000);
        return new Issued(token, expiresAt);
    }

    /** Validation, consumption and creation share one lock: only one exchange can win. */
    public synchronized Access exchange(String ticketToken, String userId) {
        cleanup();
        if (closed || !validToken(ticketToken) || userId == null) return null;
        Ticket ticket = tickets.get(ticketToken);
        if (ticket == null || !ticket.user().userId().equals(userId) || sessions.size() >= CAPACITY) return null;
        tickets.remove(ticketToken);
        long now = clock.millis();
        String sessionToken = token();
        long expiresAt = now + SESSION_TTL.toMillis();
        sessions.put(sessionToken, new Session(ticket.user(), expiresAt, now));
        return new Access(sessionToken, ticket.user(), expiresAt, IDLE_TTL.toMillis());
    }

    public synchronized Identity authenticate(String token) {
        if (closed || !validToken(token)) return null;
        Session session = sessions.get(token);
        long now = clock.millis();
        if (session == null) return null;
        if (now >= session.expiresAt() || now - session.lastSeen() >= IDLE_TTL.toMillis()) {
            sessions.remove(token);
            return null;
        }
        sessions.put(token, new Session(session.user(), session.expiresAt(), now));
        return session.user();
    }

    public synchronized void revoke(String token) {
        if (validToken(token)) sessions.remove(token);
    }

    private String token() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static boolean validToken(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]{43}");
    }

    private synchronized void cleanup() {
        long now = clock.millis();
        tickets.values().removeIf(ticket -> now >= ticket.expiresAt());
        sessions.values().removeIf(session -> now >= session.expiresAt() || now - session.lastSeen() >= IDLE_TTL.toMillis());
        issueCooldowns.values().removeIf(expiry -> now >= expiry);
    }

    @Override public synchronized void close() {
        closed = true;
        if (cleaner != null) cleaner.shutdownNow();
        tickets.clear();
        sessions.clear();
        issueCooldowns.clear();
    }
}
