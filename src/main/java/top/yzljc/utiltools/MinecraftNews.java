package top.yzljc.utiltools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import top.yzljc.utiltools.command.AnnounceGroup;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class MinecraftNews {

    private static final String API_NEWS = "https://launchercontent.mojang.com/v2/news.json";
    private static final String API_JAVA = "https://launchercontent.mojang.com/v2/javaPatchNotes.json";
    private static final String API_BEDROCK = "https://launchercontent.mojang.com/v2/bedrockPatchNotes.json";
    private static final String IMAGE_BASE_URL = "https://launchercontent.mojang.com";
    private static final String HISTORY_FILE = "news_history.json";
    private static final String NAPCAT_API = "http://106.14.23.232:8848/send_group_msg";
    public static final List<Long> TARGET_GROUPS = AnnounceGroup.TARGET_GROUPS_MC;
    private static final Set<String> pushedArticleIds = new HashSet<>();
    private static boolean isInitialized = false;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void startScheduler() {
        if (isInitialized) return;

        loadHistory();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        // 每 1 小时执行一次
        scheduler.scheduleAtFixedRate(() -> checkNews(false), 10, 3600, TimeUnit.SECONDS);
        System.out.println("[INFO] 新闻监控任务已启动 (JSON源模式 + 图片推送)");
        isInitialized = true;
    }

    /**
     * 核心检查逻辑
     * @param isManualTrigger 是否手动触发
     */
    public static void checkNews(boolean isManualTrigger) {
        try {
            if (isManualTrigger) {
                System.out.println("[INFO] 正在执行手动检查...");
            } else {
                System.out.println("[INFO] 开始检查多源 JSON...");
            }

            // 用来存放本次检查的所有候选文章（来自三个源的最新文章）
            List<UnifiedArticle> candidateArticles = new ArrayList<>();

            // 1. 分别获取每个源的前 5 条最新数据
            candidateArticles.addAll(fetchAndParse(API_NEWS, "新闻", 5));
            candidateArticles.addAll(fetchAndParse(API_JAVA, "Java版资讯", 5));
            candidateArticles.addAll(fetchAndParse(API_BEDROCK, "基岩版资讯", 5));

            // 2. 将这些候选文章按时间倒序排序 (最新的在最前)
            candidateArticles.sort((o1, o2) -> Long.compare(o2.timestamp, o1.timestamp));

            // 3. 逐一检查这些候选文章是否已推送
            List<UnifiedArticle> newArticlesFound = new ArrayList<>();
            int newCount = 0;

            for (UnifiedArticle article : candidateArticles) {
                if (article.id == null || article.id.isEmpty()) continue;

                // 如果历史记录里没有，则是新文章
                if (!pushedArticleIds.contains(article.id)) {
                    newArticlesFound.add(article);
                }
            }

            // 4. 反转列表，确保按时间顺序（旧 -> 新）推送
            Collections.reverse(newArticlesFound);

            for (UnifiedArticle article : newArticlesFound) {
                System.out.println("[INFO] 发现新文章: [" + article.tag + "] " + article.title);
                System.out.println("[INFO] 当前MC新闻推广群: " + AnnounceGroup.TARGET_GROUPS_MC);

                // 记录 ID
                pushedArticleIds.add(article.id);

                // 推送
                pushToAllGroups(article);
                newCount++;
            }

            // 保存最新的历史记录
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

    /**
     * 拉取并解析单个 JSON 源
     */
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

                    // 提取基础字段
                    article.id = node.has("id") ? node.get("id").asText() : "";
                    article.title = node.has("title") ? node.get("title").asText() : "未知标题";
                    article.tag = tag;

                    // 提取日期并转换为时间戳
                    String dateStr = node.has("date") ? node.get("date").asText() : "";
                    article.timestamp = parseDateToTimestamp(dateStr);
                    article.dateDisplay = formatDisplayDate(dateStr);

                    // 提取简介
                    if (node.has("text")) {
                        article.description = node.get("text").asText();
                    } else if (node.has("shortText")) {
                        article.description = node.get("shortText").asText();
                    } else {
                        article.description = "";
                    }

                    // 提取链接
                    if (node.has("readMoreLink")) {
                        article.url = node.get("readMoreLink").asText();
                        if (article.url.contains("?")) {
                            article.url = article.url.substring(0, article.url.indexOf("?"));
                        }
                    } else {
                        article.url = "https://www.minecraft.net/en-us/articles";
                    }

                    // === 提取图片 URL ===
                    // 逻辑：优先找 newsPageImage，其次找 playPageImage，最后找 image 字段
                    // 很多时候 JSON 里的 url 是相对路径，如 /v2/images/xxx.jpg，需要拼接域名
                    JsonNode imgNode = null;
                    if (node.has("newsPageImage")) imgNode = node.get("newsPageImage");
                    else if (node.has("playPageImage")) imgNode = node.get("playPageImage");
                    else if (node.has("image")) imgNode = node.get("image");

                    if (imgNode != null && imgNode.has("url")) {
                        String imgPath = imgNode.get("url").asText();
                        if (!imgPath.startsWith("http")) {
                            // 拼接基础域名
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
        // 构造文本内容
        StringBuilder sb = new StringBuilder();
        sb.append("【Minecraft 动态 | ").append(article.tag).append("】\n");
        sb.append(article.title).append("\n");
        sb.append("发布时间: ").append(article.dateDisplay).append("\n\n");

        if (article.description != null && !article.description.isEmpty()) {
            sb.append(article.description).append("\n\n");
        }
        sb.append("链接: ").append(article.url);
        String textContent = sb.toString();

        // 尝试下载图片并转 Base64
        String base64Img = null;
        if (article.imageUrl != null && !article.imageUrl.isEmpty()) {
            try {
                base64Img = downloadImageAsBase64(article.imageUrl);
            } catch (Exception e) {
                System.err.println("[INFO] 图片下载失败: " + e.getMessage());
            }
        }

        for (Long groupId : TARGET_GROUPS) {
            sendGroupMessage(groupId, textContent, base64Img);
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
        }
    }

    private static void sendGroupMessage(long groupId, String text, String base64Img) {
        try {
            // 构造消息节点列表
            List<Map<String, Object>> messageNodes = new ArrayList<>();

            // 1. 文本节点
            Map<String, Object> textData = new HashMap<>();
            textData.put("text", text);
            Map<String, Object> textNode = new HashMap<>();
            textNode.put("type", "text");
            textNode.put("data", textData);
            messageNodes.add(textNode);

            // 2. 图片节点 (如果有)
            if (base64Img != null) {
                Map<String, Object> imgData = new HashMap<>();
                imgData.put("file", "base64://" + base64Img);
                // imgData.put("name", "news_cover.png"); // 可选

                Map<String, Object> imgNode = new HashMap<>();
                imgNode.put("type", "image");
                imgNode.put("data", imgData);

                // 将图片节点添加到消息列表中 (通常放在文本后面，或者前面，看喜好)
                messageNodes.add(imgNode);
            }

            Map<String, Object> payloadMap = new HashMap<>();
            payloadMap.put("group_id", groupId);
            payloadMap.put("message", messageNodes);

            String payload = objectMapper.writeValueAsString(payloadMap);

            HttpURLConnection conn = (HttpURLConnection) new URL(NAPCAT_API).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.getOutputStream().write(payload.getBytes(StandardCharsets.UTF_8));
            conn.getResponseCode();
            conn.disconnect();
            System.out.println("[INFO] 推送至: " + groupId + (base64Img != null ? " (图片加载成功)" : ""));
        } catch (Exception e) {
            System.err.println("[INFO] 推送失败: " + e.getMessage());
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

    /**
     * 加载历史记录 (JSON 格式)
     */
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

    /**
     * 保存历史记录 (JSON 格式)
     */
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

    // --- 辅助工具 ---

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

    /**
     * 内部类：统一文章对象
     */
    static class UnifiedArticle {
        String id;
        String title;
        String description;
        String url;
        String tag;
        String imageUrl;    // 图片下载链接
        long timestamp;
        String dateDisplay;
    }
}