package top.yzljc.atribot.function.general;

import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.chat.official.C2CChat;
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
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.configuration.Properties;
import top.yzljc.atribot.function.impl.ImageDTO;
import top.yzljc.atribot.function.impl.PreImageGenerate;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigManager;
import top.yzljc.atribot.service.request.HttpService;
import top.yzljc.atribot.service.taskscheduler.DefaultTaskSchedule;
import top.yzljc.atribot.service.taskscheduler.ScheduleMode;
import top.yzljc.atribot.service.taskscheduler.ScheduledTask;
import top.yzljc.atribot.service.taskscheduler.TaskSchedule;

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
 * @ClassName HypixelAnnouncements
 * @Created_at 2026/06/18
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.general
 */
@Slf4j
public final class HypixelAnnouncements implements CommandExecutor, ScheduledTask {

    private static final String HYPIXEL_ANNOUNCEMENT_URL = "https://hypixel.net/forums/news-and-announcements.4/index.rss";
    private static final String HYPIXEL_SKYBLOCK_PATCH_NOTES_URL = "https://hypixel.net/forums/skyblock-patch-notes.158/index.rss";
    private record FeedConfig(String url, String label) {}
    private static final List<FeedConfig> FEEDS = List.of(
            new FeedConfig(HYPIXEL_ANNOUNCEMENT_URL, "Hypixel"),
            new FeedConfig(HYPIXEL_SKYBLOCK_PATCH_NOTES_URL, "Hypixel Skyblock")
    );
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String HISTORY_FILE = Properties.HYPIXEL_ANNOUNCEMENTS;
    private static final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final DateTimeFormatter PUBLISH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int INTRO_MAX_LENGTH = 100;
    private static final int MAX_PUSH_PER_RUN = 3;
    private static final Pattern GUID_NUMBER_PATTERN = Pattern.compile("(\\d+)");
    private static final String HYPIXEL_HEADER_URL = ResourcesProperties.HYPIXEL_HEADER_IMG;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof QQCommandSender qq)) return true;

        boolean result = feedAnnouncements();
        if (!result) {
            qq.sendMessage("暂无新的 Hypixel 公告");
            return true;
        }

        return true;
    }

    @Override
    public TaskSchedule schedule() {
        return new DefaultTaskSchedule().setMode(ScheduleMode.hourly);
    }

    @Override
    public void run() {
        feedAnnouncements();
    }

    public static boolean feedAnnouncements() {
        List<Announcement> announcements = checkForNewAnnouncements();
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

            String text = headerText + "！\n" +
                    "标题: " + a.title() + "\n" +
                    "作者: " + a.author() + "\n" +
                    "时间: " + a.publishTime() + "\n" +
                    "链接: " + a.link() + "\n" +
                    (a.intro() != null && !a.intro().isBlank() ? ("简介: " + a.intro()) + "\n" : "");

            var glist = OfficialGroups.enabledGroups("hyp_news");
            var ulist = OfficialUsers.enabledUsers("hyp_news");
            for (String gid : glist) {
                GroupChat.sendMessage(gid, md);
            }
            for (String uid : ulist) {
                C2CChat.sendMessage(uid, md);
            }

            Set<String> gids = GroupInformation.fetchAllGroupIds();
            for (String gid : gids) {
                if (!GroupConfigManager.isFeatureEnabled(gid, "hyp_news")) continue;
                if (banner != null) {
                    GroupMessage.chatMessage(gid, text, banner.url(), MessageUtils.ImageType.URL);
                } else {
                    GroupMessage.chatMessage(gid, text);
                }
            }
//            GroupMessage.chatMessage(Config.getInstance().getNapcatDebugGroupUin(), text.trim());
//            GroupChat.sendMessage(Config.getInstance().getDebugGroupOpenId(), md);
        }
        return true;
    }

    public static List<Announcement> fetchAnnouncements() {
        List<Announcement> announcements = new ArrayList<>();

        for (FeedConfig feed : FEEDS) {
            announcements.addAll(fetchAnnouncementsFromUrl(feed));
        }

        return announcements;
    }

    private static List<Announcement> fetchAnnouncementsFromUrl(FeedConfig feed) {
        String feedUrl = feed.url();
        String source = feed.label();
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

    /**
     * @param title       标题
     * @param author      作者
     * @param publishTime 发布时间
     * @param link        原始链接
     * @param guid        RSS 唯一标识
     * @param headerImage 头图
     * @param intro       简介
     * @param source      来源（如 Hypixel、Hypixel Skyblock）
     */
    public record Announcement(
            String title,
            String author,
            String publishTime,
            String link,
            String guid,
            String headerImage,
            String intro,
            String source
    ) {
    }
}