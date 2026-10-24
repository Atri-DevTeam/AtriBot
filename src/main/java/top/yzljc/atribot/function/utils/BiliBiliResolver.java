package top.yzljc.atribot.function.utils;

import top.yzljc.atricore.core.CoreConfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import top.yzljc.atricore.chat.napcat.GroupMessage;
import top.yzljc.atricore.chat.napcat.impl.MessageSegment;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atricore.event.EventHandler;
import top.yzljc.atricore.event.Listener;
import top.yzljc.atricore.event.events.NapcatGroupMessageEvent;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigManager;
import top.yzljc.atricore.service.runtime.ThreadManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.utils.FormatTools;

public class BiliBiliResolver implements Listener {

    private static final Logger log = LoggerFactory.getLogger(BiliBiliResolver.class);
    private static final ObjectMapper jsonMapper = new ObjectMapper();
    private static final long MAX_VIDEO_DURATION_SECONDS = 600L;

    private static final String SESSDATA = Config.getInstance().getBilibiliCookie();

    private static String LAST_BVID = "";
    private static String LAST_GROUP = "";
    private static final Pattern PATTERN_BV = Pattern.compile("BV[a-zA-Z0-9]{10}");
    private static final Pattern PATTERN_B23 = Pattern.compile("b23\\.tv[\\\\/]+([a-zA-Z0-9]+)");

    @EventHandler
    public void onGroupMessage(NapcatGroupMessageEvent event) {
        if (!GroupConfigManager.isFeatureEnabled(event.getGroupId(), "bv_check")) return;

        String rawMessage = event.getMessage().getContent();
        String groupId = event.getGroupId();

        String bvid = findBvid(rawMessage, groupId);

        if (bvid != null) {
            log.info("检测到 B站视频，群: {}，视频: {}", groupId, bvid);
            if (bvid.equals(LAST_BVID) && groupId.equals(LAST_GROUP)) {
                log.info("跳过重复视频，群: {}，视频: {}", groupId, bvid);
                return;
            }
            fetchVideoDetail(groupId, bvid);
            LAST_BVID = bvid;
            LAST_GROUP = groupId;
        }
    }

    private static String findBvid(String content, String groupId) {
        Matcher bvMatcher = PATTERN_BV.matcher(content);
        if (bvMatcher.find()) {
            return bvMatcher.group();
        }

        Matcher b23Matcher = PATTERN_B23.matcher(content);
        if (b23Matcher.find()) {
            String shortKey = b23Matcher.group(1);
            String shortUrl = "https://b23.tv/" + shortKey;
            String realUrl = resolveShortUrl(shortUrl, groupId);
            if (realUrl != null) {
                Matcher resolvedMatcher = PATTERN_BV.matcher(realUrl);
                if (resolvedMatcher.find()) {
                    return resolvedMatcher.group();
                }
                log.warn("短链接目标未包含 BV 号，群: {}，短链接: {}", groupId, shortUrl);
            }
        }
        return null;
    }

    private static String resolveShortUrl(String shortUrl, String groupId) {
        long started = System.nanoTime();
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
            log.warn("B站短链接未返回跳转地址，群: {}，HTTP: {}，耗时: {} ms",
                    groupId, conn.getResponseCode(), elapsedMillis(started));
        } catch (Exception e) {
            log.warn("B站短链接解析失败，群: {}，异常类型: {}，耗时: {} ms",
                    groupId, e.getClass().getSimpleName(), elapsedMillis(started));
        }
        return null;
    }

    private static void fetchVideoDetail(String groupId, String bvid) {
        long submitted = System.nanoTime();
        try {
            ThreadManager.execute(() -> {
                long started = System.nanoTime();
                boolean sent = false;
                try {
                    String viewUrl = "https://api.bilibili.com/x/web-interface/view?bvid=" + bvid;
                    JsonNode root = sendBilibiliRequest(viewUrl, "视频详情", groupId, bvid);

                    if (root == null || root.path("code").asInt() != 0) {
                        log.warn("终止视频解析，群: {}，视频: {}，原因: 视频详情获取失败", groupId, bvid);
                        return;
                    }

                    JsonNode data = root.path("data");
                    JsonNode stat = data.path("stat");

                    String title = data.path("title").asText();
                    String picUrl = normalizeHttps(data.path("pic").asText());
                    String desc = data.path("desc").asText();
                    if (desc == null || desc.isEmpty()) desc = "（该视频暂无简介）";

                    long cid = data.path("cid").asLong();
                    long duration = data.path("duration").asLong();
                    boolean tooLong = duration > MAX_VIDEO_DURATION_SECONDS;

                    long mid = data.path("owner").path("mid").asLong();
                    String upStats = fetchUploaderStats(mid, groupId, bvid);
                    String videoUrl = tooLong ? null : fetchLowestQualityVideoUrl(bvid, cid, groupId);

                    String time = FormatTools.formatTimestamp(data.path("pubdate").asText());
                    String link = "https://www.bilibili.com/video/" + bvid;

                    String sb = "视频标题：" + title + "\n" +
                            "观看次数：" + formatNum(stat.path("view").asLong()) + "\n" +
                            "点赞次数：" + formatNum(stat.path("like").asLong()) + "\n" +
                            "投币次数：" + formatNum(stat.path("coin").asLong()) + "\n" +
                            "收藏次数：" + formatNum(stat.path("favorite").asLong()) + "\n" +
                            "弹幕量：" + formatNum(stat.path("danmaku").asLong()) + "\n" +
                            "视频时长：" + formatDuration(duration) + "\n" +
                            "发布时间：" + time + "\n" +
                            "原始链接：" + link;

                    List<MessageSegment> nodes = new ArrayList<>();
                    if (picUrl != null && !picUrl.isBlank()) {
                        nodes.add(createImageNode(picUrl));
                    }
                    nodes.add(createTextNode(sb));
                    nodes.add(createTextNode("视频简介：\n" + desc));
                    if (tooLong) {
                        nodes.add(createTextNode("视频时长超出管理员设置的最大时长限制: " + (MAX_VIDEO_DURATION_SECONDS / 60) + " 分钟"));
                    } else if (videoUrl != null && !videoUrl.isBlank()) {
                        nodes.add(createVideoNode(videoUrl));
                    } else {
                        nodes.add(createTextNode("原视频：获取失败"));
                    }
                    nodes.add(createTextNode(upStats));

                    sent = sendForwardMessage(groupId, bvid, nodes, title);

                } catch (Exception e) {
                    log.error("视频解析异常，群: {}，视频: {}", groupId, bvid, e);
                } finally {
                    log.info("视频解析结束，群: {}，视频: {}，结果: {}，处理耗时: {} ms，总耗时: {} ms",
                            groupId, bvid, sent ? "已发送" : "未发送", elapsedMillis(started), elapsedMillis(submitted));
                }
            });
        } catch (RejectedExecutionException e) {
            log.warn("视频解析任务被拒绝，群: {}，视频: {}，工作队列已满或已关闭", groupId, bvid);
            throw e;
        }
    }

    private static String fetchUploaderStats(long mid, String groupId, String bvid) {
        try {
            String cardUrl = "https://api.bilibili.com/x/web-interface/card?mid=" + mid + "&photo=true";
            JsonNode cardRoot = sendBilibiliRequest(cardUrl, "UP主资料", groupId, bvid);

            if (cardRoot == null || cardRoot.path("code").asLong() != 0L) {
                log.warn("UP主资料不可用，群: {}，视频: {}，UP主: {}，继续发送视频信息", groupId, bvid, mid);
                return "👤 UP主信息获取失败";
            }

            JsonNode data = cardRoot.path("data");
            JsonNode card = data.path("card");

            String name = card.path("name").asText();
            String sign = card.path("sign").asText();
            long level = card.path("level_info").path("current_level").asLong();

            long fans = data.path("follower").asLong(); // 粉丝数
            long totalLikes = data.path("like_num").asLong(); // 总获赞数

            long totalViews = 0;
            try {
                String statUrl = "https://api.bilibili.com/x/space/upstat?mid=" + mid;
                JsonNode statRoot = sendBilibiliRequest(statUrl, "UP主播放统计", groupId, bvid);

                if (statRoot != null && statRoot.path("code").asLong() == 0L) {
                    totalViews = statRoot.path("data").path("archive").path("view").asLong();
                    if (totalViews <= 0) {
                        GroupMessage.atUser("3199590352", CoreConfig.getInstance().getSettings().getNapcatDebugGroupUin(), "登陆状态已过期，无法获取播放量，请及时处理！");
                        log.warn("UP主播放统计为空，群: {}，视频: {}，UP主: {}，已尝试发送登录状态提醒", groupId, bvid, mid);
                    }
                } else {
                    log.warn("UP主播放统计不可用，群: {}，视频: {}，UP主: {}，继续发送视频信息", groupId, bvid, mid);
                }
            } catch (Exception e) {
                log.warn("UP主播放统计处理异常，群: {}，视频: {}，UP主: {}", groupId, bvid, mid, e);
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
            log.warn("UP主资料处理异常，群: {}，视频: {}，UP主: {}", groupId, bvid, mid, e);
        }
        return "UP主信息获取失败";
    }

    private static MessageSegment createTextNode(String text) {
        return GroupMessage.createTextNode(text);
    }

    private static MessageSegment createImageNode(String url) {
        return GroupMessage.createImageNode(url);
    }

    private static MessageSegment createVideoNode(String url) {
        return GroupMessage.createVideoNode(url);
    }

    private static String normalizeHttps(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        String trimmed = url.trim();
        if (trimmed.startsWith("http://")) {
            return "https://" + trimmed.substring("http://".length());
        }
        return trimmed;
    }

    private static boolean sendForwardMessage(String groupId, String bvid, List<MessageSegment> nodes, String title) {
        try {
            String messageId = GroupMessage.forwardMessage(groupId, nodes, "B站视频解析结果", "查看哔哩哔哩视频信息", title);
            return messageId != null && !messageId.isBlank();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String fetchLowestQualityVideoUrl(String bvid, long cid, String groupId) {
        try {
            String playUrl = "https://api.bilibili.com/x/player/playurl?bvid=" + bvid +
                    "&cid=" + cid +
                    "&qn=16&fnval=16&fnver=0&fourk=0&platform=html5";
            JsonNode root = sendBilibiliRequest(playUrl, "视频播放地址", groupId, bvid);

            if (root == null || root.path("code").asInt() != 0) {
                log.warn("视频播放地址不可用，群: {}，视频: {}，继续发送图文信息", groupId, bvid);
                return null;
            }

            JsonNode data = root.path("data");
            JsonNode durl = data.path("durl");
            if (durl.isArray() && !durl.isEmpty()) {
                String url = durl.get(0).path("url").asText();
                if (url != null && !url.isEmpty()) {
                    return url;
                }
            }

            JsonNode dashVideo = data.path("dash").path("video");
            if (dashVideo.isArray() && !dashVideo.isEmpty()) {
                String url = selectDashVideoUrl(dashVideo);
                if (url != null && !url.isEmpty()) {
                    return url;
                }
            }
            log.warn("播放接口未返回可用视频流，群: {}，视频: {}，继续发送图文信息", groupId, bvid);
        } catch (Exception e) {
            log.warn("视频播放地址处理异常，群: {}，视频: {}", groupId, bvid, e);
        }
        return null;
    }

    private static String selectDashVideoUrl(JsonNode dashVideo) {
        JsonNode fallback = null;
        for (JsonNode video : dashVideo) {
            if (fallback == null) {
                fallback = video;
            }
            String codecs = video.path("codecs").asText("");
            if (codecs.startsWith("avc1")) {
                return firstText(video, "baseUrl", "base_url");
            }
        }
        return fallback == null ? null : firstText(fallback, "baseUrl", "base_url");
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = node.path(field).asText(null);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private static JsonNode sendBilibiliRequest(String urlStr, String stage, String groupId, String bvid) {
        long started = System.nanoTime();
        int status = 0;
        try {
            URL url = new URI(urlStr).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            if ("/x/web-interface/card".equals(url.getPath()) || "/x/space/upstat".equals(url.getPath())) {
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                conn.setRequestProperty("Referer", "https://www.bilibili.com/");
            }

            if (SESSDATA != null && !SESSDATA.isEmpty()) {
                conn.setRequestProperty("Cookie", "SESSDATA=" + SESSDATA);
            }

            status = conn.getResponseCode();
            if (status == 200) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);
                    JsonNode root = jsonMapper.readTree(response.toString());
                    if (root == null) {
                        log.warn("B站接口返回空内容，阶段: {}，群: {}，视频: {}，HTTP: {}，耗时: {} ms",
                                stage, groupId, bvid, status, elapsedMillis(started));
                    } else if (root.path("code").asLong() != 0) {
                        log.warn("B站接口返回错误，阶段: {}，群: {}，视频: {}，HTTP: {}，业务码: {}，原因: {}，耗时: {} ms",
                                stage, groupId, bvid, status, root.path("code").asText(),
                                logMessage(root.path("message").asText(root.path("msg").asText(""))), elapsedMillis(started));
                    }
                    return root;
                }
            }
            log.warn("B站接口 HTTP 请求失败，阶段: {}，群: {}，视频: {}，HTTP: {}，耗时: {} ms",
                    stage, groupId, bvid, status, elapsedMillis(started));
            if (status == 412) {
                try (var errorStream = conn.getErrorStream()) {
                    String body = "（空响应）";
                    if (errorStream != null) {
                        byte[] bytes = errorStream.readNBytes(16 * 1024 + 1);
                        body = new String(bytes, 0, Math.min(bytes.length, 16 * 1024), StandardCharsets.UTF_8)
                                .replaceAll("[\\p{Cntrl}]", " ");
                        if (bytes.length > 16 * 1024) body += " [响应正文已截断]";
                    }
                    log.warn("B站接口 HTTP 412 响应正文，阶段: {}，群: {}，视频: {}，内容: {}",
                            stage, groupId, bvid, body);
                }
            }
        } catch (Exception e) {
            log.warn("B站接口请求异常，阶段: {}，群: {}，视频: {}，HTTP: {}，异常类型: {}，耗时: {} ms",
                    stage, groupId, bvid, status, e.getClass().getSimpleName(), elapsedMillis(started));
        }
        return null;
    }

    private static long elapsedMillis(long started) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
    }

    private static String logMessage(String message) {
        String text = message.replaceAll("[\\p{Cntrl}]", " ")
                .replaceAll("https?://\\S+", "[链接]")
                .replaceAll("(?i)SESSDATA\\s*[:=]\\s*[^\\s;,]+", "SESSDATA=[隐藏]");
        return text.substring(0, Math.min(text.length(), 256));
    }

    private static String formatNum(long num) {
        if (num >= 10000) return String.format("%.1f万", num / 10000.0);
        return String.valueOf(num);
    }

    private static String formatDuration(long seconds) {
        long min = seconds / 60;
        long sec = seconds % 60;
        return min + "分" + sec + "秒";
    }
}
