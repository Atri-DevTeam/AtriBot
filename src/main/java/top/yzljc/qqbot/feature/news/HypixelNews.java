package top.yzljc.qqbot.feature.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import top.yzljc.qqbot.service.thread.ThreadManager;
import top.yzljc.qqbot.chat.GroupMessage;
import top.yzljc.qqbot.chat.impl.MessageUtils;
import top.yzljc.qqbot.command.Command;
import top.yzljc.qqbot.command.CommandExecutor;
import top.yzljc.qqbot.command.CommandSender;
import top.yzljc.qqbot.config.ConfigFile;
import top.yzljc.qqbot.config.groups.GroupConfigManager;
import top.yzljc.qqbot.service.userinfo.GetGroupInfo;

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

import java.net.URI;
import java.net.URISyntaxException;
import java.net.MalformedURLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.service.tools.FT;

public class HypixelNews implements CommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(HypixelNews.class);

    private static final String NEWS_URL = "https://hypixel.net/forums/news-and-announcements.4/";
    private static final String ARTICLE_BASE = "https://hypixel.net";
    private static final String HISTORY_FILE = ConfigFile.HYPIXEL_NEWS.getFileName();
    public static final Set<Long> TARGET_GROUPS = GetGroupInfo.fetchAllGroupIds();

    private static final Set<String> pushedArticleIds = new HashSet<>();
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean onCommand(CommandSender sender, Command command,String label,String[] args){
        if (!sender.isAdmin()) {
            sender.reply("你没有权限执行此命令", false);
            return true;
        }
        ThreadManager.execute(() -> checkNews(true));
        GroupMessage.chatMessage(sender.groupId(), "正在手动检查 Hypixel 官网资讯...");
        return true;
    }

    public static synchronized void checkNews(boolean isManualTrigger) {
        try {
            if (isManualTrigger) {
                log.info("正在执行 Hypixel 手动检查……");
            } else {
                log.info("Hypixel 自动新闻检查中……");
            }
            List<UnifiedArticle> candidateArticles = fetchAndParse();

            candidateArticles.sort(Comparator.comparingLong(a -> -a.timestamp));
            List<UnifiedArticle> newArticlesFound = new ArrayList<>();

            for (UnifiedArticle article : candidateArticles) {
                if (article.id == null || article.id.isEmpty()) continue;
                if (!pushedArticleIds.contains(article.id)) {
                    newArticlesFound.add(article);
                }
            }

            Collections.reverse(newArticlesFound);

            int newCount = 0;
            for (UnifiedArticle article : newArticlesFound) {
                log.info("发现新Hypixel文章：{}", article.title);
                log.info("当前Hypixel新闻推广群：{}", TARGET_GROUPS);
                pushedArticleIds.add(article.id);
                pushToAllGroups(article);
                newCount++;
            }
            if (newCount > 0) saveHistory();
            if (isManualTrigger && newCount == 0) {
                log.info("Hypixel手动检查结束，无新文章");
            }
        } catch (Exception e) {
            log.warn("Hypixel 新闻检查失败：{}", e.getMessage(), e);
        }
    }

    private static List<UnifiedArticle> fetchAndParse() {
        List<UnifiedArticle> list = new ArrayList<>();
        try {
            Document doc = Jsoup.connect(HypixelNews.NEWS_URL).userAgent("Mozilla/5.0").get();
            // Hypixel新闻列表所有帖子都在 .structItem--thread 结构下
            Elements posts = doc.select("div.structItem--thread");
            int count = 0;
            for (Element post : posts) {
                if (count >= 5) break;

                Element linkElem = post.selectFirst(".structItem-title a");
                if (linkElem == null) continue;
                String url = ARTICLE_BASE + linkElem.attr("href");

                String title = FT.unescape(linkElem.text());

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

            if (!GroupConfigManager.isFeatureEnabled(groupId,"hyp_news")) {
                continue;
            }

            GroupMessage.chatMessage(groupId, textContent, base64Img, MessageUtils.ImageType.BASE64);
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
        }
    }

    private static String downloadImageAsBase64(String imageUrl) {
        try {
            URL url = new URI(imageUrl).toURL();
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
            if (root.isArray()) {
                for (JsonNode idNode : root) {
                    pushedArticleIds.add(idNode.asText());
                }
            }
        } catch (IOException e) {
            log.warn("Hypixel 历史记录读取失败，将重新创建");
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
            log.error("写入 Hypixel 历史记录失败：{}", e.getMessage(), e);
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
