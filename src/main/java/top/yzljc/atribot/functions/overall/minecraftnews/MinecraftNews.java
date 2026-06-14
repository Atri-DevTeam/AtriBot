package top.yzljc.atribot.functions.overall.minecraftnews;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.Atri;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.chat.onebot.GroupMessage;
import top.yzljc.atribot.chat.onebot.impl.MessageUtils;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.config.Config;
import top.yzljc.atribot.config.ConfigFile;
import top.yzljc.atribot.config.groups.GroupConfigManager;
import top.yzljc.atribot.functions.official.permission.GroupList;
import top.yzljc.atribot.service.request.HttpService;
import top.yzljc.atribot.service.ThreadManager;
import top.yzljc.atribot.utils.tools.FT;
import top.yzljc.atribot.chat.onebot.GroupInformation;

import java.io.File;
import java.io.IOException;
import java.net.URI;
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

    public static final Set<Long> TARGET_GROUPS = GroupInformation.fetchAllGroupIds();
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
                summaryNews(article);
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

    private static void summaryNews(UnifiedArticle article) {
        log.info(">>> 1. 开始提取网页纯文本: {}", article.url);
        String articleText = ArticleScraper.fetchPureText(article.url);
        if (articleText.isEmpty()) {
            log.warn(">>> [注意] 提取网页正文为空，使用描述兜底");
            articleText = article.description;
        } else {
            log.info(">>> [成功] 网页正文提取完毕，长度: {}", articleText.length());
        }

        log.info(">>> 2. 开始请求 AI 进行总结...");
        String aiMessages = AtriNewsSummarizer.summarize(article.title, articleText);
        log.info(">>> [成功] AI 总结完毕");
        String url = "https://www.yzljc.top/data/api/v2/atribot/function/mcnews";
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("title", article.title);
        requestBody.put("author", article.author);
        requestBody.put("time", article.dateDisplay);
        requestBody.put("headerImageUrl", article.imageUrl);
        requestBody.put("content", aiMessages);

        try {
            JsonNode resp = HttpService.postJson(url, requestBody);
            if (resp != null) {
                if (resp.get("news_id").asText() != null) {
                    String newsId = resp.get("news_id").asText();
                    int width = resp.get("img_w").asInt();
                    int height = resp.get("img_h").asInt();
                    pushNews(newsId, width, height, article.dateDisplay);
                    return;
                }
            }
            log.warn(">>> [失败] AI 总结结果上传失败，接口返回: {}", resp);
        } catch (Exception e) {
            log.warn(">>> [失败] AI 总结结果上传失败，异常信息: {}", e.getMessage(), e);
        }
    }

    private static void pushNews(String newsId, int w, int h, String t) {
        String url = "https://www.yzljc.top/data/api/v2/atribot/function/mcnews?news_id=" + newsId;

        try {
            HttpRequest preWarmRequest = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<Void> response = HttpService.httpClient.send(preWarmRequest, HttpResponse.BodyHandlers.discarding());
            HttpService.httpClient.send(preWarmRequest, HttpResponse.BodyHandlers.discarding());

            if (response.statusCode() != 200) {
                log.warn("请求新闻图片接口失败，状态码: {}", response.statusCode());
                return;
            }

            long messageId = GroupMessage.chatMessage(Config.getInstance().getNapcatDebugGroupUin(), url, MessageUtils.ImageType.URL);
            for (long gid : TARGET_GROUPS) {
                if (gid == Config.getInstance().getNapcatDebugGroupUin()) continue;
                if (!GroupConfigManager.isFeatureEnabled(gid, "mc_news")) continue;
                GroupMessage.forwardTo(gid, messageId);
            }
            String markdown = "![MC #" + w + "px #" + h + "px](" + url + ")\n\n" +
                    "> Minecraft官方发布了新的文章，点击图片查看详情！\n\n" +
                    "> 时间：" + t;

            List<String> activeGroups = GroupList.enabledGroups("mc_news");
            for (String groupOpenId : activeGroups) {
                Atri.getInstance().getChatService().sendActiveGroupMarkdownMessage(groupOpenId, TC.md(markdown));
            }

        } catch (Exception e) {
            log.warn("获取新闻图片失败: {}", e.getMessage());
        }
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
