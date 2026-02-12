package top.yzljc.qqbot.utils.deprecated;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import top.yzljc.qqbot.botkits.message.MessageSender;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Deprecated(since = "客户自研，此项目废弃不再提供任何修复" , forRemoval = true)
public class ServerStatusReport {

    private static final Logger log = LoggerFactory.getLogger(ServerStatusReport.class);

    private static final List<Long> ALLOWED_GROUPS = Arrays.asList(
            883993372L,
            978885201L,
            626462367L,
            1039954708L
    );

    private static final String API_URL = "https://api.mcstatus.io/v2/status/java/GordonHim.com";
    private static final String TRIGGER_CMD = "在线人数";
    private static final String BG_NORMAL = "gh_background.jpg"; // 普通背景
    private static final String BG_ONLINE = "gh_online.jpg";     // 开服通知图
    private static final String BG_OFFLINE = "gh_offline.jpg";   // 关服通知图
    private static final File DATA_FILE = new File("status_data.json");
    private static final ObjectMapper jsonMapper = new ObjectMapper();
    private static StatusData currentData = new StatusData();

    public static void init() {
        loadData();

        // scheduler.scheduleAtFixedRate(ServerStatusReport::executeScheduledCheck, 0, 15, TimeUnit.MINUTES);
        log.info("监控任务已启动，每15分钟检查一次，数据存储于：{}", DATA_FILE.getAbsolutePath());
    }

    public static void process(JsonNode json) {
        if (!"message".equals(json.path("post_type").asText())) return;
        if (!"group".equals(json.path("message_type").asText())) return;

        long groupId = json.path("group_id").asLong();
        String rawMsg = json.path("raw_message").asText().trim();

        if (!ALLOWED_GROUPS.contains(groupId)) return;

        if (TRIGGER_CMD.equals(rawMsg)) {
            // ThreadManager.execute(() -> handleManualQuery(groupId));
        }
    }

    private static void handleManualQuery(long groupId) {
        try {
            ServerStatus status = fetchStatus();

            if (status.online) {
                File imgFile = generateStatusImage(GenerateType.ONLINE_COUNT, status.onlinePlayers, BG_NORMAL);
                sendImage(groupId, imgFile);
                if (imgFile != null) imgFile.delete();
            } else {
                String durationStr = "未知时长";
                if (currentData.offlineStartTime > 0) {
                    long diff = System.currentTimeMillis() - currentData.offlineStartTime;
                    long days = TimeUnit.MILLISECONDS.toDays(diff);
                    long hours = TimeUnit.MILLISECONDS.toHours(diff) % 24;
                    long minutes = TimeUnit.MILLISECONDS.toMinutes(diff) % 60;

                    if (days > 0) {
                        durationStr = days + "天 " + hours + "小时 " + minutes + "分";
                    } else if (hours > 0) {
                        durationStr = hours + "小时 " + minutes + "分";
                    } else {
                        durationStr = minutes + "分钟";
                    }
                } else {
                    durationStr = "刚刚";
                }

                File imgFile = generateOfflineDurationImage(durationStr, BG_NORMAL);
                sendImage(groupId, imgFile);
                if (imgFile != null) imgFile.delete();
            }
        } catch (Exception e) {
            e.printStackTrace();
            MessageSender.sendGroupMessage(groupId, "查询失败: " + e.getMessage());
        }
    }

    private static void executeScheduledCheck() {
        try {
            ServerStatus status = fetchStatus();
            long now = System.currentTimeMillis();
            boolean dataChanged = false;

            if (currentData.lastKnownState == null) {
                currentData.lastKnownState = status.online;
                if (!status.online && currentData.offlineStartTime == 0) {
                    currentData.offlineStartTime = now;
                }
                saveData();
                return;
            }

            boolean isOnlineNow = status.online;
            boolean wasOnline = currentData.lastKnownState;

            if (wasOnline && !isOnlineNow) {
                log.info("检测到服务器离线，全员推送……");

                currentData.offlineStartTime = now;
                dataChanged = true;

                broadcastImage(BG_OFFLINE);
            }
            else if (!wasOnline && isOnlineNow) {
                log.info("检测到服务器开服，全员推送……");

                currentData.offlineStartTime = 0L;
                dataChanged = true;

                broadcastImage(BG_ONLINE);
            }
            if (isOnlineNow) {
                if (status.onlinePlayers >= 25) {
                    if (now - currentData.lastBroadcastTime > 7200 * 1000) {
                        log.info("人数达标（{}），触发全员播报", status.onlinePlayers);
                        File imgFile = generateStatusImage(GenerateType.ONLINE_COUNT, status.onlinePlayers, BG_NORMAL);

                        // 循环发送
                        for (Long gid : ALLOWED_GROUPS) {
                            sendImage(gid, imgFile);
                        }

                        // 发送完再统一删除
                        if (imgFile != null && imgFile.exists()) {
                            imgFile.delete();
                        }

                        currentData.lastBroadcastTime = now;
                        dataChanged = true;
                    }
                }
            }

            if (currentData.lastKnownState != isOnlineNow) {
                currentData.lastKnownState = isOnlineNow;
                dataChanged = true;
            }

            if (dataChanged) {
                saveData();
            }

        } catch (Exception e) {
            log.error("定时检测异常：{}", e.getMessage());
        }
    }

    private static void loadData() {
        try {
            if (DATA_FILE.exists()) {
                currentData = jsonMapper.readValue(DATA_FILE, StatusData.class);
            } else {
                currentData = new StatusData();
            }
        } catch (Exception e) {
            log.error("读取数据文件失败：{}", e.getMessage());
            log.error("数据文件可能损坏或版本不兼容，已重置状态数据");
            currentData = new StatusData(); // 失败时重置，防止报错卡死
            saveData(); // 覆盖坏文件
        }
    }

    private static void saveData() {
        try {
            jsonMapper.writeValue(DATA_FILE, currentData);
        } catch (Exception e) {
            log.error("保存数据文件失败：{}", e.getMessage());
        }
    }

    private static void broadcastImage(String bgFileName) {
        try {
            File imgFile = generateStatusImage(GenerateType.ONLY_WATERMARK, 0, bgFileName);
            for (Long gid : ALLOWED_GROUPS) {
                sendImage(gid, imgFile);
            }

            if (imgFile != null && imgFile.exists()) {
                imgFile.delete();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static File generateStatusImage(GenerateType type, int data, String bgName) throws Exception {
        return generateImageInternal(type, String.valueOf(data), bgName);
    }

    private static File generateOfflineDurationImage(String durationStr, String bgName) throws Exception {
        return generateImageInternal(GenerateType.OFFLINE_DURATION, durationStr, bgName);
    }

    private static File generateImageInternal(GenerateType type, String dataStr, String bgName) throws Exception {
        File bgFile = new File(bgName);
        if (!bgFile.exists()) {
            throw new Exception("背景图片 " + bgName + " 不存在！");
        }
        BufferedImage bg = ImageIO.read(bgFile);
        int w = bg.getWidth();
        int h = bg.getHeight();

        Graphics2D g = bg.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        Font font = loadFont();

        if (type == GenerateType.ONLINE_COUNT) {
            g.setFont(font.deriveFont(Font.BOLD, 40f));
            String title = "当前在线人数";
            int y1 = h / 2 - 20;
            drawCenteredText(g, title, w, y1, Color.WHITE);

            g.setFont(font.deriveFont(Font.BOLD, 155f));
            int y2 = h / 2 + 160;
            drawCenteredText(g, dataStr, w, y2, Color.WHITE);

        } else if (type == GenerateType.OFFLINE_DURATION) {
            g.setFont(font.deriveFont(Font.BOLD, 90f));
            String title = "服务器离线";
            int y1 = h / 2 + 30;
            drawCenteredText(g, title, w, y1, Color.WHITE);

            g.setFont(font.deriveFont(Font.BOLD, 50f));
            String subTitle = "离线时长 " + dataStr;
            int y2 = h / 2 + 145;
            drawCenteredText(g, subTitle, w, y2, Color.GRAY);
        }

        drawDateWatermark(g, w, h, font);
        g.dispose();

        File tmpDir = new File("tmp");
        if (!tmpDir.exists()) tmpDir.mkdirs();
        File outFile = new File(tmpDir, "gh_status_" + System.currentTimeMillis() + ".png");
        ImageIO.write(bg, "png", outFile);
        return outFile;
    }

    private static void drawCenteredText(Graphics2D g, String text, int w, int y, Color color) {
        FontMetrics fm = g.getFontMetrics();
        int x = (w - fm.stringWidth(text)) / 2;
        g.setColor(color);
        g.drawString(text, x, y);
    }

    private static void drawDateWatermark(Graphics2D g, int w, int h, Font font) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd HH:mm");
        String dateStr = sdf.format(new Date());

        g.setFont(font.deriveFont(Font.PLAIN, 20f));
        g.setColor(new Color(180, 180, 180));

        FontMetrics fm = g.getFontMetrics();
        int tw = fm.stringWidth(dateStr);
        int x = w - tw - 20;
        int y = h - 20;
        g.drawString(dateStr, x, y);
    }

    private static Font loadFont() {
        try {
            File fontFile = new File("MinecraftAE.ttf");
            if (fontFile.exists()) {
                return Font.createFont(Font.TRUETYPE_FONT, fontFile);
            }
        } catch (Exception ignored) {}
        return new Font("Arial", Font.BOLD, 1);
    }

    private static void sendImage(long groupId, File file) {
        if (file == null || !file.exists()) return;
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String b64 = Base64.getEncoder().encodeToString(bytes);
            MessageSender.sendGroupMessage(groupId, null, b64);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static ServerStatus fetchStatus() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URI(API_URL).toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(50000);
        conn.setReadTimeout(50000);

        ServerStatus result = new ServerStatus();

        if (conn.getResponseCode() == 200) {
            JsonNode root = jsonMapper.readTree(conn.getInputStream());
            result.online = root.path("online").asBoolean(false);
            if (result.online) {
                result.onlinePlayers = root.path("players").path("online").asInt(0);
            }
        } else {
            result.online = false;
        }
        return result;
    }

    private static class ServerStatus {
        boolean online = false;
        int onlinePlayers = 0;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StatusData {
        public Boolean lastKnownState = null;
        public long offlineStartTime = 0L;
        public long lastBroadcastTime = 0L;
    }

    private enum GenerateType {
        ONLINE_COUNT,    // 在线人数
        OFFLINE_DURATION,// 离线时长
        ONLY_WATERMARK   // 仅水印
    }
}
