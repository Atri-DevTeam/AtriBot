package top.yzljc.qqbot.feature;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import top.yzljc.qqbot.botkits.request.PostRequest;
import top.yzljc.qqbot.botkits.request.RequestType;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CheckBilibili {

    private static final Logger log = LoggerFactory.getLogger(CheckBilibili.class);
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    static Settings settings = Config.getInstance();
    private static final String SESSDATA = settings.getBilibiliCookie();

    private static final String FAKE_UIN = "3614865692";
    private static final String FAKE_NAME = "YZ_Ljc_";

    private static final Pattern PATTERN_BV = Pattern.compile("BV[a-zA-Z0-9]{10}");
    private static final Pattern PATTERN_B23 = Pattern.compile("b23\\.tv[\\\\/]+([a-zA-Z0-9]+)");

    public static void process(JsonNode json) {
        String messageType = json.path("message_type").asText();
        if (!"group".equals(messageType)) return;

        String rawMessage = json.path("raw_message").asText();
        long groupId = json.path("group_id").asLong();

        String bvid = findBvid(rawMessage);

        if (bvid != null) {
            log.info("Detected Bilibili Video: {}", bvid);
            fetchVideoDetail(groupId, bvid);
        }
    }

    private static String findBvid(String content) {
        Matcher bvMatcher = PATTERN_BV.matcher(content);
        if (bvMatcher.find()) {
            return bvMatcher.group();
        }

        Matcher b23Matcher = PATTERN_B23.matcher(content);
        if (b23Matcher.find()) {
            String shortKey = b23Matcher.group(1);
            String shortUrl = "https://b23.tv/" + shortKey;
            log.info("Found short URL: {}, resolving...", shortUrl);
            String realUrl = resolveShortUrl(shortUrl);
            if (realUrl != null) {
                Matcher resolvedMatcher = PATTERN_BV.matcher(realUrl);
                if (resolvedMatcher.find()) {
                    return resolvedMatcher.group();
                }
            }
        }
        return null;
    }

    private static String resolveShortUrl(String shortUrl) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URI(shortUrl).toURL().openConnection();
            conn.setInstanceFollowRedirects(false);
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.connect();

            String location = conn.getHeaderField("Location");
            if (location != null) {
                return location;
            }
        } catch (Exception e) {
            log.warn("Failed to resolve short URL: {}", e.getMessage());
        }
        return null;
    }

    private static void fetchVideoDetail(long groupId, String bvid) {
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                String viewUrl = "https://api.bilibili.com/x/web-interface/view?bvid=" + bvid;
                JsonNode root = sendBilibiliRequest(viewUrl);

                if (root == null || root.path("code").asInt() != 0) {
                    return;
                }

                JsonNode data = root.path("data");
                JsonNode stat = data.path("stat");

                String title = data.path("title").asText();
                String picUrl = data.path("pic").asText();
                String desc = data.path("desc").asText();
                if (desc == null || desc.isEmpty()) desc = "（该视频暂无简介）";

                long mid = data.path("owner").path("mid").asLong();
                String upStats = fetchUploaderStats(mid);

                int duration = data.path("duration").asInt();
                String link = "https://www.bilibili.com/video/" + bvid;

                String sb = "视频标题：" + title + "\n" +
                        "观看次数：" + formatNum(stat.path("view").asInt()) + "\n" +
                        "点赞次数：" + stat.path("like").asInt() + "\n" +
                        "投币次数：" + stat.path("coin").asInt() + "\n" +
                        "收藏次数：" + stat.path("favorite").asInt() + "\n" +
                        "弹幕量：" + stat.path("danmaku").asInt() + "\n" +
                        "视频时长：" + formatDuration(duration) + "\n" +
                        "原始链接：" + link;

                List<Map<String, Object>> nodes = new ArrayList<>();
                nodes.add(createImageNode(picUrl));
                nodes.add(createTextNode(sb));
                nodes.add(createTextNode("视频简介：\n" + desc));
                nodes.add(createTextNode(upStats));

                sendForwardMessage(groupId, nodes, title);

            } catch (Exception e) {
                log.warn("[Bili API] Processing Error: {}", e.getMessage());
            }
        });
    }

    private static String fetchUploaderStats(long mid) {
        try {
            String cardUrl = "https://api.bilibili.com/x/web-interface/card?mid=" + mid + "&photo=true";
            JsonNode cardRoot = sendBilibiliRequest(cardUrl);

            if (cardRoot == null || cardRoot.path("code").asInt() != 0) {
                return "👤 UP主信息获取失败";
            }

            JsonNode data = cardRoot.path("data");
            JsonNode card = data.path("card");

            String name = card.path("name").asText();
            String sign = card.path("sign").asText();
            int level = card.path("level_info").path("current_level").asInt();

            int fans = data.path("follower").asInt(); // 粉丝数
            int totalLikes = data.path("like_num").asInt(); // 总获赞数

            long totalViews = 0;
            try {
                String statUrl = "https://api.bilibili.com/x/space/upstat?mid=" + mid;
                JsonNode statRoot = sendBilibiliRequest(statUrl);

                if (statRoot != null && statRoot.path("code").asInt() == 0) {
                    totalViews = statRoot.path("data").path("archive").path("view").asLong();
                } else {
                    log.warn("[Bili Up Stat] Failed to get view count: API returned error/no-auth");
                }
            } catch (Exception e) {
                log.warn("[Bili Up Stat] Failed to get view count: {}", e.getMessage());
            }

            if (sign == null || sign.trim().isEmpty()) {
                sign = "（暂无签名）";
            } else {
                sign = sign.replace("\n", " ");
            }

            String viewData = String.valueOf(totalViews);
            return "UP主：" + name + " (Lv" + level + ")\n" +
                    "UID: " + mid + "\n" +
                    "粉丝：" + fans + " | 获赞：" + totalLikes + "\n" +
                    "总播放：" + viewData + "\n" +
                    "签名：" + sign;

        } catch (Exception e) {
            log.warn("[Bili Up Info] Error: {}", e.getMessage());
        }
        return "UP主信息获取失败";
    }

    private static Map<String, Object> createTextNode(String text) {
        Map<String, Object> node = new HashMap<>();
        node.put("type", "node");
        Map<String, Object> data = new HashMap<>();
        data.put("uin", FAKE_UIN);
        data.put("name", FAKE_NAME);

        List<Map<String, Object>> contentList = new ArrayList<>();
        Map<String, Object> textItem = new HashMap<>();
        textItem.put("type", "text");
        Map<String, Object> textData = new HashMap<>();
        textData.put("text", text);
        textItem.put("data", textData);
        contentList.add(textItem);

        data.put("content", contentList);
        node.put("data", data);
        return node;
    }

    private static Map<String, Object> createImageNode(String url) {
        Map<String, Object> node = new HashMap<>();
        node.put("type", "node");
        Map<String, Object> data = new HashMap<>();
        data.put("uin", FAKE_UIN);
        data.put("name", FAKE_NAME);

        List<Map<String, Object>> contentList = new ArrayList<>();
        Map<String, Object> imgItem = new HashMap<>();
        imgItem.put("type", "image");
        Map<String, Object> imgData = new HashMap<>();
        imgData.put("file", url);
        imgItem.put("data", imgData);
        contentList.add(imgItem);

        data.put("content", contentList);
        node.put("data", data);
        return node;
    }

    private static void sendForwardMessage(long groupId, List<Map<String, Object>> nodes, String title) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("group_id", groupId);
            payload.put("messages", nodes);

            List<Map<String, String>> news = new ArrayList<>();
            Map<String, String> newsItem = new HashMap<>();
            newsItem.put("text", title);
            news.add(newsItem);
            payload.put("news", news);

            payload.put("source", "B站视频解析结果");
            payload.put("summary", "查看哔哩哔哩视频信息");

            PostRequest.sendPost(RequestType.SEND_FORWARD_MSG, payload);

            log.info("Result request sending task was successfully transformed to PostRequest {}", groupId);

        } catch (Exception e) {
            log.error("Failed to send forward message", e);
        }
    }

    private static JsonNode sendBilibiliRequest(String urlStr) {
        try {
            URL url = new URI(urlStr).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            conn.setRequestProperty("Referer", "https://www.bilibili.com/");

            if (SESSDATA != null && !SESSDATA.isEmpty()) {
                conn.setRequestProperty("Cookie", "SESSDATA=" + SESSDATA);
            } else {
                log.debug("SESSDATA is empty, sending request without cookie: {}", urlStr);
            }

            if (conn.getResponseCode() == 200) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);
                    return jsonMapper.readTree(response.toString());
                }
            }
        } catch (Exception e) {
            log.warn("[Bili API] Request Failed: {}", e.getMessage());
        }
        return null;
    }

    private static String formatNum(int num) {
        if (num >= 10000) return String.format("%.1f万", num / 10000.0);
        return String.valueOf(num);
    }

    private static String formatDuration(int seconds) {
        int min = seconds / 60;
        int sec = seconds % 60;
        return min + "分" + sec + "秒";
    }
}