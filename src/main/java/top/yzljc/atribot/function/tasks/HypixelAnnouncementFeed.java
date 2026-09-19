package top.yzljc.atribot.function.tasks;

import top.yzljc.atribot.configuration.ResourcesProperties;
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
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.function.impl.ImageDTO;
import top.yzljc.atribot.function.impl.PreImageGenerate;
import top.yzljc.atribot.function.tasks.pushtask.PushTask;
import top.yzljc.atribot.service.request.HttpService;
import top.yzljc.sakuraba_ema.guild.ChannelPosts;
import top.yzljc.sakuraba_ema.utils.ForumCode;

import top.yzljc.atribot.function.tasks.HypixelAnnouncements.Announcement;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Author YZ_Ljc_
 * @ClassName HypixelAnnouncementFeed
 * @Created_at 2026/09/18
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.tasks
 */
@Slf4j
public final class HypixelAnnouncementFeed {

    private final String feedUrl;
    private final String source;
    private final String historyFile;
    private static final Object HISTORY_LOCK = new Object();

    public HypixelAnnouncementFeed(String feedUrl, String source, String historyFile) {
        this.feedUrl = feedUrl;
        this.source = source;
        this.historyFile = historyFile;
    }

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final DateTimeFormatter PUBLISH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int INTRO_MAX_LENGTH = 100;
    private static final int MAX_PUSH_PER_RUN = 3;
    private static final Pattern GUID_NUMBER_PATTERN = Pattern.compile("(\\d+)");
    private static final String HYPIXEL_HEADER_URL = ResourcesProperties.HYPIXEL_HEADER_IMG;

    static boolean pushAnnouncements(List<Announcement> announcements, String functionId) {
        if (announcements.isEmpty()) return false;

        int pushed = 0;
        for (Announcement a : announcements) {
            if (pushed >= MAX_PUSH_PER_RUN) break;
            pushed++;

            ImageDTO banner = a.headerImage() != null ? PreImageGenerate.dump(a.headerImage()) : null;

            String headerText = a.source() + " 发布了新的公告";

            Markdown md = TC.md(
                    Markdown.img(HYPIXEL_HEADER_URL, 24, 24) + "**" + headerText + "**\n\n" +
                            "标题: " + a.title() + "\n\n" +
                            "作者: " + a.author() + "\n\n" +
                            "时间: " + a.publishTime() + "\n\n" +
                            (a.intro() != null && !a.intro().isBlank() ? ("简介: " + a.intro()) : "") +
                            ((banner != null) ? "\n\n" + Markdown.img("banner", banner.url(), banner.width(), banner.height()) : "")
            );

            PushTask.push(functionId, md);

            Markdown forumsMarkdown = TC.md(
                    "**" + headerText + "**\n\n" +
                            "作者: " + a.author() + "\n\n" +
                            "时间: " + a.publishTime() + "\n\n" +
                            "位置: " + Markdown.link(a.link(), "查看原帖") + "\n\n" +
                            (a.intro() != null && !a.intro().isBlank() ? ("简介: " + a.intro()) : "") +
                            ((banner != null) ? "\n\n" + Markdown.img("banner", banner.url(), banner.width(), banner.height()) : "")
            );

            ChannelPosts.sendMessage(ForumCode.GUILD_ID, ForumCode.HYPIXEL_NEWS.getChannelId(), a.title(), forumsMarkdown);
        }
        return true;
    }

    private List<Announcement> fetchAnnouncements() {
        List<Announcement> announcements = new ArrayList<>();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(feedUrl))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/rss+xml, application/xml, text/xml;q=0.9, */*;q=0.8")
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = HttpService.httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();

            if (statusCode < 200 || statusCode >= 300) {
                log.warn("Failed to fetch Hypixel RSS feed from {}, HTTP {}", feedUrl, statusCode);
                return announcements;
            }

            String rssXml = response.body();

            if (rssXml == null || rssXml.isBlank()) {
                log.warn("Failed to fetch Hypixel RSS feed from {}, empty response", feedUrl);
                return announcements;
            }

            Document rssDoc = Jsoup.parse(rssXml, "", Parser.xmlParser());

            List<Element> items = rssDoc.select("rss > channel > item");

            if (items.isEmpty()) {
                items = rssDoc.select("channel > item");
            }

            if (items.isEmpty()) {
                log.warn("No RSS items found from {}. Response preview: {}", feedUrl, preview(rssXml, 300));
                return announcements;
            }

            for (Element item : items) {
                Announcement announcement = parseAnnouncementItem(item, source);

                if (announcement == null) {
                    continue;
                }

                announcements.add(announcement);
            }

            log.info("Fetched {} announcements from {}", announcements.size(), feedUrl);

        } catch (IOException e) {
            log.error("Failed to fetch Hypixel announcements from {}", feedUrl, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Fetching Hypixel announcements from {} was interrupted", feedUrl, e);
        } catch (Exception e) {
            log.error("Failed to parse Hypixel announcements from {}", feedUrl, e);
        }

        return announcements;
    }

    private static Announcement parseAnnouncementItem(Element item, String source) {
        String title = directChildText(item, "title");
        String link = directChildText(item, "link");
        String guid = directChildText(item, "guid");
        String pubDate = directChildText(item, "pubDate");

        if (title.isBlank() && link.isBlank()) {
            return null;
        }

        guid = normalizeGuid(guid);
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

        return new Announcement(title, author, publishTime, link, guid, headerImage, intro, source);
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

    List<Announcement> checkForNewAnnouncements() {
        List<Announcement> latest = fetchAnnouncements();

        if (latest.isEmpty()) {
            return List.of();
        }

        // 两个任务共用历史文件，读取、合并、写回必须一起加锁，避免覆盖对方记录。
        synchronized (HISTORY_LOCK) {
            return recordNewAnnouncements(latest);
        }
    }

    private List<Announcement> recordNewAnnouncements(List<Announcement> latest) {
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

    private Set<String> loadStoredGuids() {
        File file = new File(historyFile);

        if (!file.exists()) {
            return new HashSet<>();
        }

        if (!file.isFile()) {
            log.warn("History path exists but is not a file: {}", file);
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

    private void saveGuids(List<Announcement> announcements) {
        Set<String> guids = new HashSet<>();

        for (Announcement announcement : announcements) {
            if (announcement.guid() != null && !announcement.guid().isBlank()) {
                guids.add(announcement.guid());
            }
        }

        saveGuids(guids);
    }

    private void saveGuids(Set<String> guids) {
        File file = new File(historyFile);

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
                log.error("Failed to save Hypixel announcement guids to {}", historyFile, e);
            }
        }
    }

    private static String normalizeGuid(String guid) {
        if (guid == null || guid.isBlank()) {
            return "";
        }

        Matcher matcher = GUID_NUMBER_PATTERN.matcher(guid.trim());
        if (matcher.find()) {
            return matcher.group(1);
        }

        return guid.trim();
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

}
