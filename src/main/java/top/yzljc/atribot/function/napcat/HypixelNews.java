package top.yzljc.atribot.function.napcat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import top.yzljc.atribot.chat.napcat.GroupInformation;
import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.chat.napcat.impl.MessageUtils;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.configuration.Properties;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigManager;
import top.yzljc.atribot.service.runtime.ThreadManager;
import top.yzljc.atribot.utils.FormatTools;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HypixelNews implements CommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(HypixelNews.class);
    private static final String NEWS_URL = "https://hypixel.net/forums/news-and-announcements.4/";
    private static final String ARTICLE_BASE = "https://hypixel.net";
    private static final String HISTORY_FILE = Properties.HYPIXEL_NEWS;
    public static final Set<String> TARGET_GROUPS = GroupInformation.fetchAllGroupIds();
    private static final Set<String> pushedArticleIds = new HashSet<>();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender.getPlatform() != Platform.NAPCAT_GROUP) return true;
        if (!GroupConfigManager.isFeatureEnabled(sender.getGroupId(), "hyp_news")) return true;
        if (!sender.hasPermission()) {
            sender.sendMessage("你没有权限执行此命令");
            return true;
        }
        ThreadManager.execute(() -> checkNews(true));
        GroupMessage.chatMessage(sender.getGroupId(), "正在手动检查 Hypixel 官网资讯...");
        return true;
    }

    public static synchronized void checkNews(boolean isManualTrigger) {
        try {
            if (isManualTrigger) log.info("正在执行 Hypixel 手动检查……");
            else log.info("Hypixel 自动新闻检查中……");
            List<UnifiedArticle> candidateArticles = fetchAndParse();
            candidateArticles.sort(Comparator.comparingLong(a -> -a.timestamp));
            List<UnifiedArticle> newArticlesFound = new ArrayList<>();
            for (UnifiedArticle article : candidateArticles) {
                if (article.id == null || article.id.isEmpty()) continue;
                if (!pushedArticleIds.contains(article.id)) newArticlesFound.add(article);
            }
            Collections.reverse(newArticlesFound);
            int newCount = 0;
            for (UnifiedArticle article : newArticlesFound) {
                log.info("发现新Hypixel文章：{}", article.title);
                pushedArticleIds.add(article.id);
                pushToAllGroups(article);
                newCount++;
            }
            if (newCount > 0) saveHistory();
            if (isManualTrigger && newCount == 0) log.info("Hypixel手动检查结束，无新文章");
        } catch (Exception e) {
            log.warn("Hypixel 新闻检查失败：{}", e.getMessage(), e);
        }
    }

    private static List<UnifiedArticle> fetchAndParse() {
        List<UnifiedArticle> list = new ArrayList<>();
        try {
            Document doc = Jsoup.connect(NEWS_URL).userAgent("Mozilla/5.0").get();
            Elements posts = doc.select("div.structItem--thread");
            int count = 0;
            for (Element post : posts) {
                if (count >= 5) break;
                Element linkElem = post.selectFirst(".structItem-title a");
                if (linkElem == null) continue;
                String url = ARTICLE_BASE + linkElem.attr("href");
                String title = FormatTools.unescape(linkElem.text());
                Element metaElem = post.selectFirst(".structItem-parts time");
                String dateStr = metaElem != null ? metaElem.attr("datetime") : "";
                long timestamp = parseDateToTimestamp(dateStr);
                String dateDisplay = formatDisplayDate(dateStr);
                String descPreview = "";
                Element excerptElem = post.selectFirst(".structItem-snippet");
                if (excerptElem != null) descPreview = excerptElem.text();
                String imageUrl = null;
                try {
                    Document artDoc = Jsoup.connect(url).userAgent("Mozilla/5.0").get();
                    Element imgElem = artDoc.selectFirst(".bbImage");
                    if (imgElem != null)
                        imageUrl = imgElem.hasAttr("data-url") ? imgElem.attr("data-url") : imgElem.attr("src");
                } catch (Exception ignored) {
                }
                UnifiedArticle article = new UnifiedArticle();
                article.id = url;
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
            log.warn("Hypixel解析失败：{}", e.getMessage(), e);
        }
        return list;
    }

    private static void pushToAllGroups(UnifiedArticle article) {
        StringBuilder sb = new StringBuilder();
        sb.append("【Hypixel 官网资讯】\n").append(article.title).append("\n发布时间: ").append(article.dateDisplay).append("\n\n");
        if (article.description != null && !article.description.isEmpty())
            sb.append(article.description).append("\n\n");
        sb.append("链接: ").append(article.url);
        String textContent = sb.toString();
        String base64Img = null;
        if (article.imageUrl != null && !article.imageUrl.isEmpty()) {
            try {
                base64Img = downloadImageAsBase64(article.imageUrl);
            } catch (Exception e) {
                log.error("[INFO] Hypixel图片下载失败: {}", e.getMessage());
            }
        }
        for (String groupId : TARGET_GROUPS) {
            if (!GroupConfigManager.isFeatureEnabled(groupId, "hyp_news")) continue;
            GroupMessage.chatMessage(groupId, textContent, base64Img, MessageUtils.ImageType.BASE64);
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
            try (InputStream in = conn.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[1024];
                int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                return Base64.getEncoder().encodeToString(out.toByteArray());
            } finally {
                conn.disconnect();
            }
        } catch (URISyntaxException | MalformedURLException e) {
            log.warn("URL 格式错误：{}", imageUrl);
            return null;
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
            if (root.isArray()) for (JsonNode idNode : root) pushedArticleIds.add(idNode.asText());
        } catch (IOException e) {
            log.warn("Hypixel 历史记录读取失败，将重新创建");
        }
    }

    private static void saveHistory() {
        try {
            ArrayNode arrayNode = objectMapper.createArrayNode();
            for (String id : pushedArticleIds) arrayNode.add(id);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(HISTORY_FILE), arrayNode);
        } catch (IOException e) {
            log.error("写入 Hypixel 历史记录失败：{}", e.getMessage(), e);
        }
    }

    private static String formatDisplayDate(String rawDate) {
        if (rawDate == null || rawDate.isEmpty()) return "未知时间";
        try {
            if (rawDate.length() >= 19)
                return LocalDateTime.parse(rawDate.substring(0, 19)).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            else return rawDate;
        } catch (Exception e) {
            return rawDate;
        }
    }

    private static long parseDateToTimestamp(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return 0;
        try {
            if (dateStr.length() >= 19)
                return LocalDateTime.parse(dateStr.substring(0, 19)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            else return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    static class UnifiedArticle {
        String id, title, description, url, tag, imageUrl;
        long timestamp;
        String dateDisplay;
    }
}
