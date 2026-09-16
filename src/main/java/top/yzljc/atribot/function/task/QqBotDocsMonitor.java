package top.yzljc.atribot.function.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.function.tasks.pushtask.PushTask;
import top.yzljc.atribot.service.taskscheduler.ScheduleMode;
import top.yzljc.atribot.service.taskscheduler.ScheduledTask;
import top.yzljc.atribot.service.taskscheduler.TaskPlan;
import top.yzljc.atribot.service.taskscheduler.TaskSchedule;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * @Author YZ_Ljc_
 * @ClassName QqBotDocsMonitor
 * @Created_at 2026/09/04
 * @Project AtriBot
 * @Package top.yzljc.atribot.function.task
 */
@Slf4j
public final class QqBotDocsMonitor implements ScheduledTask {
    public static final QqBotDocsMonitor INSTANCE = new QqBotDocsMonitor();
    private static final TaskSchedule SCHEDULE = new TaskPlan(ScheduleMode.a_quarter);
    private static final URI INDEX_URI = URI.create("https://bot.q.qq.com/wiki/develop/api-v2/autogen/");
    private static final URI CHANGELOG_URI = URI.create("https://bot.q.qq.com/wiki/develop/api-v2/changelog.html");
    private static final URI ROOT_URI = URI.create("https://bot.q.qq.com/wiki/");
    private static final URI SITEMAP_URI = ROOT_URI.resolve("sitemap.xml");
    private static final String DOC_PREFIX = "/wiki/";
    private static final Pattern SDK_PATH = Pattern.compile("(?i)^/wiki/develop/[^/]*sdk(?:/|$)");
    private static final Path DATA_DIR = Path.of("data", "qqbot-docs-monitor");
    private static final Path SNAPSHOT_FILE = DATA_DIR.resolve("snapshot.json");
    private static final Path REPORT_FILE = DATA_DIR.resolve("latest-report.txt");
    private static final Pattern UPDATED_AT = Pattern.compile("^上次更新[:：].*$");
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public TaskSchedule schedule() {
        return SCHEDULE;
    }

    @Override
    public void run() {
        checkForUpdates();
    }

    public static void checkForUpdates() {
        if (!RUNNING.compareAndSet(false, true)) {
            log.info("QQ 开放平台文档检查仍在运行，跳过本轮");
            return;
        }
        try {
            checkNow();
        } catch (Exception e) {
            // 抓取不完整时绝不覆盖快照，否则容易把网络故障误报为页面删除。
            log.warn("QQ 开放平台文档检查失败，本轮快照未更新: {}", e.getMessage(), e);
        } finally {
            RUNNING.set(false);
        }
    }

    public static CheckResult checkNow() throws Exception {
        Map<String, PageSnapshot> current = crawlAllPages();
        Map<String, PageSnapshot> previous = loadSnapshot();

        if (previous.isEmpty()) {
            saveSnapshot(current);
            log.info("QQ 开放平台文档监听基线已建立，共 {} 页", current.size());
            return new CheckResult(true, 0, 0, 0, "");
        }

        ChangeSet changes = compare(previous, current);
        String report = changes.isEmpty() ? "" : buildReport(changes, previous, current);
        saveSnapshot(current);

        if (changes.isEmpty()) {
            log.info("QQ 开放平台文档无变化，共检查 {} 页", current.size());
        } else {
            Files.createDirectories(DATA_DIR);
            Files.writeString(REPORT_FILE, report, StandardCharsets.UTF_8);

            PushTask.push("open_platform_doc_check", TC.md(report));
//            notifyOfficialDebugGroup(report);
            log.info("检测到 QQ 开放平台文档变化：新增 {}，修改 {}，删除 {}；完整报告：{}",
                    changes.added.size(), changes.modified.size(), changes.removed.size(), REPORT_FILE);
        }
        return new CheckResult(false, changes.added.size(), changes.modified.size(), changes.removed.size(), report);
    }

    private static Map<String, PageSnapshot> crawlAllPages() throws Exception {
        return crawlAllPages(QqBotDocsMonitor::fetch);
    }

    @FunctionalInterface
    interface PageFetcher {
        FetchedPage fetch(URI uri) throws Exception;
    }

    record FetchedPage(URI uri, String html) {
    }

    static Map<String, PageSnapshot> crawlAllPages(PageFetcher fetcher) throws Exception {
        // 站点地图覆盖没有导航入口的文档；每页的链接继续补充尚未进入站点地图的页面。
        // 地图或任意正文抓取失败都会中止本轮，避免把不完整结果写成新的基线。
        Set<URI> urls = readSitemap(fetcher);
        urls.add(ROOT_URI);
        urls.add(INDEX_URI);
        urls.add(CHANGELOG_URI);
        var pending = new ArrayDeque<>(urls);
        Map<String, PageSnapshot> pages = new LinkedHashMap<>();
        while (!pending.isEmpty()) {
            URI uri = pending.removeFirst();
            if (pages.containsKey(uri.toString())) continue;
            FetchedPage response = fetcher.fetch(uri);
            URI resolved = normalizePageUri(uri, response.uri().toString());
            if (resolved == null) throw new IOException("文档重定向到监控范围之外: " + uri + " -> " + response.uri());
            // 旧版链接常重定向到文档首页，必须使用最终地址，不能把首页当作多篇文档。
            urls.add(resolved);
            if (pages.containsKey(resolved.toString())) continue;
            pages.put(resolved.toString(), parsePage(resolved, response.html()));
            for (URI linked : discoverPageLinks(resolved, response.html())) {
                if (urls.add(linked)) pending.addLast(linked);
            }
        }
//        log.info("QQ 官方文档抓取完成，共 {} 页", pages.size());
        return pages;
    }

    private static Set<URI> readSitemap(PageFetcher fetcher) throws Exception {
        Set<URI> pages = new LinkedHashSet<>();
        Set<URI> visited = new LinkedHashSet<>();
        var pending = new ArrayDeque<URI>();
        pending.add(SITEMAP_URI);
        while (!pending.isEmpty()) {
            URI uri = pending.removeFirst();
            if (!visited.add(uri)) continue;
            FetchedPage response = fetcher.fetch(uri);
            if (!isOfficialWikiUri(response.uri())) throw new IOException("站点地图重定向到站外: " + uri);
            Document xml = Jsoup.parse(response.html(), response.uri().toString(), Parser.xmlParser());
            if (xml.selectFirst("urlset") != null) {
                for (Element location : xml.select("urlset > url > loc")) {
                    URI page = normalizePageUri(uri, location.text());
                    if (page != null) pages.add(page);
                }
            } else if (xml.selectFirst("sitemapindex") != null) {
                for (Element location : xml.select("sitemapindex > sitemap > loc")) {
                    URI child = uri.resolve(location.text().strip()).normalize();
                    if (!isOfficialWikiUri(child) || !child.getPath().endsWith(".xml")) {
                        throw new IOException("站点地图地址不受支持: " + child);
                    }
                    pending.addLast(child);
                }
            } else {
                throw new IOException("站点地图格式无效: " + uri);
            }
        }
        if (pages.isEmpty()) throw new IOException("站点地图未解析到文档，拒绝更新快照");
        return pages;
    }

    static Set<URI> discoverPageLinks(URI base, String html) {
        Set<URI> urls = new LinkedHashSet<>();
        for (Element link : Jsoup.parse(html, base.toString()).select("a[href]")) {
            URI uri = normalizePageUri(base, link.attr("href"));
            if (uri != null) urls.add(uri);
        }
        return urls;
    }

    static URI normalizePageUri(URI base, String href) {
        try {
            if (href == null || href.isBlank()) return null;
            URI uri = base.resolve(href.strip()).normalize();
            if (!isOfficialWikiUri(uri)) return null;
            String path = uri.getPath();
            if (SDK_PATH.matcher(path).find() || path.equals("/wiki/404.html")) return null;
            if (!(path.endsWith("/") || path.endsWith(".html"))) return null;
            String rawPath = uri.getRawPath();
            if (rawPath.endsWith("/index.html")) rawPath = rawPath.substring(0, rawPath.length() - 10);
            return URI.create("https://" + ROOT_URI.getHost() + rawPath);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static boolean isOfficialWikiUri(URI uri) {
        return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                && ROOT_URI.getHost().equalsIgnoreCase(uri.getHost())
                && uri.getUserInfo() == null
                && (uri.getPort() == -1 || ("https".equalsIgnoreCase(uri.getScheme()) ? uri.getPort() == 443 : uri.getPort() == 80))
                && uri.getPath() != null && uri.getPath().startsWith(DOC_PREFIX);
    }

    /** 手动导出全站正文，便于核对覆盖范围；不修改运行中的基线，也不触发订阅推送。 */
    public static void main(String[] args) throws Exception {
        if (args.length != 2 || !"--export".equals(args[0])) {
            throw new IllegalArgumentException("用法: QqBotDocsMonitor --export <输出目录>");
        }
        Path directory = Path.of(args[1]);
        Map<String, PageSnapshot> pages = crawlAllPages();
        Files.createDirectories(directory);
        JSON.writeValue(directory.resolve("snapshot.json").toFile(),
                new SnapshotFile(Instant.now().toString(), pages));
        Files.write(directory.resolve("pages.txt"), pages.keySet().stream().sorted().toList(), StandardCharsets.UTF_8);
        log.info("已导出 {} 页官方文档至 {}", pages.size(), directory.toAbsolutePath());
    }

    private static FetchedPage fetch(URI uri) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "AtriMeow-QqBotDocsMonitor/1.0")
                .header("Accept", "text/html,application/xhtml+xml")
                .GET()
                .build();
        IOException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                HttpResponse<String> response = HTTP.send(request,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() >= 200 && response.statusCode() < 300 && !response.body().isBlank()) {
                    return new FetchedPage(response.uri(), response.body());
                }
                last = new IOException("HTTP " + response.statusCode() + " from " + uri);
            } catch (IOException e) {
                last = e;
            }
            if (attempt < 3) Thread.sleep(500L * attempt);
        }
        throw last == null ? new IOException("无法抓取 " + uri) : last;
    }

    static PageSnapshot parsePage(URI uri, String html) throws Exception {
        Document document = Jsoup.parse(html, uri.toString());
        String title = document.selectFirst("h1") != null
                ? document.selectFirst("h1").text().trim()
                : document.title().replace(" | QQ 机器人官方文档", "").trim();
        Element content = document.selectFirst("main .theme-default-content");
        if (content == null) content = document.selectFirst(".theme-default-content");
        if (content == null) content = document.selectFirst("main");
        if (content == null) throw new IOException("页面正文不存在: " + uri);

        Element clean = content.clone();
        clean.select("script,style,nav,.page-nav,.last-updated,.line-number").remove();
        clean.select("br").append("\n");
        clean.select("p,h1,h2,h3,h4,h5,h6,li,tr,pre,blockquote").before("\n").after("\n");
        String normalized = normalize(clean.wholeText());
        if (normalized.isBlank()) throw new IOException("页面正文为空: " + uri);
        return new PageSnapshot(title, normalized, sha256(normalized), Instant.now().toString());
    }

    private static String normalize(String raw) {
        List<String> lines = new ArrayList<>();
        boolean previousBlank = true;
        for (String source : raw.replace('\u00a0', ' ').replace("\r", "").split("\n")) {
            String line = source.strip().replaceAll("[\\t ]+", " ");
            if (UPDATED_AT.matcher(line).matches()) continue;
            boolean blank = line.isEmpty();
            if (!blank || !previousBlank) lines.add(line);
            previousBlank = blank;
        }
        while (!lines.isEmpty() && lines.getLast().isEmpty()) lines.removeLast();
        return String.join("\n", lines);
    }

    static ChangeSet compare(Map<String, PageSnapshot> oldPages, Map<String, PageSnapshot> newPages) {
        List<String> added = newPages.keySet().stream().filter(url -> !oldPages.containsKey(url)).sorted().toList();
        List<String> removed = oldPages.keySet().stream().filter(url -> !newPages.containsKey(url)).sorted().toList();
        List<String> modified = newPages.keySet().stream()
                .filter(oldPages::containsKey)
                .filter(url -> !newPages.get(url).hash.equals(oldPages.get(url).hash))
                .sorted().toList();
        return new ChangeSet(added, modified, removed);
    }

    static String buildReport(ChangeSet changes, Map<String, PageSnapshot> oldPages,
                                      Map<String, PageSnapshot> newPages) {
        StringBuilder out = new StringBuilder("QQ 开放平台官方文档检测到更新\n")
                .append("新增 ").append(changes.added.size()).append(" 页，修改 ")
                .append(changes.modified.size()).append(" 页，删除 ")
                .append(changes.removed.size()).append(" 页\n");
        int number = 1;
        number = appendChanges(out, number, "新增", changes.added, newPages);
        number = appendChanges(out, number, "修改", changes.modified, newPages);
        appendChanges(out, number, "删除", changes.removed, oldPages);

        for (String url : changes.modified) {
            PageSnapshot before = oldPages.get(url);
            PageSnapshot after = newPages.get(url);
            out.append("\n页面内容对比：").append(after.title).append('\n').append(url).append('\n')
                    .append(UnifiedDiff.create(before.content, after.content));
        }
        return out.toString().stripTrailing() + "\n";
    }

    private static int appendChanges(StringBuilder out, int number, String type, List<String> urls,
                                     Map<String, PageSnapshot> pages) {
        for (String url : urls) {
            PageSnapshot page = pages.get(url);
            out.append(number++).append(". [").append(type).append("] ")
                    .append(page == null ? url : page.title).append('\n').append(url).append('\n');
        }
        return number;
    }

//    private static void notifyOfficialDebugGroup(String report) {
//        Config config = Config.getInstance();
//        String groupOpenId = config.getDebugGroupOpenId();
//        String superAdminId = config.getSuperAdminId();
//        if (isMissingConfig(groupOpenId) || isMissingConfig(superAdminId)) {
//            log.warn("QQ 开放平台文档已有变化，但 qq.debug-group-openId 或 qq.super_admin_id 未配置；报告保存在 {}",
//                    REPORT_FILE);
//            return;
//        }
//
//        String markdown = Markdown.at(superAdminId) + "\n\n" + report;
//        String messageId = GroupChat.sendMessage(groupOpenId, TC.md(markdown));
//        if (messageId == null || messageId.isBlank()) {
//            log.warn("QQ 开放平台文档更新通知发送失败；完整报告保存在 {}", REPORT_FILE);
//        }
//    }

    private static Map<String, PageSnapshot> loadSnapshot() throws IOException {
        if (!Files.isRegularFile(SNAPSHOT_FILE)) return Map.of();
        SnapshotFile file = JSON.readValue(SNAPSHOT_FILE.toFile(), SnapshotFile.class);
        return file.pages == null ? Map.of() : file.pages;
    }

    private static void saveSnapshot(Map<String, PageSnapshot> pages) throws IOException {
        Files.createDirectories(DATA_DIR);
        Path temporary = Files.createTempFile(DATA_DIR, "snapshot-", ".tmp");
        try {
            JSON.writeValue(temporary.toFile(), new SnapshotFile(Instant.now().toString(), pages));
            try {
                Files.move(temporary, SNAPSHOT_FILE, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException unsupportedAtomicMove) {
                Files.move(temporary, SNAPSHOT_FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String sha256(String content) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
        return java.util.HexFormat.of().formatHex(digest);
    }

    public record CheckResult(boolean baselineCreated, int added, int modified, int removed, String report) {
    }

    record ChangeSet(List<String> added, List<String> modified, List<String> removed) {
        private boolean isEmpty() {
            return added.isEmpty() && modified.isEmpty() && removed.isEmpty();
        }
    }

    public static final class PageSnapshot {
        public String title;
        public String content;
        public String hash;
        public String fetchedAt;

        public PageSnapshot() {
        }

        private PageSnapshot(String title, String content, String hash, String fetchedAt) {
            this.title = title;
            this.content = content;
            this.hash = hash;
            this.fetchedAt = fetchedAt;
        }
    }

    public static final class SnapshotFile {
        public String fetchedAt;
        public Map<String, PageSnapshot> pages = new LinkedHashMap<>();

        public SnapshotFile() {}

        private SnapshotFile(String fetchedAt, Map<String, PageSnapshot> pages) {
            this.fetchedAt = fetchedAt;
            this.pages = pages;
        }
    }

    /** 简单的逐行 LCS diff；文档页规模较小，无需额外引入 diff 依赖。 */
    private static final class UnifiedDiff {
        private static String create(String before, String after) {
            String[] a = before.split("\n", -1);
            String[] b = after.split("\n", -1);
            int[][] lcs = new int[a.length + 1][b.length + 1];
            for (int i = a.length - 1; i >= 0; i--) {
                for (int j = b.length - 1; j >= 0; j--) {
                    lcs[i][j] = a[i].equals(b[j]) ? lcs[i + 1][j + 1]
                            : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
                }
            }
            StringBuilder diff = new StringBuilder("--- 更新前\n+++ 更新后\n");
            int i = 0, j = 0;
            while (i < a.length || j < b.length) {
                if (i < a.length && j < b.length && a[i].equals(b[j])) {
                    diff.append(' ').append(a[i]).append('\n');
                    i++;
                    j++;
                } else if (j < b.length && (i == a.length || lcs[i][j + 1] >= lcs[i + 1][j])) {
                    diff.append('+').append(b[j++]).append('\n');
                } else {
                    diff.append('-').append(a[i++]).append('\n');
                }
            }
            return compact(diff.toString(), 3);
        }

        private static String compact(String raw, int context) {
            String[] lines = raw.split("\n", -1);
            StringBuilder out = new StringBuilder(lines[0]).append('\n').append(lines[1]).append('\n');
            boolean[] keep = new boolean[lines.length];
            keep[0] = keep[1] = true;
            for (int i = 2; i < lines.length; i++) {
                if (lines[i].startsWith("+") || lines[i].startsWith("-")) {
                    for (int j = Math.max(2, i - context); j <= Math.min(lines.length - 1, i + context); j++) keep[j] = true;
                }
            }
            boolean gap = false;
            for (int i = 2; i < lines.length; i++) {
                if (keep[i]) {
                    if (gap) out.append("@@ ... @@\n");
                    out.append(lines[i]).append('\n');
                    gap = false;
                } else if (!lines[i].isEmpty()) {
                    gap = true;
                }
            }
            return out.toString();
        }
    }
}
