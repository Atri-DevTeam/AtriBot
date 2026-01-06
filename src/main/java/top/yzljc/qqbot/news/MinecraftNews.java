package top.yzljc.qqbot.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.GroupConfigManager;
import top.yzljc.qqbot.config.Settings;
import top.yzljc.qqbot.messages.MessageSender;
import top.yzljc.qqbot.utils.GroupList;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MinecraftNews {

    // 新的 API 地址
    private static final String API_NEWS_SEARCH = "https://net-secondary.web.minecraft-services.net/api/v1.0/zh-cn/search?pageSize=5&sortType=Recent&category=News&newsOnly=true";
    private static final String HISTORY_FILE = "news_history.json";

    public static final Set<Long> TARGET_GROUPS = GroupList.fetchAllGroupIds();
    // 这里使用 URL 作为唯一标识 ID，因为新 API 返回并没有显式的 ID 字段，URL 是唯一的
    private static final Set<String> pushedArticleIds = new HashSet<>();
    private static boolean isInitialized = false;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    static Settings settings = Config.getInstance();
    private static final List<Long> admins = settings.getAdminUids();

    public static boolean processCommand(long userId, long groupId, String rawMessage) {
        if (rawMessage == null) return false;
        String msgLower = rawMessage.trim().toLowerCase();

        if ("testformc".equals(msgLower)) {
            if (admins.contains(userId)) {
                MessageSender.sendGroupMessage(groupId, "正在手动检查 Minecraft 最新咨询...");

                Executors.newSingleThreadExecutor().submit(() -> {
                    checkNews(true);
                });
            } else {
                System.out.println("[INFO] 用户 " + userId + " 尝试触发更新但无权限");
            }
            return true;
        }
        return false;
    }

    public static void startScheduler() {
        if (isInitialized) return;

        loadHistory();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> checkNews(false), 10, 3600, TimeUnit.SECONDS);
        System.out.println("[INFO] Minecraft新闻抓取任务已启动!");
        isInitialized = true;
    }

    /**
     * 核心检查逻辑
     */
    public static void checkNews(boolean isManualTrigger) {
        try {
            if (isManualTrigger) {
                System.out.println("[INFO] 正在执行手动检查...");
            } else {
                System.out.println("[INFO] 开始检查 Minecraft 新闻源...");
            }

            List<UnifiedArticle> candidateArticles = fetchAndParse(API_NEWS_SEARCH, "Minecraft 资讯");

            candidateArticles.sort((o1, o2) -> Long.compare(o2.timestamp, o1.timestamp));

            List<UnifiedArticle> newArticlesFound = new ArrayList<>();
            int newCount = 0;

            for (UnifiedArticle article : candidateArticles) {
                if (article.id == null || article.id.isEmpty()) continue;
                if (!pushedArticleIds.contains(article.id)) {
                    newArticlesFound.add(article);
                }
            }

            Collections.reverse(newArticlesFound);

            for (UnifiedArticle article : newArticlesFound) {
                System.out.println("[INFO] 发现新文章: [" + article.tag + "] " + article.title);

                pushedArticleIds.add(article.id);
                pushToAllGroups(article);
                newCount++;
            }

            if (newCount > 0) {
                saveHistory();
            }

            if (isManualTrigger && newCount == 0) {
                System.out.println("[INFO] 手动检查完成，未发现新文章!");
            }

        } catch (Exception e) {
            System.err.println("[INFO] 检查失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static List<UnifiedArticle> fetchAndParse(String urlStr, String tag) {
        List<UnifiedArticle> list = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(new URL(urlStr));

            JsonNode resultNode = root.get("result");
            if (resultNode == null) return list;

            JsonNode results = resultNode.get("results");

            if (results != null && results.isArray()) {
                for (JsonNode node : results) {
                    UnifiedArticle article = new UnifiedArticle();

                    article.title = node.has("title") ? node.get("title").asText() : "未知标题";
                    article.tag = tag;

                    // 使用 URL 作为唯一标识 ID
                    article.url = node.has("url") ? node.get("url").asText() : "";
                    article.id = article.url;

                    long timeSeconds = node.has("time") ? node.get("time").asLong() : 0;
                    article.timestamp = timeSeconds * 1000; // 转换为毫秒用于排序
                    article.dateDisplay = formatTimestamp(article.timestamp);

                    // 描述
                    article.description = node.has("description") ? node.get("description").asText() : "";
                    article.author = node.has("author") ? node.get("author").asText() : "Staff";

                    if (node.has("image")) {
                        article.imageUrl = node.get("image").asText();
                    } else {
                        article.imageUrl = "";
                    }

                    if (article.id != null && !article.id.isEmpty()) {
                        list.add(article);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[INFO] 解析源失败: " + e.getMessage());
        }
        return list;
    }

    private static void pushToAllGroups(UnifiedArticle article) {
        StringBuilder sb = new StringBuilder();
        sb.append("【Minecraft 动态 | ").append(article.tag).append("】\n");
        sb.append(article.title).append("\n");
        sb.append("作者：").append(article.author).append("\n");
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
                System.err.println("[INFO] 图片下载失败: " + e.getMessage());
            }
        }

        for (Long groupId : TARGET_GROUPS) {

            if (!GroupConfigManager.isFeatureEnabled(groupId,"mc_news")) {
                continue;
            }

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
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");

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
            System.err.println("[INFO] 读取历史记录失败，将重新创建。");
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

    // 新的时间格式化方法，直接处理 long 时间戳
    private static String formatTimestamp(long timestampMillis) {
        if (timestampMillis == 0) return "未知时间";
        try {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestampMillis), ZoneId.of("Asia/Shanghai")) // 使用系统默认或指定时区
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        } catch (Exception e) {
            return "时间解析错误";
        }
    }

    static class UnifiedArticle {
        String id; // 这里使用 URL 作为 ID
        String title;
        String description;
        String url;
        String author;
        String tag;
        String imageUrl;
        long timestamp;
        String dateDisplay;
    }
}