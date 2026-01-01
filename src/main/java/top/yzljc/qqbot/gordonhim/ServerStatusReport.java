package top.yzljc.qqbot.gordonhim;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import top.yzljc.qqbot.messages.MessageSender;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * GordonHim 服务器状态专属查询 & 监控
 */
public class ServerStatusReport {

    // ==== 配置区域 ====

    // 限定群号
    private static final List<Long> ALLOWED_GROUPS = Arrays.asList(
            883993372L,
            978885201L,
            626462367L,
            1039954708L
    );

    // 固定目标 API
    private static final String API_URL = "https://api.mcstatus.io/v2/status/java/GordonHim.com";

    // 触发关键词
    private static final String TRIGGER_CMD = "在线人数";

    // 图片文件名配置
    private static final String BG_NORMAL = "gh_background.jpg"; // 普通背景
    private static final String BG_ONLINE = "gh_online.jpg";     // 开服通知图
    private static final String BG_OFFLINE = "gh_offline.jpg";   // 关服通知图

    // 数据存储文件
    private static final File DATA_FILE = new File("status_data.json");

    // 工具对象
    private static final ObjectMapper jsonMapper = new ObjectMapper();
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    // 运行时数据持有对象
    private static StatusData currentData = new StatusData();

    /**
     * 初始化定时任务
     */
    public static void init() {
        // 1. 启动时先加载数据
        loadData();

        // 2. 每15分钟执行一次检测
        scheduler.scheduleAtFixedRate(ServerStatusReport::executeScheduledCheck, 0, 15, TimeUnit.MINUTES);
        System.out.println("[GordonHim] 监控任务已启动，每15分钟检查一次，数据存储于: " + DATA_FILE.getAbsolutePath());
    }

    /**
     * 消息处理入口
     */
    public static void process(JsonNode json) {
        if (!"message".equals(json.path("post_type").asText())) return;
        if (!"group".equals(json.path("message_type").asText())) return;

        long groupId = json.path("group_id").asLong();
        String rawMsg = json.path("raw_message").asText().trim();

        if (!ALLOWED_GROUPS.contains(groupId)) return;

        if (TRIGGER_CMD.equals(rawMsg)) {
            Executors.newSingleThreadExecutor().submit(() -> handleManualQuery(groupId));
        }
    }

    /**
     * 处理用户主动查询
     */
    private static void handleManualQuery(long groupId) {
        try {
            ServerStatus status = fetchStatus();

            if (status.online) {
                // 在线：发送人数渲染图
                File imgFile = generateStatusImage(GenerateType.ONLINE_COUNT, status.onlinePlayers, BG_NORMAL);
                sendImage(groupId, imgFile);
                // 手动删除文件
                if (imgFile != null) imgFile.delete();
            } else {
                // 离线：计算时长
                // 使用 offlineStartTime 计算
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
                    durationStr = "刚刚"; // 还没记录到离线时间，说明刚掉线或者刚重启程序
                }

                // 生成离线时长图片
                File imgFile = generateOfflineDurationImage(durationStr, BG_NORMAL);
                sendImage(groupId, imgFile);
                // 手动删除文件
                if (imgFile != null) imgFile.delete();
            }
        } catch (Exception e) {
            e.printStackTrace();
            MessageSender.sendGroupMessage(groupId, "查询失败: " + e.getMessage());
        }
    }

    /**
     * 定时检测逻辑 (每15分钟)
     */
    private static void executeScheduledCheck() {
        try {
            ServerStatus status = fetchStatus();
            long now = System.currentTimeMillis();
            boolean dataChanged = false;

            // 首次运行，只记录状态
            if (currentData.lastKnownState == null) {
                currentData.lastKnownState = status.online;
                // 如果启动时就是离线，记录一下时间防止显示null
                if (!status.online && currentData.offlineStartTime == 0) {
                    currentData.offlineStartTime = now;
                }
                saveData(); // 立即保存初始状态
                return;
            }

            boolean isOnlineNow = status.online;
            boolean wasOnline = currentData.lastKnownState;

            // 1. 检测状态变化
            if (wasOnline && !isOnlineNow) {
                // 状态变为离线 -> 发送 gh_offline.jpg
                System.out.println("[GordonHim] 检测到服务器离线，全员推送...");

                // 记录离线开始时间
                currentData.offlineStartTime = now;
                dataChanged = true;

                broadcastImage(BG_OFFLINE);
            }
            else if (!wasOnline && isOnlineNow) {
                // 状态变为在线 -> 发送 gh_online.jpg
                System.out.println("[GordonHim] 检测到服务器开服，全员推送...");

                // 重置离线时间
                currentData.offlineStartTime = 0L;
                dataChanged = true;

                broadcastImage(BG_ONLINE);
            }

            // 2. 在线人数播报逻辑
            if (isOnlineNow) {
                // 人数 >= 25 且 冷却时间已过 (2小时)
                if (status.onlinePlayers >= 25) {
                    if (now - currentData.lastBroadcastTime > 7200 * 1000) {
                        System.out.println("[GordonHim] 人数达标(" + status.onlinePlayers + ")，触发全员播报");
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

            // 更新状态缓存
            if (currentData.lastKnownState != isOnlineNow) {
                currentData.lastKnownState = isOnlineNow;
                dataChanged = true;
            }

            // 如果数据有变动，保存到 JSON 文件
            if (dataChanged) {
                saveData();
            }

        } catch (Exception e) {
            System.err.println("[GordonHim] 定时检测异常: " + e.getMessage());
        }
    }

    // ==== 数据持久化方法 ====

    private static void loadData() {
        try {
            if (DATA_FILE.exists()) {
                currentData = jsonMapper.readValue(DATA_FILE, StatusData.class);
            } else {
                currentData = new StatusData();
            }
        } catch (Exception e) {
            System.err.println("[GordonHim] 读取数据文件失败: " + e.getMessage());
            System.out.println("[GordonHim] 数据文件可能损坏或版本不兼容，已重置状态数据。");
            currentData = new StatusData(); // 失败时重置，防止报错卡死
            saveData(); // 覆盖坏文件
        }
    }

    private static void saveData() {
        try {
            jsonMapper.writeValue(DATA_FILE, currentData);
        } catch (Exception e) {
            System.err.println("[GordonHim] 保存数据文件失败: " + e.getMessage());
        }
    }

    // ==== 核心功能方法 ====

    private static void broadcastImage(String bgFileName) {
        try {
            File imgFile = generateStatusImage(GenerateType.ONLY_WATERMARK, 0, bgFileName);

            // 循环推送给所有允许的群
            for (Long gid : ALLOWED_GROUPS) {
                sendImage(gid, imgFile);
            }

            // 所有群发送完毕后，再删除文件
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

    /**
     * 发送图片
     * 修改说明：移除了 file.delete() 操作，防止在循环发送时文件丢失。
     * 文件的清理工作现在由调用者负责。
     */
    private static void sendImage(long groupId, File file) {
        if (file == null || !file.exists()) return;
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String b64 = Base64.getEncoder().encodeToString(bytes);
            MessageSender.sendGroupMessage(groupId, null, b64);
            // 警告：这里不要删除文件，因为如果是广播模式，其他群还没发呢
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static ServerStatus fetchStatus() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(API_URL).openConnection();
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

    // 内部类用于传递状态
    private static class ServerStatus {
        boolean online = false;
        int onlinePlayers = 0;
    }

    // 数据持久化类
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