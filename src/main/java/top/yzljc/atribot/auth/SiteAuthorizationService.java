package top.yzljc.atribot.auth;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import top.yzljc.atribot.dashboard.DashboardSettings;
import top.yzljc.atribot.webui.Result;
import top.yzljc.atribot.webui.ApiCode;
import top.yzljc.atricore.auth.AccountStatus;
import top.yzljc.atricore.auth.AtriAccount;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Clock;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 维护站点授权请求和一次性授权码，不签发其他站点的登录会话
 *
 * @Author YZ_Ljc_
 * @ClassName SiteAuthorizationService
 * @Created_at 2026/10/20
 * @Project AtriMeow
 * @Package top.yzljc.atribot.auth
 */
public final class SiteAuthorizationService implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final int CAPACITY = 4096;
    private static final int REQUEST_SECONDS = 300;
    private static final int LOGIN_SECONDS = 600;
    private static final int CODE_SECONDS = 60;
    private final Supplier<SiteAuthorizationSettings> configuration;
    private final Accounts accounts;
    private final Clock clock;
    private final SiteAccountBroadcaster updates;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Request> requests = new HashMap<>();
    private final Map<String, Code> codes = new HashMap<>();
    private final Map<Nonce, Long> nonces = new HashMap<>();
    private boolean closed;

    @FunctionalInterface
    interface Accounts {
        AtriAccount find(String qqConnectOpenId) throws SQLException;
    }

    public record Envelope(String client_id, String payload, String signature) {
    }

    public record ExchangeResult(String body, String signature) {
    }

    private record Nonce(String purpose, String clientId, String value) {
    }

    private record Application(String appId, String botAppId) {
        static Application of(DashboardSettings settings) {
            return new Application(settings.appId(), settings.botAppId());
        }
    }

    private record Request(String id, String clientId, SiteAuthorizationSettings.Client client, String redirectUri,
                           String state, String challenge, Application application, long expiresAt) {
    }

    private record Code(Request request, String qqOpenId, String avatarUrl, long expiresAt) {
    }

    public static final class Failure extends RuntimeException {
        private final ApiCode code;

        Failure(ApiCode code) {
            super(code.getMessage());
            this.code = code;
        }

        public ApiCode code() {
            return code;
        }
    }

    public SiteAuthorizationService(Supplier<SiteAuthorizationSettings> configuration) {
        this(configuration, UnifiedAuthentication::findByQqConnect, Clock.systemUTC(), null);
    }

    public SiteAuthorizationService(Supplier<SiteAuthorizationSettings> configuration, SiteAccountBroadcaster updates) {
        this(configuration, UnifiedAuthentication::findByQqConnect, Clock.systemUTC(), updates);
    }

    SiteAuthorizationService(Supplier<SiteAuthorizationSettings> configuration, Accounts accounts, Clock clock) {
        this(configuration, accounts, clock, null);
    }

    SiteAuthorizationService(Supplier<SiteAuthorizationSettings> configuration, Accounts accounts, Clock clock,
                             SiteAccountBroadcaster updates) {
        this.configuration = configuration;
        this.accounts = accounts;
        this.clock = clock;
        this.updates = updates;
    }

    /**
     * @param envelope 接入方签名请求
     * @param settings 当前 QQ 登录配置
     * @return 服务端授权请求标识，供 QQ 登录回调使用
     * @throws Failure 请求无效、重放或服务容量不足
     */
    public synchronized String begin(Envelope envelope, DashboardSettings settings) {
        cleanup();
        if (updates != null) updates.requireAvailable();
        var client = client(envelope == null ? null : envelope.client_id());
        JsonNode payload = verify(envelope, client, "authorize");
        fields(payload, Set.of("redirect_uri", "state", "expires_at", "nonce", "code_challenge"));
        String redirect = text(payload, "redirect_uri", 2048);
        String state = text(payload, "state", 256);
        String challenge = text(payload, "code_challenge", 43);
        if (!client.redirectUris().contains(redirect) || !challenge.matches("[A-Za-z0-9_-]{43}"))
            throw new Failure(ApiCode.AUTHORIZATION_INVALID_REQUEST);
        if (!settings.available()) throw new Failure(ApiCode.AUTHORIZATION_DISABLED);
        if (requests.size() >= CAPACITY) throw new Failure(ApiCode.AUTHORIZATION_BUSY);
        rememberNonce("authorize", envelope.client_id(), payload);
        String id = token();
        requests.put(id, new Request(id, envelope.client_id(), client, redirect, state, challenge,
                Application.of(settings), clock.instant().getEpochSecond() + LOGIN_SECONDS));
        return id;
    }

    /**
     * @param requestId 已验签的站点授权请求标识
     * @param qqOpenId  QQ 服务端确认的 OpenID，失败时为 null
     * @param avatarUrl QQ 头像地址
     * @param error     失败原因，成功时为 null
     * @param settings  当前 QQ 登录配置
     * @return 携带一次性授权码或失败原因的接入方返回地址
     * @throws Failure 请求已失效或接入配置已变更
     */
    public synchronized String complete(String requestId, String qqOpenId, String avatarUrl, String error,
                                        DashboardSettings settings) {
        cleanup();
        Request request = requests.remove(requestId);
        if (request == null) throw new Failure(ApiCode.AUTHORIZATION_EXPIRED);
        current(request, settings);
        String query = "?state=" + encode(request.state());
        if (error != null) return request.redirectUri() + query + "&error=" + encode(error);
        if (qqOpenId == null || !qqOpenId.matches("[A-Za-z0-9_-]{1,128}"))
            throw new Failure(ApiCode.AUTHORIZATION_INVALID_IDENTITY);
        try {
            ApiCode accountError = accountError(accounts.find(qqOpenId));
            if (accountError != null) return request.redirectUri() + query + "&error=" + accountError.name();
        } catch (SQLException failure) {
            throw new Failure(ApiCode.AUTHORIZATION_FAILED);
        }
        if (codes.size() >= CAPACITY) return request.redirectUri() + query + "&error=AUTHORIZATION_BUSY";
        String code = token();
        codes.put(code, new Code(request, qqOpenId, avatarUrl, clock.instant().getEpochSecond() + CODE_SECONDS));
        return request.redirectUri() + query + "&code=" + code;
    }

    /**
     * @param envelope 接入方签名的授权码兑换请求
     * @param settings 当前 QQ 登录配置
     * @return 用户信息 JSON 和响应签名
     * @throws Failure      签名、授权码、PKCE 或当前账号状态校验失败
     * @throws SQLException 读取统一账号失败
     */
    public synchronized ExchangeResult exchange(Envelope envelope, DashboardSettings settings) throws SQLException {
        cleanup();
        var client = client(envelope == null ? null : envelope.client_id());
        JsonNode payload = verify(envelope, client, "exchange");
        fields(payload, Set.of("code", "redirect_uri", "code_verifier", "expires_at", "nonce"));
        String token = text(payload, "code", 43);
        String redirect = text(payload, "redirect_uri", 2048);
        String verifier = text(payload, "code_verifier", 128);
        if (!token.matches("[A-Za-z0-9_-]{43}") || !verifier.matches("[A-Za-z0-9._~-]{43,128}"))
            throw new Failure(ApiCode.AUTHORIZATION_INVALID_GRANT);
        Code code = codes.get(token);
        if (code == null || !envelope.client_id().equals(code.request().clientId())
                || !redirect.equals(code.request().redirectUri()) || !equal(challenge(verifier), code.request().challenge()))
            throw new Failure(ApiCode.AUTHORIZATION_INVALID_GRANT);
        current(code.request(), settings);
        rememberNonce("exchange", envelope.client_id(), payload);
        AtriAccount account = accounts.find(code.qqOpenId());
        ApiCode accountError = accountError(account);
        if (accountError != null) {
            codes.remove(token);
            throw new Failure(accountError);
        }
        long now = clock.instant().getEpochSecond();
        if (now >= code.expiresAt()) {
            codes.remove(token);
            throw new Failure(ApiCode.AUTHORIZATION_INVALID_GRANT);
        }
        var result = JSON.createObjectNode();
        result.put("client_id", envelope.client_id());
        result.put("request_id", code.request().id());
        result.put("state", code.request().state());
        result.put("issued_at", now);
        result.put("expires_at", now + CODE_SECONDS);
        var snapshot = updates == null ? new SiteAccountBroadcaster.Snapshot(
                SiteAccountBroadcaster.account(settings.appId(), code.qqOpenId(), account), clock.millis(), "")
                : updates.snapshot(settings.appId(), code.qqOpenId(), account);
        if (snapshot.user().accountId() == null || snapshot.user().userOpenId() == null || snapshot.user().userOpenId().isBlank()) {
            codes.remove(token);
            throw new Failure(ApiCode.ACCOUNT_NOT_REGISTERED);
        }
        if (!AccountStatus.ACTIVE.name().equals(snapshot.user().status())) {
            codes.remove(token);
            throw new Failure(ApiCode.ACCOUNT_RESTRICTED);
        }
        var user = SiteAccountBroadcaster.userJson(snapshot);
        user.put("avatar_url", code.avatarUrl());
        result.set("user", user);
        String body = JSON.valueToTree(Result.success(result, code.request().id())).toString();
        String signature = sign(client.secret(), "result", envelope.client_id(), encodePayload(body));
        codes.remove(token);
        return new ExchangeResult(body, signature);
    }

    /**
     * @param secret   接入方的服务端密钥
     * @param purpose  协议用途，取 authorize、exchange、result 或 account_patch
     * @param clientId 接入方标识
     * @param payload  Base64URL 编码后的原始 JSON
     * @return HMAC-SHA256 的无填充 Base64URL 签名
     */
    public static String sign(String secret, String purpose, String clientId, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return ENCODER.encodeToString(mac.doFinal(("atri-site-auth-v1\n" + purpose + "\n" + clientId + "\n" + payload)
                    .getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("站点授权签名不可用", e);
        }
    }

    /**
     * @param json 原始 JSON 文本，签名与验签时必须使用相同字节
     * @return UTF-8 编码后无填充的 Base64URL 字符串
     */
    public static String encodePayload(String json) {
        return ENCODER.encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @param verifier 来源站后端生成并保留的 PKCE 校验字符串
     * @return S256 方式的 PKCE code_challenge
     */
    public static String challenge(String verifier) {
        try {
            return ENCODER.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    private SiteAuthorizationSettings.Client client(String id) {
        var settings = configuration.get();
        if (closed || settings == null || !settings.enabled()) throw new Failure(ApiCode.AUTHORIZATION_DISABLED);
        var client = id == null || !id.matches("[A-Za-z0-9_-]{1,64}") ? null : settings.clients().get(id);
        if (client == null) throw new Failure(ApiCode.AUTHORIZATION_INVALID_CLIENT);
        return client;
    }

    private static ApiCode accountError(AtriAccount account) {
        if (account == null || account.qqUserOpenId() == null || account.qqUserOpenId().isBlank())
            return ApiCode.ACCOUNT_NOT_REGISTERED;
        return account.status() == AccountStatus.ACTIVE ? null : ApiCode.ACCOUNT_RESTRICTED;
    }

    private JsonNode verify(Envelope envelope, SiteAuthorizationSettings.Client client, String purpose) {
        if (envelope.payload() == null || envelope.payload().isEmpty() || envelope.payload().length() > 8192
                || !envelope.payload().matches("[A-Za-z0-9_-]+") || envelope.signature() == null
                || !envelope.signature().matches("[A-Za-z0-9_-]{43}")
                || !equal(sign(client.secret(), purpose, envelope.client_id(), envelope.payload()), envelope.signature()))
            throw new Failure(ApiCode.AUTHORIZATION_INVALID_SIGNATURE);
        try {
            JsonNode payload = JSON.readTree(DECODER.decode(envelope.payload()));
            if (payload == null || !payload.isObject()) throw new Failure(ApiCode.AUTHORIZATION_INVALID_REQUEST);
            JsonNode expires = payload.get("expires_at");
            long now = clock.instant().getEpochSecond();
            if (expires == null || !expires.isIntegralNumber() || !expires.canConvertToLong()
                    || expires.longValue() <= now || expires.longValue() > now + REQUEST_SECONDS)
                throw new Failure(ApiCode.AUTHORIZATION_REQUEST_EXPIRED);
            return payload;
        } catch (IOException | IllegalArgumentException invalid) {
            throw new Failure(ApiCode.AUTHORIZATION_INVALID_REQUEST);
        }
    }

    private void current(Request request, DashboardSettings settings) {
        if (!settings.available() || !Application.of(settings).equals(request.application())
                || !client(request.clientId()).equals(request.client()))
            throw new Failure(ApiCode.AUTHORIZATION_EXPIRED);
    }

    private void rememberNonce(String purpose, String id, JsonNode payload) {
        String value = text(payload, "nonce", 128);
        if (!value.matches("[A-Za-z0-9_-]{22,128}")) throw new Failure(ApiCode.AUTHORIZATION_INVALID_REQUEST);
        Nonce nonce = new Nonce(purpose, id, value);
        if (nonces.containsKey(nonce)) throw new Failure(ApiCode.AUTHORIZATION_REQUEST_REPLAYED);
        if (nonces.size() >= CAPACITY * 2) throw new Failure(ApiCode.AUTHORIZATION_BUSY);
        nonces.put(nonce, payload.path("expires_at").longValue());
    }

    private static void fields(JsonNode payload, Set<String> allowed) {
        var names = payload.fieldNames();
        while (names.hasNext())
            if (!allowed.contains(names.next())) throw new Failure(ApiCode.AUTHORIZATION_INVALID_REQUEST);
    }

    private static String text(JsonNode payload, String key, int limit) {
        JsonNode value = payload.get(key);
        if (value == null || !value.isTextual() || value.textValue().isBlank() || value.textValue().length() > limit
                || value.textValue().chars().anyMatch(c -> c < 32 || c == 127))
            throw new Failure(ApiCode.AUTHORIZATION_INVALID_REQUEST);
        return value.textValue();
    }

    private static boolean equal(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), actual.getBytes(StandardCharsets.US_ASCII));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String token() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    private void cleanup() {
        long now = clock.instant().getEpochSecond();
        requests.values().removeIf(request -> request.expiresAt() <= now);
        codes.values().removeIf(code -> code.expiresAt() <= now);
        nonces.values().removeIf(expires -> expires <= now);
    }

    synchronized void discard(String requestId) {
        requests.remove(requestId);
    }

    @Override
    public synchronized void close() {
        closed = true;
        requests.clear();
        codes.clear();
        nonces.clear();
    }
}
