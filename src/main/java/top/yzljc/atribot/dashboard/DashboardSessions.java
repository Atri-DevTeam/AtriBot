package top.yzljc.atribot.dashboard;

import top.yzljc.atribot.miniapp.MiniappSessions;
import top.yzljc.atribot.auth.UnifiedAuthentication;
import top.yzljc.atricore.auth.AccountStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 一次性绑定凭证、OAuth 状态与 Dashboard 登录会话
 *
 * @Author YZ_Ljc_
 * @ClassName DashboardSessions
 * @Created_at 2026/10/16
 * @Project AtriMeow
 * @Package top.yzljc.atribot.dashboard
 */
public final class DashboardSessions implements AutoCloseable {
    public static final String SESSION_COOKIE = "dashboard_session";
    public static final int LOGIN_SECONDS = 12 * 60 * 60;
    public static final int STATE_SECONDS = 10 * 60;
    private static final int CAPACITY = 4096;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Ticket> tickets = new HashMap<>();
    private final Map<String, Pending> states = new HashMap<>();
    private final Map<String, Login> logins = new HashMap<>();
    private final Map<String, Long> cooldowns = new HashMap<>();
    private boolean closed;

    private record Ticket(MiniappSessions.Identity identity, String appId, String botAppId, long expiresAt) {
    }

    public record Pending(MiniappSessions.Identity binding, String appId, String botAppId, String navigation,
                          long expiresAt, String authorizationRequestId) {
    }

    public record Login(MiniappSessions.Identity identity, String qqConnectOpenId, String avatarUrl, String appId,
                        String botAppId, long expiresAt) {
    }

    public DashboardSessions() {
        this(Clock.systemUTC());
    }

    DashboardSessions(Clock clock) {
        this.clock = clock;
    }

    public synchronized String issue(String userOpenId, String displayName, DashboardSettings settings) {
        cleanup();
        if (closed || userOpenId == null || userOpenId.isBlank() || userOpenId.length() > 128
                || tickets.size() >= CAPACITY || cooldowns.size() >= CAPACITY || cooldowns.containsKey(userOpenId))
            return null;
        String token = token();
        tickets.put(token, new Ticket(new MiniappSessions.Identity(userOpenId, displayName == null ? "" : displayName), settings.appId(), settings.botAppId(),
                clock.millis() + Duration.ofMinutes(5).toMillis()));
        cooldowns.put(userOpenId, clock.millis() + 10_000);
        return token;
    }

    /**
     * 在发起授权时消费绑定凭证，并建立独立的 OAuth 状态
     *
     * @param userOpenId 待绑定的官机用户标识，普通登录时为 null
     * @param ticket     一次性绑定凭证，普通登录时为 null
     * @param settings   本次授权使用的应用配置
     * @param navigation 已过滤的页面导航查询参数
     * @return OAuth state
     */
    public synchronized String begin(String userOpenId, String ticket, DashboardSettings settings, String navigation) {
        return begin(userOpenId, ticket, settings, navigation, null);
    }

    /**
     * @param requestId 已通过来源签名校验的站点授权请求标识
     * @param settings 本次授权的 QQ 应用配置
     * @return 独立的 QQ OAuth state，与普通登录及账号绑定流程隔离
     */
    public synchronized String beginAuthorization(String requestId, DashboardSettings settings) {
        if (!valid(requestId)) throw new IllegalArgumentException("AUTHORIZATION_EXPIRED");
        return begin(null, null, settings, "", requestId);
    }

    private String begin(String userOpenId, String ticket, DashboardSettings settings, String navigation,
                         String authorizationRequestId) {
        cleanup();
        if (closed || states.size() >= CAPACITY) throw new IllegalStateException("LOGIN_BUSY");
        MiniappSessions.Identity binding = null;
        if (userOpenId != null || ticket != null) {
            Ticket stored = ticket == null ? null : tickets.get(ticket);
            if (stored == null || !stored.identity().userId().equals(userOpenId) || !stored.appId().equals(settings.appId())
                    || !stored.botAppId().equals(settings.botAppId()))
                throw new IllegalArgumentException("BINDING_EXPIRED");
            binding = stored.identity();
            tickets.remove(ticket);
        }
        String state = token();
        states.put(state, new Pending(binding, settings.appId(), settings.botAppId(), navigation,
                clock.millis() + STATE_SECONDS * 1000L, authorizationRequestId));
        return state;
    }

    public synchronized Pending consume(String state, String cookie) {
        cleanup();
        if (closed || !valid(state) || !valid(cookie) || !MessageDigest.isEqual(
                state.getBytes(StandardCharsets.US_ASCII), cookie.getBytes(StandardCharsets.US_ASCII))) return null;
        return states.remove(state);
    }

    /**
     * 消费当前浏览器的站点授权页面凭证，普通登录与绑定状态不接受此交接
     *
     * @param state 页面持有的一次性状态
     * @param cookie 当前浏览器的状态 Cookie
     * @param settings 当前 QQ 应用配置
     * @return 站点授权请求，凭证无效或配置不匹配时为 null
     */
    public synchronized Pending consumeAuthorization(String state, String cookie, DashboardSettings settings) {
        cleanup();
        Pending pending = states.get(state);
        if (pending == null || pending.authorizationRequestId() == null || !pending.appId().equals(settings.appId())
                || !pending.botAppId().equals(settings.botAppId())) return null;
        return consume(state, cookie);
    }

    public synchronized String login(MiniappSessions.Identity identity, String qqConnectOpenId, String avatarUrl, DashboardSettings settings) {
        cleanup();
        if (qqConnectOpenId == null || !qqConnectOpenId.matches("[A-Za-z0-9_-]{1,128}"))
            throw new IllegalArgumentException("QQ identity response invalid");
        if (closed || logins.size() >= CAPACITY) throw new IllegalStateException("LOGIN_BUSY");
        String token = token();
        logins.put(token, new Login(identity, qqConnectOpenId, avatarUrl, settings.appId(), settings.botAppId(), clock.millis() + LOGIN_SECONDS * 1000L));
        return token;
    }

    public synchronized Login authenticate(String token, DashboardSettings settings) {
        cleanup();
        Login login = token == null || closed || !settings.available() ? null : logins.get(token);
        if (login == null || !login.appId().equals(settings.appId()) || !login.botAppId().equals(settings.botAppId()))
            return null;
        if (login.identity() != null) {
            var account = UnifiedAuthentication.getCachedQqUserAccount(login.identity().userId());
            if (account == null || account.status() != AccountStatus.ACTIVE
                    || !Objects.equals(account.qqConnectOpenId(), login.qqConnectOpenId())) {
                logins.remove(token);
                return null;
            }
        }
        return login;
    }

    public synchronized void revoke(String token) {
        if (token != null) logins.remove(token);
    }

    private void cleanup() {
        long now = clock.millis();
        tickets.values().removeIf(value -> now >= value.expiresAt());
        states.values().removeIf(value -> now >= value.expiresAt());
        logins.values().removeIf(value -> now >= value.expiresAt());
        cooldowns.values().removeIf(value -> now >= value);
    }

    private String token() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static boolean valid(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]{43}");
    }

    @Override
    public synchronized void close() {
        closed = true;
        tickets.clear();
        states.clear();
        logins.clear();
        cooldowns.clear();
    }
}
