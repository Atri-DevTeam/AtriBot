package top.yzljc.atribot.function.utils.napcat.classtable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.service.request.HttpService;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author YZ_Ljc_
 * @ClassName TeacherClassTableService
 * @Created_at 2026/09/19
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.utils.napcat.classtable
 */
@Slf4j
public final class TeacherClassTableService {
    private static final String API_BASE = "https://ita.moentg.cn/api/class_table/";
    private static final Path CACHE_FILE = Path.of("data", "teacher-class-table-cache.json");
    private static final long FRESH_MILLIS = Duration.ofHours(6).toMillis();
    private static final long RETRY_NANOS = Duration.ofMinutes(5).toNanos();
    private static final int MAX_CLASSES = 2048;
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_CACHE_BYTES = 64 * 1024 * 1024;
    private static final int MAX_TABLE_BYTES = MAX_CACHE_BYTES - 1024 * 1024;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Object STATE_LOCK = new Object();

    private static Snapshot snapshot;
    private static boolean cacheLoaded;
    private static boolean closed;
    private static boolean failedRecently;
    private static long failedAtNanos;
    private static CompletableFuture<Snapshot> inFlight;
    private static Thread refreshThread;
    private static int completed;
    private static int total;

    private TeacherClassTableService() {
    }

    /** 返回已发布的只读快照；首次调用只读取本地缓存，不访问远端。 */
    public static Snapshot current() {
        synchronized (STATE_LOCK) {
            loadCacheIfNeeded();
            return snapshot;
        }
    }

    /** 所有调用共享一次后台汇总，调用方取消返回值不会取消其他用户的查询。 */
    public static CompletableFuture<Snapshot> refreshIfNeeded() {
        synchronized (STATE_LOCK) {
            loadCacheIfNeeded();
            if (closed) return CompletableFuture.failedFuture(new CancellationException("教师课表服务已关闭"));
            if (inFlight != null) return inFlight.copy();
            long now = System.currentTimeMillis();
            if (snapshot != null && snapshot.failedClasses() == 0
                    && now >= snapshot.updatedAtMillis() && now - snapshot.updatedAtMillis() < FRESH_MILLIS) {
                return CompletableFuture.completedFuture(snapshot);
            }
            if (failedRecently && System.nanoTime() - failedAtNanos < RETRY_NANOS) {
                return CompletableFuture.failedFuture(new IOException("课表刷新暂不可用，请在五分钟后重试"));
            }

            CompletableFuture<Snapshot> future = new CompletableFuture<>();
            inFlight = future;
            completed = 0;
            total = 0;
            Snapshot previous = snapshot;
            try {
                refreshThread = Thread.ofVirtual().name("teacher-class-table-refresh")
                        .unstarted(() -> refresh(future, previous));
                refreshThread.start();
            } catch (RuntimeException failure) {
                inFlight = null;
                refreshThread = null;
                failedRecently = true;
                failedAtNanos = System.nanoTime();
                return CompletableFuture.failedFuture(failure);
            }
            return future.copy();
        }
    }

    public static Progress progress() {
        synchronized (STATE_LOCK) {
            return new Progress(completed, total, !closed && inFlight != null);
        }
    }

    public static void shutdown() {
        Thread worker;
        CompletableFuture<Snapshot> future;
        synchronized (STATE_LOCK) {
            closed = true;
            worker = refreshThread;
            future = inFlight;
        }
        if (worker != null) worker.interrupt();
        if (future != null) future.completeExceptionally(new CancellationException("教师课表服务已关闭"));
    }

    private static void refresh(CompletableFuture<Snapshot> future, Snapshot previous) {
        Snapshot result = null;
        Throwable failure = null;
        try {
            result = download(previous);
            checkRunning();
            try {
                saveCache(result);
            } catch (IOException persistenceFailure) {
                log.warn("教师课表缓存保存失败，本轮结果继续保留在内存", persistenceFailure);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            failure = new CancellationException("教师课表汇总已中断");
        } catch (Throwable problem) {
            failure = problem;
            log.warn("教师课表汇总失败，保留现有缓存: {}", problem.getMessage());
        } finally {
            synchronized (STATE_LOCK) {
                if (closed) failure = new CancellationException("教师课表服务已关闭");
                if (failure == null) snapshot = result;
                failedRecently = failure != null || result == null || result.failedClasses() > 0;
                if (failedRecently) failedAtNanos = System.nanoTime();
                inFlight = null;
                refreshThread = null;
            }
            // 回调可能发送消息，不在状态锁内执行调用方回调。
            if (failure == null) future.complete(result);
            else future.completeExceptionally(failure);
        }
    }

    private static Snapshot download(Snapshot previous) throws IOException, InterruptedException {
        checkRunning();
        List<String> majors = readMajors(requestJson(URI.create(API_BASE + "get_global_filter"), false));
        synchronized (STATE_LOCK) {
            total = majors.size();
        }

        ObjectNode tables = JSON.createObjectNode();
        long tableBytes = 0;
        for (String major : majors) {
            String key = tableKey(major);
            JsonNode old = previous == null ? null : previous.tables().get(key);
            if (old != null) {
                tables.set(key, old);
                tableBytes += JSON.writeValueAsBytes(old).length;
            }
        }
        int successCount = 0;
        int consecutiveFailures = 0;
        for (String major : majors) {
            checkRunning();
            // 只有一个汇总线程，所有远端请求之间至少间隔半秒。
            Thread.sleep(500);
            String key = tableKey(major);
            try {
                URI uri = URI.create(API_BASE + "get_raw_class_table?semester=" + encode(ProcessClassTable.SEMESTER)
                        + "&major=" + encode(major));
                JsonNode table = readTable(requestJson(uri, true), key);
                long oldBytes = tables.has(key) ? JSON.writeValueAsBytes(tables.get(key)).length : 0;
                long nextBytes = tableBytes - oldBytes + JSON.writeValueAsBytes(table).length;
                if (nextBytes > MAX_TABLE_BYTES) {
                    log.warn("教师课表汇总达到缓存大小上限，本轮暂停");
                    break;
                }
                tables.set(key, table);
                tableBytes = nextBytes;
                successCount++;
                consecutiveFailures = 0;
            } catch (RemoteStatusException status) {
                consecutiveFailures++;
                log.warn("教师课表班级请求失败: major={}, status={}", major, status.status);
                if (status.status == 429 || status.status == 503) break;
            } catch (IOException failure) {
                consecutiveFailures++;
                log.warn("教师课表班级请求失败: major={}, error={}", major, failure.getMessage());
            } finally {
                synchronized (STATE_LOCK) {
                    completed++;
                }
            }
            if (consecutiveFailures >= 5) {
                log.warn("教师课表连续五次请求失败，本轮暂停");
                break;
            }
        }
        if (successCount == 0) throw new IOException("本轮未成功刷新任何班级课表，保留现有缓存和更新时间");
        int failed = majors.size() - successCount;
        log.info("教师课表汇总结束: total={}, refreshed={}, oldOrMissing={}", majors.size(), successCount, failed);
        return new Snapshot(ProcessClassTable.SEMESTER, System.currentTimeMillis(), tables, majors.size(), failed);
    }

    private static List<String> readMajors(JsonNode root) throws IOException {
        JsonNode majors = root.path(ProcessClassTable.SEMESTER);
        if (!root.isObject() || !majors.isArray() || majors.isEmpty() || majors.size() > MAX_CLASSES) {
            throw new IOException("课表班级目录格式或数量无效");
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (JsonNode value : majors) {
            if (!value.isTextual() || value.textValue().isBlank() || value.textValue().length() > 128) {
                throw new IOException("课表班级名称无效");
            }
            unique.add(value.textValue().trim());
        }
        return new ArrayList<>(unique);
    }

    private static JsonNode readTable(JsonNode root, String expectedKey) throws IOException {
        if (!root.isObject()) throw new IOException("班级课表根节点无效");
        JsonNode table = root.get(expectedKey);
        if (table == null) {
            var fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                try {
                    if (expectedKey.equals(URLDecoder.decode(field.getKey(), StandardCharsets.UTF_8))) {
                        table = field.getValue();
                        break;
                    }
                } catch (IllegalArgumentException ignored) {
                    // 无效编码不能匹配当前班级。
                }
            }
        }
        if (!validTable(table)) throw new IOException("班级课表内容格式无效或缺少目标班级");
        return table;
    }

    /** 空班级对象是成功响应；缺少班级键、错误页和不完整结构属于请求失败。 */
    private static boolean validTable(JsonNode table) {
        if (table == null || !table.isObject() || table.size() > 7) return false;
        var days = table.fields();
        int courses = 0;
        while (days.hasNext()) {
            var day = days.next();
            if (!numberInRange(day.getKey(), 1, 7) || !day.getValue().isObject() || day.getValue().size() > 24) return false;
            var slots = day.getValue().fields();
            while (slots.hasNext()) {
                var slot = slots.next();
                if (!numberInRange(slot.getKey(), 1, 24) || !slot.getValue().isArray()) return false;
                for (JsonNode course : slot.getValue()) {
                    if (++courses > 2048 || !course.isObject()) return false;
                    JsonNode data = course.path("class_data");
                    JsonNode weeks = course.path("with_in_week");
                    if (!data.isObject() || !weeks.isArray() || weeks.size() > 60) return false;
                    int start = data.path("class_start_time").asInt(0);
                    int end = data.path("class_end_time").asInt(0);
                    if (start < 1 || end < start || end > 24) return false;
                    if (data.hasNonNull("semester") && !ProcessClassTable.SEMESTER.equals(data.path("semester").asText())) return false;
                    if (!data.path("class_name_show").isTextual()) return false;
                    if (data.hasNonNull("teacher") && !data.path("teacher").isTextual()) return false;
                    for (JsonNode week : weeks) {
                        if (!week.isIntegralNumber() || week.intValue() < 1 || week.intValue() > 60) return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean numberInRange(String value, int min, int max) {
        try {
            int number = Integer.parseInt(value);
            return number >= min && number <= max;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static JsonNode requestJson(URI uri, boolean post) throws IOException, InterruptedException {
        checkRunning();
        var builder = HttpService.newRequestBuilder().uri(uri).timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json");
        HttpRequest request = post
                ? builder.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build()
                : builder.GET().build();
        AtomicInteger status = new AtomicInteger();
        CompletableFuture<HttpResponse<byte[]>> requestFuture = HttpService.redirectHttpClient.sendAsync(request, info -> {
            status.set(info.statusCode());
            return new LimitedBodySubscriber();
        });
        try {
            HttpResponse<byte[]> response = requestFuture.get(10, TimeUnit.SECONDS);
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw new RemoteStatusException(response.statusCode());
            JsonNode root = JSON.readTree(response.body());
            if (root == null) throw new IOException("课表接口返回空响应");
            return root;
        } catch (TimeoutException | ExecutionException failure) {
            requestFuture.cancel(true);
            if (status.get() == 429 || status.get() == 503) throw new RemoteStatusException(status.get());
            throw new IOException(failure instanceof TimeoutException ? "课表请求超过十秒" : "课表请求失败", failure);
        } catch (InterruptedException interrupted) {
            requestFuture.cancel(true);
            throw interrupted;
        }
    }

    private static void loadCacheIfNeeded() {
        if (cacheLoaded) return;
        cacheLoaded = true;
        if (!Files.isRegularFile(CACHE_FILE)) return;
        try {
            if (Files.size(CACHE_FILE) > MAX_CACHE_BYTES) throw new IOException("缓存文件超过大小上限");
            byte[] bytes;
            try (var input = Files.newInputStream(CACHE_FILE)) {
                bytes = input.readNBytes(MAX_CACHE_BYTES + 1);
            }
            if (bytes.length > MAX_CACHE_BYTES) throw new IOException("缓存文件超过大小上限");
            JsonNode root = JSON.readTree(bytes);
            if (root == null || !root.isObject() || !ProcessClassTable.SEMESTER.equals(root.path("semester").asText())) {
                throw new IOException("缓存学期不匹配或格式无效");
            }
            long updated = root.path("updatedAtMillis").asLong(0);
            int expected = root.path("expectedClasses").asInt(-1);
            int failed = root.path("failedClasses").asInt(-1);
            JsonNode tables = root.path("tables");
            if (updated <= 0 || updated > System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5)
                    || expected < 1 || expected > MAX_CLASSES || failed < 0 || failed > expected
                    || !tables.isObject() || tables.isEmpty() || tables.size() > expected || tables.size() < expected - failed) {
                throw new IOException("缓存时间、班级数量或缺失数量无效");
            }
            var entries = tables.fields();
            while (entries.hasNext()) {
                var entry = entries.next();
                String key = entry.getKey();
                if (!key.startsWith(ProcessClassTable.SEMESTER + "_")
                        || key.length() <= ProcessClassTable.SEMESTER.length() + 1
                        || key.length() > ProcessClassTable.SEMESTER.length() + 129 || !validTable(entry.getValue())) {
                    throw new IOException("缓存班级键或课程结构无效");
                }
            }
            snapshot = new Snapshot(ProcessClassTable.SEMESTER, updated, tables, expected, failed);
            log.info("已载入教师课表缓存: total={}, oldOrMissing={}", expected, failed);
        } catch (IOException | RuntimeException invalid) {
            log.warn("教师课表缓存不可用，将在首次查询时重新汇总: {}", invalid.getMessage());
        }
    }

    private static void saveCache(Snapshot value) throws IOException {
        byte[] bytes = JSON.writeValueAsBytes(value);
        if (bytes.length > MAX_CACHE_BYTES) throw new IOException("教师课表缓存超过大小上限");
        Path target = CACHE_FILE.toAbsolutePath();
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), "teacher-class-table-cache-", ".tmp");
        try {
            Files.write(temporary, bytes);
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void checkRunning() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        synchronized (STATE_LOCK) {
            if (closed) throw new InterruptedException();
        }
    }

    private static String tableKey(String major) {
        return ProcessClassTable.SEMESTER + "_" + major;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static final class RemoteStatusException extends IOException {
        private final int status;

        private RemoteStatusException(int status) {
            super("课表接口返回 HTTP " + status);
            this.status = status;
        }
    }

    /** 在接收过程中限制响应体，避免服务器未声明长度时无限缓冲。 */
    private static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private Flow.Subscription subscription;
        private long received;
        private boolean terminated;

        @Override
        public CompletionStage<byte[]> getBody() {
            return delegate.getBody();
        }

        @Override
        public void onSubscribe(Flow.Subscription value) {
            subscription = value;
            delegate.onSubscribe(value);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            if (terminated) return;
            for (ByteBuffer buffer : buffers) received += buffer.remaining();
            if (received > MAX_RESPONSE_BYTES) {
                terminated = true;
                subscription.cancel();
                delegate.onError(new IOException("课表接口响应超过大小上限"));
                return;
            }
            delegate.onNext(buffers);
        }

        @Override
        public void onError(Throwable failure) {
            if (terminated) return;
            terminated = true;
            delegate.onError(failure);
        }

        @Override
        public void onComplete() {
            if (terminated) return;
            terminated = true;
            delegate.onComplete();
        }
    }

    public record Progress(int completed, int total, boolean refreshing) {}

    /** tables 在发布后只读；failedClasses 包括本轮沿用旧数据和未获取到数据的班级。 */
    public record Snapshot(String semester, long updatedAtMillis, JsonNode tables, int expectedClasses, int failedClasses) {}
}
