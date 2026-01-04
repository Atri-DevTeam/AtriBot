package top.yzljc.qqbot.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import top.yzljc.qqbot.command.AnnounceGroup;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;
import top.yzljc.qqbot.messages.MessageSender; // 引入 MessageSender

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MinecraftNews {

    private static final String API_NEWS = "https://launchercontent.mojang.com/v2/news.json";
    private static final String API_JAVA = "https://launchercontent.mojang.com/v2/javaPatchNotes.json";
    private static final String API_BEDROCK = "https://launchercontent.mojang.com/v2/bedrockPatchNotes.json";
    private static final String IMAGE_BASE_URL = "https://launchercontent.mojang.com";
    private static final String HISTORY_FILE = "news_history.json";

    public static final List<Long> TARGET_GROUPS = AnnounceGroup.TARGET_GROUPS_MC;
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
                System.out.println("[MC-News] 用户 " + userId + " 尝试触发更新但无权限");
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
        System.out.println("[INFO] 新闻监控任务已启动 (JSON源模式 + 图片推送)");
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
                System.out.println("[INFO] 开始检查多源 JSON...");
            }

            List<UnifiedArticle> candidateArticles = new ArrayList<>();
            candidateArticles.addAll(fetchAndParse(API_NEWS, "新闻", 5));
            candidateArticles.addAll(fetchAndParse(API_JAVA, "Java版资讯", 5));
            candidateArticles.addAll(fetchAndParse(API_BEDROCK, "基岩版资讯", 5));

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
                System.out.println("[INFO] 手动检查完成，未发现新文章。");
            }

        } catch (Exception e) {
            System.err.println("[INFO] 检查失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static List<UnifiedArticle> fetchAndParse(String urlStr, String tag, int limit) {
        List<UnifiedArticle> list = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(new URL(urlStr));
            JsonNode entries = root.get("entries");

            if (entries != null && entries.isArray()) {
                int count = 0;
                for (JsonNode node : entries) {
                    if (count >= limit) break;

                    UnifiedArticle article = new UnifiedArticle();
                    article.id = node.has("id") ? node.get("id").asText() : "";
                    article.title = node.has("title") ? node.get("title").asText() : "未知标题";
                    article.tag = tag;

                    String dateStr = node.has("date") ? node.get("date").asText() : "";
                    article.timestamp = parseDateToTimestamp(dateStr);
                    article.dateDisplay = formatDisplayDate(dateStr);

                    if (node.has("text")) {
                        article.description = node.get("text").asText();
                    } else if (node.has("shortText")) {
                        article.description = node.get("shortText").asText();
                    } else {
                        article.description = "";
                    }

                    if (node.has("readMoreLink")) {
                        article.url = node.get("readMoreLink").asText();
                        if (article.url.contains("?")) {
                            article.url = article.url.substring(0, article.url.indexOf("?"));
                        }
                    } else {
                        article.url = "https://www.minecraft.net/en-us/articles";
                    }

                    JsonNode imgNode = null;
                    if (node.has("newsPageImage")) imgNode = node.get("newsPageImage");
                    else if (node.has("playPageImage")) imgNode = node.get("playPageImage");
                    else if (node.has("image")) imgNode = node.get("image");

                    if (imgNode != null && imgNode.has("url")) {
                        String imgPath = imgNode.get("url").asText();
                        if (!imgPath.startsWith("http")) {
                            article.imageUrl = IMAGE_BASE_URL + imgPath;
                        } else {
                            article.imageUrl = imgPath;
                        }
                    }

                    list.add(article);
                    count++;
                }
            }
        } catch (Exception e) {
            System.err.println("[INFO] 解析源失败 (" + tag + "): " + e.getMessage());
        }
        return list;
    }

    /**
     * 推送逻辑 (支持图片)
     */
    private static void pushToAllGroups(UnifiedArticle article) {
        StringBuilder sb = new StringBuilder();
        sb.append("【Minecraft 动态 | ").append(article.tag).append("】\n");
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
                System.err.println("[INFO] 图片下载失败: " + e.getMessage());
            }
        }

        for (Long groupId : TARGET_GROUPS) {
            MessageSender.sendGroupMessage(groupId, textContent, base64Img);

            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
        }
    }

    /**
     * 下载图片并转换为 Base64 字符串
     */
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

    private static String formatDisplayDate(String rawDate) {
        if (rawDate == null || rawDate.isEmpty()) return "未知时间";
        try {
            if (rawDate.contains("T")) {
                LocalDateTime ldt = LocalDateTime.parse(rawDate, DateTimeFormatter.ISO_DATE_TIME);
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
            if (dateStr.contains("T")) {
                return LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME)
                        .atZone(ZoneId.of("UTC"))
                        .toInstant()
                        .toEpochMilli();
            } else {
                return LocalDate.parse(dateStr)
                        .atStartOfDay(ZoneId.of("UTC"))
                        .toInstant()
                        .toEpochMilli();
            }
        } catch (Exception e) {
            return 0;
        }
    }

    static class UnifiedArticle {
        String id;
        String title;
        String description;
        String url;
        String tag;
        String imageUrl;
        long timestamp;
        String dateDisplay;
    }
}