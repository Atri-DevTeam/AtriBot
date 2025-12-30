package top.yzljc.qqbot.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import top.yzljc.qqbot.command.AnnounceGroup;
import top.yzljc.qqbot.messages.MessageSender;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Hypixel官网新闻自动推送
 */
public class HypixelNews {

    private static final String NEWS_URL = "https://hypixel.net/forums/news-and-announcements.4/";
    private static final String ARTICLE_BASE = "https://hypixel.net";
    private static final String HISTORY_FILE = "hypixel_news_history.json";

    public static final List<Long> TARGET_GROUPS = AnnounceGroup.TARGET_GROUPS_HYP;

    private static boolean isInitialized = false;
    private static final Set<String> pushedArticleIds = new HashSet<>();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void startScheduler() {
        if (isInitialized) return;
        loadHistory();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> checkNews(false), 10, 3600, TimeUnit.SECONDS);
        System.out.println("[INFO] Hypixel新闻监控任务已启动");
        isInitialized = true;
    }

    public static void checkNews(boolean isManualTrigger) {
        try {
            if (isManualTrigger) {
                System.out.println("[INFO] 正在执行 Hypixel 手动检查...");
            } else {
                System.out.println("[INFO] Hypixel 自动新闻检查中...");
            }
            // 1. 拉取和解析官网新闻首页
            List<UnifiedArticle> candidateArticles = fetchAndParse(NEWS_URL, 5);

            // 2. 按发布时间新到旧排好
            candidateArticles.sort(Comparator.comparingLong(a -> -a.timestamp));
            List<UnifiedArticle> newArticlesFound = new ArrayList<>();

            // 3. 检查未推送过的
            for (UnifiedArticle article : candidateArticles) {
                if (article.id == null || article.id.isEmpty()) continue;
                if (!pushedArticleIds.contains(article.id)) {
                    newArticlesFound.add(article);
                }
            }

            // 4. 反转为旧到新推送
            Collections.reverse(newArticlesFound);

            int newCount = 0;
            for (UnifiedArticle article : newArticlesFound) {
                System.out.println("[INFO] 发现新Hypixel文章: " + article.title);
                System.out.println("[INFO] 当前Hypixel新闻推广群: " + AnnounceGroup.TARGET_GROUPS_HYP);
                pushedArticleIds.add(article.id);
                pushToAllGroups(article);
                newCount++;
            }
            if (newCount > 0) saveHistory();
            if (isManualTrigger && newCount == 0) {
                System.out.println("[INFO] Hypixel手动检查结束，无新文章。");
            }
        } catch (Exception e) {
            System.err.println("[INFO] Hypixel新闻检查失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static List<UnifiedArticle> fetchAndParse(String newsUrl, int limit) {
        List<UnifiedArticle> list = new ArrayList<>();
        try {
            Document doc = Jsoup.connect(newsUrl).userAgent("Mozilla/5.0").get();
            // Hypixel新闻列表所有帖子都在 .structItem--thread 结构下
            Elements posts = doc.select("div.structItem--thread");
            int count = 0;
            for (Element post : posts) {
                if (count >= limit) break;

                Element linkElem = post.selectFirst(".structItem-title a");
                if (linkElem == null) continue;
                String url = ARTICLE_BASE + linkElem.attr("href");
                String id = url; //官网无ID字段，用完整链接唯一标识

                String title = linkElem.text();

                Element metaElem = post.selectFirst(".structItem-parts time");
                String dateStr = metaElem != null ? metaElem.attr("datetime") : "";
                long timestamp = parseDateToTimestamp(dateStr);
                String dateDisplay = formatDisplayDate(dateStr);

                String descPreview = "";
                Element excerptElem = post.selectFirst(".structItem-snippet");
                if (excerptElem != null) {
                    descPreview = excerptElem.text();
                }

                String imageUrl = null;
                // 进入帖子详情页找置顶图片
                try {
                    Document artDoc = Jsoup.connect(url).userAgent("Mozilla/5.0").get();
                    Element imgElem = artDoc.selectFirst(".bbImage");
                    if (imgElem != null) {
                        imageUrl = imgElem.hasAttr("data-url") ? imgElem.attr("data-url") : imgElem.attr("src");
                    }
                } catch (Exception e) {
                    // 无图片正文也没关系
                }

                UnifiedArticle article = new UnifiedArticle();
                article.id = id;
                article.title = title;
                article.url = url;
                article.timestamp = timestamp;
                article.dateDisplay = dateDisplay;
                article.description = descPreview;
                article.tag = "Hypixel";
                article.imageUrl = imageUrl;

                list.add(article);
                count++;
            }
        } catch (Exception e) {
            System.err.println("[INFO] Hypixel解析失败: " + e.getMessage());
            e.printStackTrace();
        }
        return list;
    }

    private static void pushToAllGroups(UnifiedArticle article) {
        StringBuilder sb = new StringBuilder();
        sb.append("【Hypixel 官网资讯】\n");
        sb.append(article.title).append("\n");
        sb.append("发布时间: ").append(article.dateDisplay).append("\n\n");
        if (article.description != null && !article.description.isEmpty()) {
            sb.append(article.description).append("\n\n");
        }
        sb.append("链接: ").append(article.url);
        String textContent = sb.toString();

        String base64Img = null;
        if (article.imageUrl != null && !article.imageUrl.isEmpty()) {
            try {
                base64Img = downloadImageAsBase64(article.imageUrl);
            } catch (Exception e) {
                System.err.println("[INFO] Hypixel图片下载失败: " + e.getMessage());
            }
        }

        for (Long groupId : TARGET_GROUPS) {
            MessageSender.sendGroupMessage(groupId, textContent, base64Img);
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
        }
    }

    private static String downloadImageAsBase64(String imageUrl) throws IOException {
        URL url = new URL(imageUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);
        conn.setRequestMethod("GET");
        try (InputStream in = conn.getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
            byte[] imgBytes = out.toByteArray();
            return Base64.getEncoder().encodeToString(imgBytes);
        } finally {
            conn.disconnect();
        }
    }

    private static void loadHistory() {
        File file = new File(HISTORY_FILE);
        if (!file.exists()) return;
        try {
            JsonNode root = objectMapper.readTree(file);
            if (root.isArray()) {
                for (JsonNode idNode : root) {
                    pushedArticleIds.add(idNode.asText());
                }
            }
        } catch (IOException e) {
            System.err.println("[INFO] Hypixel历史记录读取失败，将重新创建。");
        }
    }

    private static void saveHistory() {
        try {
            ArrayNode arrayNode = objectMapper.createArrayNode();
            for (String id : pushedArticleIds) {
                arrayNode.add(id);
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(HISTORY_FILE), arrayNode);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String formatDisplayDate(String rawDate) {
        if (rawDate == null || rawDate.isEmpty()) return "未知时间";
        try {
            if (rawDate.length() >= 19) { // e.g. 2024-02-15T04:33:14+00:00
                LocalDateTime ldt = LocalDateTime.parse(rawDate.substring(0, 19));
                return ldt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            } else {
                return rawDate;
            }
        } catch (Exception e) {
            return rawDate;
        }
    }

    private static long parseDateToTimestamp(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return 0;
        try {
            if (dateStr.length() >= 19) {
                return LocalDateTime.parse(dateStr.substring(0, 19))
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli();
            } else {
                return 0;
            }
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 指令触发入口，只允许 user=3199590352 使用 testforhyp 触发
     * 保持方法签名不变，兼容 MessageProcessor
     */
    public static void processTestForHyp(JsonNode json) {
        String postType = json.path("post_type").asText("");
        if (!"message".equals(postType)) return;
        String messageType = json.path("message_type").asText("");
        if (!"group".equals(messageType)) return;

        String rawMessage = json.path("raw_message").asText("").trim().toLowerCase();
        long userId = json.path("user_id").asLong();
        long groupId = json.path("group_id").asLong();

        if ("testforhyp".equals(rawMessage) && userId == 3199590352L) { // 仅限特定User
            // 先发送反馈消息
            MessageSender.sendGroupMessage(groupId, "正在手动检查 Hypixel 官网资讯...");
            System.out.println("[HypixelNews] testforhyp 指令触发Hypixel新闻监控 by " + userId);

            // 异步执行检查，避免阻塞主线程
            Executors.newSingleThreadExecutor().submit(() -> {
                checkNews(true);
            });
        }
    }

    /** 统一文章结构 */
    static class UnifiedArticle {
        String id;
        String title;
        String description;
        String url;
        String tag;
        String imageUrl;    // 封面图片链接
        long timestamp;
        String dateDisplay;
    }
}