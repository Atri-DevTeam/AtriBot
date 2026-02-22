package top.yzljc.qqbot.feature.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import top.yzljc.qqbot.botkits.thread.ThreadManager;
import top.yzljc.qqbot.botkits.userinfo.GetGroupInfo;
import top.yzljc.qqbot.command.process.Command;
import top.yzljc.qqbot.command.process.CommandExecutor;
import top.yzljc.qqbot.command.process.CommandSender;
import top.yzljc.qqbot.config.ConfigFile;
import top.yzljc.qqbot.config.groups.GroupConfigManager;
import top.yzljc.qqbot.botkits.message.MessageSender;

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

import java.net.URI;
import java.net.URISyntaxException;
import java.net.MalformedURLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.botkits.tools.FT;

public class MinecraftNews implements CommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(MinecraftNews.class);

    // 主 API (搜索接口)
    private static final String API_PRIMARY = "https://net-secondary.web.minecraft-services.net/api/v1.0/zh-cn/search?pageSize=5&sortType=Recent&category=News&newsOnly=true";
    // 辅助 API (CMS 内容接口)
    private static final String API_SECONDARY = "https://www.minecraft.net/content/minecraftnet/language-masters/en-us/_jcr_content.articles.page-1.json";

    private static final String HISTORY_FILE = ConfigFile.MINECRAFT_NEWS.getFileName();
    private static final String BASE_URL = "https://www.minecraft.net";

    public static final Set<Long> TARGET_GROUPS = GetGroupInfo.fetchAllGroupIds();
    private static final Set<String> pushedArticleIds = new HashSet<>();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isAdmin()){
            sender.reply("你没有权限执行此命令", false);
            return true;
        }
        ThreadManager.execute(() -> checkNews(true));
        MessageSender.sendGroupMessage(sender.getGroupId(), "正在手动检查 Minecraft 最新资讯...");
        return true;
    }

    public static void checkNews(boolean isManualTrigger) {
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

                // 去重判断
                if (!pushedArticleIds.contains(article.id)) {
                    // 二次去重：防止本次检查中主源和辅助源查到同一篇新文章，导致重复添加
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

    private static List<UnifiedArticle> fetchAndParsePrimary() {
        List<UnifiedArticle> list = new ArrayList<>();
        HttpURLConnection connection = null;
        try {
            // 使用 HttpURLConnection 替代直接 URL 读取，以便设置超时
            URL url = new URI(MinecraftNews.API_PRIMARY).toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10000); // 10秒连接超时
            connection.setReadTimeout(30000);    // 30秒读取超时
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");

            try (InputStream in = connection.getInputStream()) {
                JsonNode root = objectMapper.readTree(in);
                JsonNode resultNode = root.get("result");
                if (resultNode == null) return list;
                JsonNode results = resultNode.get("results");

                if (results != null && results.isArray()) {
                    for (JsonNode node : results) {
                        UnifiedArticle article = new UnifiedArticle();
                        article.title = node.has("title") ? FT.unescape(node.get("title").asText()) : "未知标题";
                        article.tag = "Minecraft 资讯";
                        article.url = node.has("url") ? node.get("url").asText() : "";
                        article.id = article.url; // ID = URL

                        long timeSeconds = node.has("time") ? node.get("time").asLong() : 0;
                        article.timestamp = timeSeconds * 1000;
                        article.dateDisplay = formatTimestamp(article.timestamp);

                        article.description = node.has("description") ? FT.unescape(node.get("description").asText()) : "";
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
            }
        } catch (Exception e) {
            log.warn("主源解析失败：{}", e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return list;
    }

    private static List<UnifiedArticle> fetchAndParseSecondary() {
        List<UnifiedArticle> list = new ArrayList<>();
        HttpURLConnection connection = null;
        try {
            URL url = new URI(MinecraftNews.API_SECONDARY).toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10000); // 10秒连接超时
            connection.setReadTimeout(30000);    // 30秒读取超时
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");

            try (InputStream in = connection.getInputStream()) {
                JsonNode root = objectMapper.readTree(in);
                JsonNode grid = root.get("article_grid");

                if (grid != null && grid.isArray()) {
                    int limit = 5; // 如果还是他妈的漏新闻就给这个数值改大，我就不信了
                    for (JsonNode item : grid) {
                        if (list.size() >= limit) break;

                        UnifiedArticle article = new UnifiedArticle();

                        JsonNode tile = item.get("default_tile");
                        if (tile == null) continue;

                        article.title = tile.has("title") ? FT.unescape(tile.get("title").asText()) : "未知标题";
                        article.tag = "Minecraft 快讯";

                        String relUrl = item.has("article_url") ? item.get("article_url").asText() : "";
                        if (relUrl.startsWith("/")) {
                            article.url = BASE_URL + relUrl;
                        } else {
                            article.url = relUrl;
                        }
                        article.id = article.url;

                        article.timestamp = System.currentTimeMillis();
                        article.dateDisplay = "未知时间";

                        article.description = tile.has("sub_header") ? FT.unescape(tile.get("sub_header").asText()) : "";
                        article.description += "\n\n注：此消息为新闻快讯，内容较为简略，几小时之后会再次推送完整资讯！";

                        article.author = "未知作者";

                        if (tile.has("image")) {
                            JsonNode imgNode = tile.get("image");
                            String imgRel = imgNode.has("imageURL") ? imgNode.get("imageURL").asText() : "";
                            if (imgRel.startsWith("/")) {
                                article.imageUrl = BASE_URL + imgRel;
                            } else {
                                article.imageUrl = imgRel;
                            }
                        } else {
                            article.imageUrl = "";
                        }

                        if (!article.id.isEmpty()) {
                            list.add(article);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("辅助源解析失败：{}", e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return list;
    }

    private static void pushToAllGroups(UnifiedArticle article) {
        StringBuilder sb = new StringBuilder();
        sb.append("【Minecraft 动态 | ").append(article.tag).append("】\n");
        sb.append(article.title).append("\n\n");

        if (article.author != null && !article.author.isEmpty()) {
            sb.append("作者: ").append(article.author).append("\n");
        }

        if (article.dateDisplay != null && !article.dateDisplay.isEmpty()) {
            sb.append("时间: ").append(article.dateDisplay).append("\n\n");
        }

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
                log.warn("图片下载失败：{}", e.getMessage());
            }
        }

        for (Long groupId : TARGET_GROUPS) {
            if (!GroupConfigManager.isFeatureEnabled(groupId, "mc_news")) {
                continue;
            }
            MessageSender.sendGroupMessage(groupId, textContent, base64Img);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
            }
        }
    }

    private static String downloadImageAsBase64(String imageUrl) {
        try {
            URL url = new URI(imageUrl).toURL();
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
        } catch (URISyntaxException | MalformedURLException e) {
            log.error("URL格式错误：{}", imageUrl, e);
            return null;  // 或者返回空字符串、默认图片等
        } catch (IOException e) {
            log.error("下载图片失败：{}", imageUrl, e);
            return null;
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