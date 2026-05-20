package top.yzljc.qqbot.functions.minecraftnews;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.chat.GroupMessage;
import top.yzljc.qqbot.chat.MessageSegment;
import top.yzljc.qqbot.command.Command;
import top.yzljc.qqbot.command.CommandExecutor;
import top.yzljc.qqbot.command.CommandSender;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.ConfigFile;
import top.yzljc.qqbot.config.groups.GroupConfigManager;
import top.yzljc.qqbot.service.request.HttpService;
import top.yzljc.qqbot.service.thread.ThreadManager;
import top.yzljc.qqbot.service.tools.FT;
import top.yzljc.qqbot.service.userinfo.GetGroupInfo;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class MinecraftNews implements CommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(MinecraftNews.class);

    private static final String API_PRIMARY = "https://net-secondary.web.minecraft-services.net/api/v1.0/zh-cn/search?pageSize=5&sortType=Recent&category=News&newsOnly=true";
    private static final String API_SECONDARY = "https://www.minecraft.net/content/minecraftnet/language-masters/en-us/_jcr_content.articles.page-1.json";

    private static final String HISTORY_FILE = ConfigFile.MINECRAFT_NEWS.getFileName();
    private static final String BASE_URL = "https://www.minecraft.net";

    public static final Set<Long> TARGET_GROUPS = GetGroupInfo.fetchAllGroupIds();
    private static final Set<String> pushedArticleIds = new HashSet<>();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isAdmin()) {
            sender.reply("你没有权限执行此命令", false);
            return true;
        }
        ThreadManager.execute(() -> checkNews(true));
        GroupMessage.chatMessage(sender.groupId(), "正在手动检查 Minecraft 最新资讯...");
        return true;
    }

    public static synchronized void checkNews(boolean isManualTrigger) {
        try {
            if (isManualTrigger) {
                log.info("正在执行手动检查……");
            } else {
                log.info("开始检查 Minecraft 新闻源……");
            }

            List<UnifiedArticle> primaryList = fetchAndParsePrimary();
            List<UnifiedArticle> secondaryList = fetchAndParseSecondary();

            List<UnifiedArticle> candidateArticles = new ArrayList<>();
            candidateArticles.addAll(primaryList);
            candidateArticles.addAll(secondaryList);

            candidateArticles.sort((o1, o2) -> Long.compare(o2.timestamp, o1.timestamp));

            List<UnifiedArticle> newArticlesFound = new ArrayList<>();
            int newCount = 0;

            for (UnifiedArticle article : candidateArticles) {
                if (article.id == null || article.id.isEmpty()) continue;

                if (!pushedArticleIds.contains(article.id)) {
                    boolean alreadyInList = false;
                    for (UnifiedArticle added : newArticlesFound) {
                        if (added.id.equals(article.id)) {
                            alreadyInList = true;
                            break;
                        }
                    }
                    if (!alreadyInList) {
                        newArticlesFound.add(article);
                    }
                }
            }

            Collections.reverse(newArticlesFound);

            for (UnifiedArticle article : newArticlesFound) {
                log.info("发现新文章：[{}] {}", article.tag, article.title);
                pushedArticleIds.add(article.id);
                pushToAllGroups(article);
                newCount++;
            }

            if (newCount > 0) {
                saveHistory();
            }

            if (isManualTrigger && newCount == 0) {
                log.info("手动检查完成，未发现新文章");
            }

        } catch (Exception e) {
            log.warn("检查失败：{}", e.getMessage(), e);
        }
    }

    private static JsonNode fetchJson(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .GET()
                    .build();
            HttpResponse<String> response = HttpService.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return objectMapper.readTree(response.body());
            }
            return null;
        } catch (Exception e) {
            log.warn("HTTP GET failed for {}: {}", url, e.getMessage());
            return null;
        }
    }

    private static List<UnifiedArticle> fetchAndParsePrimary() {
        List<UnifiedArticle> list = new ArrayList<>();
        try {
            JsonNode root = fetchJson(API_PRIMARY);
            if (root == null || !root.has("result")) return list;

            JsonNode results = root.get("result").get("results");
            if (results != null && results.isArray()) {
                for (JsonNode node : results) {
                    UnifiedArticle article = new UnifiedArticle();
                    article.title = node.has("title") ? FT.unescape(node.get("title").asText()) : "未知标题";
                    article.tag = "新闻资讯";
                    article.url = node.has("url") ? node.get("url").asText() : "";
                    article.id = article.url;

                    long timeSeconds = node.has("time") ? node.get("time").asLong() : 0;
                    article.timestamp = timeSeconds * 1000;
                    article.dateDisplay = formatTimestamp(article.timestamp);

                    article.description = node.has("description") ? FT.unescape(node.get("description").asText()) : "";
                    article.author = node.has("author") ? node.get("author").asText() : "Staff";
                    article.imageUrl = node.has("image") ? node.get("image").asText() : "";

                    if (article.id != null && !article.id.isEmpty()) {
                        list.add(article);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("主源解析失败：{}", e.getMessage());
        }
        return list;
    }

    private static List<UnifiedArticle> fetchAndParseSecondary() {
        List<UnifiedArticle> list = new ArrayList<>();
        try {
            JsonNode root = fetchJson(API_SECONDARY);
            if (root == null) return list;

            JsonNode grid = root.get("article_grid");
            if (grid != null && grid.isArray()) {
                int limit = 5;
                for (JsonNode item : grid) {
                    if (list.size() >= limit) break;

                    UnifiedArticle article = new UnifiedArticle();
                    JsonNode tile = item.get("default_tile");
                    if (tile == null) continue;

                    article.title = tile.has("title") ? FT.unescape(tile.get("title").asText()) : "未知标题";
                    article.tag = "新闻快讯";

                    String relUrl = item.has("article_url") ? item.get("article_url").asText() : "";
                    article.url = relUrl.startsWith("/") ? BASE_URL + relUrl : relUrl;
                    article.id = article.url;

                    article.timestamp = System.currentTimeMillis();
                    article.dateDisplay = "未知时间";

                    article.description = tile.has("sub_header") ? FT.unescape(tile.get("sub_header").asText()) : "";
                    article.description += "\n\n注：此消息为新闻快讯，内容较为简略，几小时之后会再次推送完整资讯！";
                    article.author = "未知作者";

                    if (tile.has("image")) {
                        JsonNode imgNode = tile.get("image");
                        String imgRel = imgNode.has("imageURL") ? imgNode.get("imageURL").asText() : "";
                        article.imageUrl = imgRel.startsWith("/") ? BASE_URL + imgRel : imgRel;
                    } else {
                        article.imageUrl = "";
                    }

                    if (!article.id.isEmpty()) {
                        list.add(article);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("辅助源解析失败：{}", e.getMessage());
        }
        return list;
    }

    private static void pushToAllGroups(UnifiedArticle article) {
        log.info(">>> 1. 开始提取网页纯文本: {}", article.url);
        String articleText = ArticleScraper.fetchPureText(article.url);
        if (articleText.isEmpty()) {
            log.warn(">>> [注意] 提取网页正文为空，使用描述兜底");
            articleText = article.description;
        } else {
            log.info(">>> [成功] 网页正文提取完毕，长度: {}", articleText.length());
        }

        log.info(">>> 2. 开始请求 AI 进行总结...");
        List<String> aiMessages = AtriNewsSummarizer.summarize(article.title, articleText);
        log.info(">>> [成功] AI 总结完毕，共分成 {} 段", aiMessages.size());

        log.info(">>> 3. 开始构建 MessageSegment Nodes...");
        List<MessageSegment> nodes = new ArrayList<>();
        String atriUin = "3199590352";
        String atriName = "YZ_Ljc_";

        StringBuilder headerMsg = new StringBuilder();
        headerMsg.append("【Minecraft 动态 | 新闻资讯】\n");
        headerMsg.append("标题：").append(article.title).append("\n");
        if (article.dateDisplay != null && !article.dateDisplay.isEmpty()) {
            headerMsg.append("时间：").append(article.dateDisplay).append("\n");
        }
        headerMsg.append("原文链接：").append(article.url);

        nodes.add(GroupMessage.createTextNode(headerMsg.toString(), atriUin, atriName));

        if (article.imageUrl != null && !article.imageUrl.isEmpty()) {
            String base64Img = downloadImageAsBase64(article.imageUrl);
            if (base64Img != null) {
                nodes.add(GroupMessage.createImageNode("base64://" + base64Img, atriUin, atriName));
            }
        }

        for (String msg : aiMessages) {
            nodes.add(GroupMessage.createTextNode(msg, atriUin, atriName));
        }

        String[] textVars = {
                "标题: " + article.title,
                "时间: " + article.dateDisplay,
                "作者: " + article.author,
                "简介: " + article.description
        };

        log.info(">>> 4. 准备遍历群聊发送，总群数: {}", TARGET_GROUPS.size());
        log.info(">>> 5. 正在执行第一次转发: {}", Config.getInstance().getDebugGroupId());

        long messageId = GroupMessage.forwardMessage(
                Config.getInstance().getDebugGroupId(),
                nodes,
                "【Minecraft 动态 | 新闻资讯】",
                "点击查看详细总结",
                textVars
        );

        try {
            Thread.sleep(2000);
        } catch (InterruptedException ignored) {
        }

        for (Long groupId : TARGET_GROUPS) {
            if (groupId == Config.getInstance().getDebugGroupId()) continue;
            if (!GroupConfigManager.isFeatureEnabled(groupId, "mc_news")) {
                continue;
            }

            GroupMessage.forwardTo(groupId, messageId);
            log.info(">>> 6. 正在转发到群 {}...", groupId);
        }
        log.info(">>> 7. 本条新闻转发执行结束");
    }

    private static String downloadImageAsBase64(String imageUrl) {
        try {
            String safeUrl = imageUrl.replace(" ", "%20");
            URL url = new URI(safeUrl).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setConnectTimeout(5000);
            conn.setReadTimeout(15000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");

            try (java.io.InputStream in = conn.getInputStream();
                 java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    out.write(buffer, 0, n);
                }

                byte[] imgBytes = out.toByteArray();
                if (imgBytes.length > 100) {
                    return Base64.getEncoder().encodeToString(imgBytes);
                }
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            log.warn("流式图片下载失败: {}，原因: {}", imageUrl, e.getMessage());
        }
        return null;
    }

    public static void loadHistory() {
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
            log.warn("读取历史记录失败，将重新创建");
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
            log.warn("保存历史记录失败：{}", e.getMessage());
        }
    }

    private static String formatTimestamp(long timestampMillis) {
        if (timestampMillis == 0) return "未知时间";
        try {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestampMillis), ZoneId.of("Asia/Shanghai"))
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        } catch (Exception e) {
            return "时间解析错误";
        }
    }

    static class UnifiedArticle {
        String id;
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