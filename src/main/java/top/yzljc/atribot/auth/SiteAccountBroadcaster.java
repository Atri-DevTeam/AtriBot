package top.yzljc.atribot.auth;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.utils.AtomicFiles;
import top.yzljc.atribot.webui.ApiCode;
import top.yzljc.atricore.auth.AccountStatus;
import top.yzljc.atricore.auth.AtriAccount;

/**
 * 串行投递统一账号变更，合并同一站点和身份的待发送状态
 *
 * @Author YZ_Ljc_
 * @ClassName SiteAccountBroadcaster
 * @Created_at 2026/10/21
 * @Project AtriMeow
 * @Package top.yzljc.atribot.auth
 */
public final class SiteAccountBroadcaster implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(SiteAccountBroadcaster.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_SUBJECTS = 8192;
    private static final int MAX_DELIVERIES = 32768;
    private final Supplier<SiteAuthorizationSettings> configuration;
    private final Supplier<String> appId;
    private final Path file;
    private final Clock clock;
    private final Transport transport;
    private final HttpClient http;
    private final ScheduledExecutorService worker;
    private final Map<String, Snapshot> snapshots = new HashMap<>();
    private final Map<String, Delivery> deliveries = new LinkedHashMap<>();
    private Map<String, SiteAuthorizationSettings.Client> clients = Map.of();
    private long lastVersion;
    private long generation;
    private long persistedGeneration;
    private boolean started;
    private boolean closed;
    private boolean storageReadable = true;

    public record Account(String appId, String openId, String accountId, String username, String userOpenId,
                          String role, List<String> permissions, String status) {
        public Account {
            permissions = permissions == null ? List.of() : permissions.stream().filter(Objects::nonNull).distinct().sorted().toList();
        }
    }

    public record Snapshot(Account user, long version, String eventId) {}
    private record Delivery(String clientId, Snapshot snapshot, int attempts, long nextAttempt) {}
    private record Stored(long lastVersion, Map<String, Snapshot> snapshots, Map<String, Delivery> deliveries) {}

    @FunctionalInterface
    interface Transport {
        int send(String url, String body, String signature) throws Exception;
    }

    public SiteAccountBroadcaster(Supplier<SiteAuthorizationSettings> configuration, Supplier<String> appId, Path file) {
        this(configuration, appId, file, Clock.systemUTC(), null);
    }

    SiteAccountBroadcaster(Supplier<SiteAuthorizationSettings> configuration, Supplier<String> appId, Path file,
                           Clock clock, Transport transport) {
        this.configuration = configuration;
        this.appId = appId;
        this.file = file;
        this.clock = clock;
        http = transport == null ? HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER).build() : null;
        this.transport = transport == null ? this::send : transport;
        worker = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon(true).name("site-account-patch").factory());
        if (Files.exists(file)) {
            try {
                if (Files.size(file) > 64L * 1024 * 1024) throw new IOException("站点变更暂存文件超过 64 MiB");
                Stored stored = JSON.readValue(Files.readAllBytes(file), Stored.class);
                if (stored.snapshots().size() > MAX_SUBJECTS || stored.deliveries().size() > MAX_DELIVERIES)
                    throw new IOException("站点变更暂存数据超过容量限制");
                snapshots.putAll(stored.snapshots());
                deliveries.putAll(stored.deliveries());
                lastVersion = stored.lastVersion();
            } catch (IOException | RuntimeException failure) {
                storageReadable = false;
                LOG.error("读取站点变更暂存数据失败，站点授权与广播已停用，原文件保留", failure);
            }
        }
    }

    /**
     * @param accounts 启动时已加载的统一账号，用于恢复当前状态及删除、解绑结果
     */
    public synchronized void start(Collection<AtriAccount> accounts) {
        if (closed || started || !storageReadable) return;
        try { initialize(accounts); }
        catch (RuntimeException failure) {
            storageReadable = false;
            LOG.error("初始化站点账号广播失败，站点授权与广播已停用", failure);
        }
    }

    private void initialize(Collection<AtriAccount> accounts) {
        var current = new HashMap<String, AtriAccount>();
        for (AtriAccount account : accounts) current.put(account.uuid().toString(), account);
        for (Snapshot old : new ArrayList<>(snapshots.values())) {
            Account user = old.user();
            if (!Objects.equals(user.appId(), appId.get()) || user.accountId() == null) continue;
            AtriAccount account = current.get(user.accountId());
            if (account == null) put(deleted(user));
            else if (!Objects.equals(user.openId(), account.qqConnectOpenId())) put(unbound(user.appId(), user.openId()));
        }
        for (AtriAccount account : accounts) changed(null, account);
        started = true;
        worker.scheduleWithFixedDelay(this::tick, 0, 500, TimeUnit.MILLISECONDS);
    }

    /**
     * 只更新内存，不执行 HTTP 或文件操作，由统一账号提交后的缓存更新调用
     *
     * @param before 更新前的账号，首次加入缓存时为 null
     * @param after 更新后的账号，删除时为 null
     */
    public synchronized void changed(AtriAccount before, AtriAccount after) {
        if (closed || !storageReadable) return;
        String application = appId.get();
        if (application == null || application.isBlank()) return;
        if (!configuration.get().enabled()
                && (before == null || !snapshots.containsKey(application + "/" + before.qqConnectOpenId()))
                && (after == null || !snapshots.containsKey(application + "/" + after.qqConnectOpenId()))) return;
        if (before != null && before.qqConnectOpenId() != null
                && (after == null || !Objects.equals(before.qqConnectOpenId(), after.qqConnectOpenId()))) {
            if (after == null) put(deleted(account(application, before.qqConnectOpenId(), before)));
            else put(unbound(application, before.qqConnectOpenId()));
        }
        if (after != null && after.qqConnectOpenId() != null && !after.qqConnectOpenId().isBlank())
            put(account(application, after.qqConnectOpenId(), after));
    }

    /**
     * @param application QQ 互联应用 ID
     * @param openId 经 QQ 验证的 OpenID
     * @param account 当前统一账号，未绑定时为 null
     * @return 与变更广播共用版本号的完整账号状态
     */
    public synchronized Snapshot snapshot(String application, String openId, AtriAccount account) {
        requireAvailable();
        Snapshot current = snapshots.get(application + "/" + openId);
        if (current != null) return current;
        return put(account(application, openId, account));
    }

    synchronized void requireAvailable() {
        if (closed) throw new SiteAuthorizationService.Failure(ApiCode.AUTHORIZATION_DISABLED);
        if (!storageReadable) throw new SiteAuthorizationService.Failure(ApiCode.AUTHORIZATION_FAILED);
    }

    static Account account(String application, String openId, AtriAccount account) {
        return new Account(application, openId, account == null ? null : account.uuid().toString(),
                account == null ? null : account.username(), account == null ? null : account.qqUserOpenId(),
                account == null ? null : account.role().name(), account == null ? List.of() : account.permissions(),
                account == null ? null : account.status().name());
    }

    private static Account unbound(String application, String openId) {
        return account(application, openId, null);
    }

    private static Account deleted(Account user) {
        return new Account(user.appId(), user.openId(), user.accountId(), user.username(), user.userOpenId(),
                null, List.of(), AccountStatus.DELETED.name());
    }

    private Snapshot put(Account user) {
        String key = user.appId() + "/" + user.openId();
        Snapshot old = snapshots.get(key);
        if (old != null && old.user().equals(user)) return old;
        if (old == null && snapshots.size() >= MAX_SUBJECTS)
            throw new SiteAuthorizationService.Failure(ApiCode.AUTHORIZATION_BUSY);
        var settings = configuration.get();
        if (settings.enabled()) {
            long needed = settings.clients().entrySet().stream().filter(entry -> entry.getValue().patchUrl() != null
                    && !deliveries.containsKey(entry.getKey() + "/" + key)).count();
            if (deliveries.size() + needed > MAX_DELIVERIES)
                throw new SiteAuthorizationService.Failure(ApiCode.AUTHORIZATION_BUSY);
        }
        lastVersion = Math.max(clock.millis(), lastVersion + 1);
        Snapshot snapshot = new Snapshot(user, lastVersion, UUID.randomUUID().toString());
        snapshots.put(key, snapshot);
        generation++;
        enqueue(snapshot, settings);
        return snapshot;
    }

    private void enqueue(Snapshot snapshot, SiteAuthorizationSettings settings) {
        if (!settings.enabled()) return;
        for (var entry : settings.clients().entrySet()) {
            if (entry.getValue().patchUrl() == null) continue;
            String key = entry.getKey() + "/" + snapshot.user().appId() + "/" + snapshot.user().openId();
            if (!deliveries.containsKey(key) && deliveries.size() >= MAX_DELIVERIES)
                throw new SiteAuthorizationService.Failure(ApiCode.AUTHORIZATION_BUSY);
            deliveries.put(key, new Delivery(entry.getKey(), snapshot, 0, 0));
            generation++;
        }
    }

    private void tick() {
        try { runOnce(); }
        catch (Exception failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            LOG.error("站点账号变更投递失败，errorType={}", failure.getClass().getSimpleName());
        }
    }

    void runOnce() throws Exception {
        Delivery delivery = null;
        String key = null;
        SiteAuthorizationSettings.Client client = null;
        synchronized (this) {
            if (closed || !storageReadable) return;
            var settings = configuration.get();
            if (!settings.enabled()) return;
            if (!clients.equals(settings.clients())) {
                for (Snapshot snapshot : snapshots.values()) enqueue(snapshot, settings);
                clients = settings.clients();
            }
            var iterator = deliveries.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                client = settings.clients().get(entry.getValue().clientId());
                if (client == null || client.patchUrl() == null) {
                    iterator.remove();
                    generation++;
                } else if (entry.getValue().nextAttempt() <= clock.millis()) {
                    key = entry.getKey();
                    delivery = entry.getValue();
                    break;
                }
            }
        }
        persist();
        if (delivery == null) return;
        long now = clock.instant().getEpochSecond();
        var body = JSON.createObjectNode();
        body.put("client_id", delivery.clientId());
        body.put("event", "account.changed");
        body.put("event_id", delivery.snapshot().eventId());
        body.put("issued_at", now);
        body.put("expires_at", now + 300);
        body.set("user", userJson(delivery.snapshot()));
        String content = body.toString();
        String signature = SiteAuthorizationService.sign(client.secret(), "account_patch", delivery.clientId(),
                SiteAuthorizationService.encodePayload(content));
        boolean accepted = false;
        try {
            int status = transport.send(client.patchUrl(), content, signature);
            accepted = status >= 200 && status < 300;
            if (!accepted) LOG.warn("站点账号变更未被接收: clientId={}, eventId={}, httpStatus={}",
                    delivery.clientId(), delivery.snapshot().eventId(), status);
        } catch (InterruptedException interrupted) {
            throw interrupted;
        } catch (Exception failure) {
            LOG.warn("站点账号变更请求失败: clientId={}, eventId={}, errorType={}",
                    delivery.clientId(), delivery.snapshot().eventId(), failure.getClass().getSimpleName());
        }
        synchronized (this) {
            if (delivery.equals(deliveries.get(key))) {
                deliveries.remove(key);
                if (!accepted || !client.equals(configuration.get().clients().get(delivery.clientId()))) {
                    int attempts = Math.min(delivery.attempts() + 1, 10);
                    long delay = Math.min(300_000, 1000L << attempts);
                    deliveries.put(key, new Delivery(delivery.clientId(), delivery.snapshot(), attempts, clock.millis() + delay));
                }
                generation++;
            }
        }
        persist();
    }

    static com.fasterxml.jackson.databind.node.ObjectNode userJson(Snapshot snapshot) {
        Account user = snapshot.user();
        var node = JSON.createObjectNode();
        node.put("qq_connect_open_id", user.openId());
        node.put("account_id", user.accountId());
        node.put("username", user.username());
        node.put("user_open_id", user.userOpenId());
        node.put("role", user.role());
        node.set("permissions", JSON.valueToTree(user.permissions()));
        node.put("status", user.status());
        node.put("account_version", snapshot.version());
        return node;
    }

    private int send(String url, String body, String signature) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json; charset=utf-8").header("X-Auth-Signature", signature)
                .method("PATCH", HttpRequest.BodyPublishers.ofString(body)).build();
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private void persist() throws IOException {
        Stored stored;
        long version;
        synchronized (this) {
            if (!storageReadable || generation == persistedGeneration) return;
            version = generation;
            stored = new Stored(lastVersion, Map.copyOf(snapshots), Map.copyOf(deliveries));
        }
        AtomicFiles.write(file, JSON.writeValueAsBytes(stored));
        synchronized (this) { persistedGeneration = version; }
    }

    @Override public void close() {
        synchronized (this) { closed = true; }
        worker.shutdownNow();
        if (http != null) http.shutdownNow();
        try {
            if (worker.awaitTermination(6, TimeUnit.SECONDS)) persist();
            else LOG.error("站点变更工作线程未按时停止，未覆盖暂存数据");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (IOException failure) {
            LOG.error("保存站点变更暂存数据失败", failure);
        }
    }
}
