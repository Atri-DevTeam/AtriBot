package top.yzljc.atribot.function.general;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.CDataNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.parser.Parser;
import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.chat.napcat.GroupInformation;
import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.chat.napcat.impl.MessageUtils;
import top.yzljc.atribot.chat.official.GroupChat;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.configuration.Properties;
import top.yzljc.atribot.function.general.impl.ImageDTO;
import top.yzljc.atribot.function.general.impl.PreImageGenerate;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigManager;
import top.yzljc.atribot.service.request.HttpService;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @Author YZ_Ljc_
 * @ClassName HypixelAnnouncements
 * @Created_at 2026/06/18
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.general
 */
@Slf4j
public class HypixelAnnouncements implements CommandExecutor {

    private static final String HYPIXEL_ANNOUNCEMENT_URL = "https://hypixel.net/forums/news-and-announcements.4/index.rss";

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final String HISTORY_FILE = Properties.HYPIXEL_ANNOUNCEMENTS;

    private static final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private static final DateTimeFormatter PUBLISH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private static final int INTRO_MAX_LENGTH = 100;

    private static final String HYPIXEL_HEADER_URL = "https://www.yzljc.top/img/hypixel-header.png";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender.getPlatform() != Platform.OFFICIAL_GROUP) return true;

        boolean result = feedAnnouncements();
        if (!result) {
            sender.sendMessage("暂无新的 Hypixel 公告");
            return true;
        }

        return true;
    }

    public static boolean feedAnnouncements() {
        List<Announcement> announcements = checkForNewAnnouncements();
        if (announcements.isEmpty()) return false;

        for (Announcement a : announcements) {

            ImageDTO banner = PreImageGenerate.dump(a.headerImage);

            Markdown md = TC.md(
                    Markdown.img(HYPIXEL_HEADER_URL, 24, 24) + "**Hypixel发布了新的公告**\n\n" +
                            "标题: `" + a.title() + "`\n\n" +
                            "作者: `" + a.author() + "`\n\n" +
                            "时间: `" + a.publishTime() + "`\n\n" +
                            (a.intro() != null && !a.intro().isBlank() ? ("简介: `" + a.intro()) + "`" : "") +
                            ((banner != null) ? "\n\n" + Markdown.img("banner", banner.url(), banner.width(), banner.height()) : "")
            );

            String text = "Hypixel 发布了新的公告！\n" +
                    "标题: " + a.title() + "\n" +
                    "作者: " + a.author() + "\n" +
                    "时间: " + a.publishTime() + "\n" +
                    "链接: " + a.link() + "\n" +
                    (a.intro() != null && !a.intro().isBlank() ? ("简介: " + a.intro()) + "\n" : "");

            for (String gid : OfficialGroups.enabledGroups("hyp_news")) {
                GroupChat.sendMessage(gid, md);
            }

            for (String gid : GroupInformation.fetchAllGroupIds()) {
                if (!GroupConfigManager.isFeatureEnabled(gid, "hyp_news")) continue;
                if (banner != null) {
                    GroupMessage.chatMessage(gid, text, banner.url(), MessageUtils.ImageType.URL);
                } else {
                    GroupMessage.chatMessage(gid, text);
                }
            }
        }
        return true;
    }

    public static List<Announcement> fetchAnnouncements() {
        List<Announcement> announcements = new ArrayList<>();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(HYPIXEL_ANNOUNCEMENT_URL))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/rss+xml, application/xml, text/xml;q=0.9, */*;q=0.8")
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = HttpService.httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();

            if (statusCode < 200 || statusCode >= 300) {
                log.warn("Failed to fetch Hypixel RSS feed, HTTP {}", statusCode);
                return announcements;
            }

            String rssXml = response.body();

            if (rssXml == null || rssXml.isBlank()) {
                log.warn("Failed to fetch Hypixel RSS feed, empty response");
                return announcements;
            }

            Document rssDoc = Jsoup.parse(rssXml, "", Parser.xmlParser());

            List<Element> items = rssDoc.select("rss > channel > item");

            if (items.isEmpty()) {
                items = rssDoc.select("channel > item");
            }

            if (items.isEmpty()) {
                log.warn("No RSS items found. Response preview: {}", preview(rssXml, 300));
                return announcements;
            }

            for (Element item : items) {
                Announcement announcement = parseAnnouncementItem(item);

                if (announcement == null) {
                    continue;
                }

                announcements.add(announcement);
            }

            log.info("Fetched {} announcements from Hypixel RSS", announcements.size());

        } catch (IOException e) {
            log.error("Failed to fetch Hypixel announcements", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Fetching Hypixel announcements was interrupted", e);
        } catch (Exception e) {
            log.error("Failed to parse Hypixel announcements", e);
        }

        return announcements;
    }

    private static Announcement parseAnnouncementItem(Element item) {
        String title = directChildText(item, "title");
        String link = directChildText(item, "link");
        String guid = directChildText(item, "guid");
        String pubDate = directChildText(item, "pubDate");

        if (title.isBlank() && link.isBlank()) {
            return null;
        }

        if (guid.isBlank()) {
            guid = !link.isBlank() ? link : title;
        }

        String author = extractAuthor(directChildText(item, "author"));

        if ("Unknown".equals(author)) {
            String creator = directChildText(item, "dc:creator");

            if (!creator.isBlank()) {
                author = creator;
            }
        }

        String publishTime = formatRssTime(pubDate);

        String contentEncoded = directChildText(item, "content:encoded");

        String headerImage = null;
        String intro = "";

        if (!contentEncoded.isBlank()) {
            ParsedContent parsedContent = parseContentEncoded(contentEncoded);
            headerImage = parsedContent.headerImage();
            intro = parsedContent.intro();
        }

        return new Announcement(title, author, publishTime, link, guid, headerImage, intro);
    }

    private static ParsedContent parseContentEncoded(String contentEncoded) {
        Document contentDoc = Jsoup.parse(contentEncoded);

        String headerImage = null;

        Element img = contentDoc.selectFirst("img[src]:not([src^=data:])");

        if (img != null) {
            headerImage = normalizeHypixelUrl(img.attr("src").trim());
        }

        String intro = contentDoc.text()
                .replace("\u200B", "")
                .replace("&ZeroWidthSpace;", "")
                .replaceAll("\\s+", " ")
                .trim();

        intro = removeReadMoreSuffix(intro);
        intro = limitLength(intro, INTRO_MAX_LENGTH);

        return new ParsedContent(headerImage, intro);
    }

    public static List<Announcement> checkForNewAnnouncements() {
        List<Announcement> latest = fetchAnnouncements();

        if (latest.isEmpty()) {
            return List.of();
        }

        Set<String> storedGuids = loadStoredGuids();

        if (storedGuids.isEmpty()) {
            saveGuids(latest);
            log.info("Initial announcement guids saved, {} entries", latest.size());

            return latest;
        }

        List<Announcement> newOnes = new ArrayList<>();

        for (Announcement announcement : latest) {
            if (!storedGuids.contains(announcement.guid())) {
                newOnes.add(announcement);
            }
        }

        if (!newOnes.isEmpty()) {
            for (Announcement announcement : newOnes) {
                storedGuids.add(announcement.guid());
            }

            saveGuids(storedGuids);

            log.info("{} new Hypixel announcement(s) detected and saved", newOnes.size());
        }

        return newOnes;
    }

    private static Set<String> loadStoredGuids() {
        File file = new File(HISTORY_FILE);

        if (!file.exists()) {
            return new HashSet<>();
        }

        if (!file.isFile()) {
            log.warn("History path exists but is not a file: {}", HISTORY_FILE);
            return new HashSet<>();
        }

        try {
            List<String> guidList = objectMapper.readValue(file, new TypeReference<>() {});

            return new HashSet<>(guidList);

        } catch (IOException e) {
            log.warn("Failed to load stored Hypixel announcement guids, will re-create", e);
            return new HashSet<>();
        }
    }

    private static void saveGuids(List<Announcement> announcements) {
        Set<String> guids = new HashSet<>();

        for (Announcement announcement : announcements) {
            if (announcement.guid() != null && !announcement.guid().isBlank()) {
                guids.add(announcement.guid());
            }
        }

        saveGuids(guids);
    }

    private static synchronized void saveGuids(Set<String> guids) {
        File file = new File(HISTORY_FILE);

        try {
            File parent = file.getParentFile();

            if (parent != null && !parent.exists()) {
                Files.createDirectories(parent.toPath());
            }

            List<String> sorted = new ArrayList<>(guids);
            sorted.sort(String::compareTo);

            File tempFile = new File(file.getAbsolutePath() + ".tmp");

            objectMapper.writeValue(tempFile, sorted);

            Files.move(tempFile.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );

        } catch (Exception atomicMoveException) {
            try {
                List<String> sorted = new ArrayList<>(guids);
                sorted.sort(String::compareTo);

                objectMapper.writeValue(file, sorted);
            } catch (IOException e) {
                log.error("Failed to save Hypixel announcement guids to {}", HISTORY_FILE, e);
            }
        }
    }

    private static String extractAuthor(String author) {
        if (author == null || author.isBlank()) {
            return "Unknown";
        }

        author = author.trim();

        if (author.contains("(") && author.contains(")")) {
            int start = author.indexOf('(') + 1;
            int end = author.lastIndexOf(')');

            if (start < end) {
                String extracted = author.substring(start, end).trim();

                if (!extracted.isBlank()) {
                    return extracted;
                }
            }
        }

        return author;
    }

    private static String formatRssTime(String pubDate) {
        if (pubDate == null || pubDate.isBlank()) {
            return "未知时间";
        }

        try {
            ZonedDateTime zdt = ZonedDateTime.parse(pubDate.trim(), DateTimeFormatter.RFC_1123_DATE_TIME);
            return PUBLISH_FORMATTER.format(zdt);
        } catch (Exception e) {
            log.debug("Failed to parse RSS pubDate: {}", pubDate, e);
            return pubDate;
        }
    }

    private static String directChildText(Element parent, String tagName) {
        if (parent == null || tagName == null || tagName.isBlank()) {
            return "";
        }

        for (Element child : parent.children()) {
            String childTagName = child.tagName();
            String childNodeName = child.nodeName();

            if (tagName.equals(childTagName) || tagName.equals(childNodeName)) {
                String cdata = extractCData(child);

                if (!cdata.isBlank()) {
                    return cdata.trim();
                }

                return child.text().trim();
            }
        }

        return "";
    }

    private static String extractCData(Element element) {
        StringBuilder sb = new StringBuilder();

        for (Node node : element.childNodes()) {
            if (node instanceof CDataNode cDataNode) {
                sb.append(cDataNode.getWholeText());
            }
        }

        return sb.toString();
    }

    private static String normalizeHypixelUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        url = url.trim();

        if (url.startsWith("data:")) {
            return null;
        }

        if (url.startsWith("//")) {
            return "https:" + url;
        }

        if (url.startsWith("/")) {
            return "https://hypixel.net" + url;
        }

        return url;
    }

    private static String removeReadMoreSuffix(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        text = text.trim();
        String suffix = "Read more";

        if (text.endsWith(suffix)) {
            text = text.substring(0, text.length() - suffix.length()).trim();
        }

        return text;
    }

    private static String limitLength(String text, int maxLength) {
        if (text == null || text.isBlank()) {
            return "";
        }

        if (text.length() <= maxLength) {
            return text;
        }

        return text.substring(0, maxLength).trim() + "...";
    }

    private static String preview(String text, int maxLength) {
        if (text == null) {
            return "";
        }

        text = text.replaceAll("\\s+", " ").trim();

        if (text.length() <= maxLength) {
            return text;
        }

        return text.substring(0, maxLength) + "...";
    }

    private record ParsedContent(String headerImage, String intro) {
    }

    /**
     * @param title       标题
     * @param author      作者
     * @param publishTime 发布时间
     * @param link        原始链接
     * @param guid        RSS 唯一标识
     * @param headerImage 头图
     * @param intro       简介
     */
    public record Announcement(
            String title,
            String author,
            String publishTime,
            String link,
            String guid,
            String headerImage,
            String intro
    ) {
    }
}