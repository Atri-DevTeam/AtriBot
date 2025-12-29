package top.yzljc.utiltools.img;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 * 生成并推送魔法少女の魔女审判 x Minecraft 项目进度图片到QQ群（自动计算天数）
 */
public class ManosabaDate {
    private static final long GROUP_ID = 1041561558L;
    private static final String NAPCAT_API = "http://106.14.23.232:8848/send_group_msg";

    /**
     * 生成项目第N天图片：temp/manoday.png
     * @throws IOException 生成异常
     */
    public static void generateDevelopDayImage() throws IOException {
        File tempDir = new File("tmp");
        if (!tempDir.exists()) tempDir.mkdirs();
        File outFile = new File(tempDir, "manoday.png");

        String imgFileName = "manosaba.png";
        File bgFile = new File(imgFileName);
        if (!bgFile.exists()) throw new IOException("未找到背景图片: " + imgFileName);

        BufferedImage img = ImageIO.read(bgFile);
        Graphics2D g = img.createGraphics();

        // 抗锯齿
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 项目开始日
        LocalDate start = LocalDate.of(2025, 11, 13);
        LocalDate now = LocalDate.now();
        long days = ChronoUnit.DAYS.between(start, now) + 1;
        if (days < 1) days = 1;

        // 分三行
        String line1 = "你说的对，但是今天是";
        String line2 = "【魔法少女の魔女审判 x Minecraft】项目";
        String line3 = "开发的第 " + days + " 天";

        // 使用自定义 MinecraftAE.ttf 字体作为主字体
        Font font;
        File fontFile = new File("MinecraftAE.ttf");
        if (fontFile.exists()) {
            try {
                font = Font.createFont(Font.TRUETYPE_FONT, fontFile).deriveFont(Font.BOLD, 28f);
            } catch (Exception e) {
                System.err.println("[ImgWarning] 自定义字体加载失败，将使用默认字体: " + e.getMessage());
                font = new Font(Font.SANS_SERIF, Font.BOLD, 28);
            }
        } else {
            System.err.println("[ImgWarning] 字体文件 MinecraftAE.ttf 未找到，将使用默认无衬线字体。");
            font = new Font(Font.SANS_SERIF, Font.BOLD, 28);
        }
        g.setFont(font);

        Color cyan = new Color(0, 200, 255);

        // 计算居中位置与行距
        FontMetrics fm = g.getFontMetrics(font);
        int imgW = img.getWidth();
        int imgH = img.getHeight();
        int lineHeight = fm.getHeight();
        int padding = 20;
        String[] lines = {line1, line2, line3};
        int totalHeight = lineHeight * lines.length;
        int firstY = imgH - padding - totalHeight + fm.getAscent();

        for (int i = 0; i < lines.length; ++i) {
            String line = lines[i];
            int textWidth = fm.stringWidth(line);
            int x = (imgW - textWidth) / 2;
            int y = firstY + i * lineHeight;

            // 阴影
            g.setColor(new Color(0, 0, 0, 128));
            g.drawString(line, x + 2, y + 2);
            // 主体
            g.setColor(cyan);
            g.drawString(line, x, y);
        }

        g.dispose();
        ImageIO.write(img, "png", outFile);
    }

    public static void processManodate(com.fasterxml.jackson.databind.JsonNode json) {
        // 只监听 group 消息且内容为 manodate
        String postType = json.path("post_type").asText("");
        if (!"message".equals(postType)) return;
        String messageType = json.path("message_type").asText("");
        if (!"group".equals(messageType)) return;
        String rawMessage = json.path("raw_message").asText("").trim().toLowerCase();
        long groupId = json.path("group_id").asLong();
        if ("manodate".equals(rawMessage)) {
            // 立即触发
            sendAndNotifyToGroup();
            System.out.println("[INFO] manodate 指令触发图片推送：" + groupId);
        }
    }

    /**
     * 自动生成并推送每日图片到目标QQ群。推荐每天0点调用。
     * 支持发送完自动删除图片
     */
    public static boolean sendAndNotifyToGroup() {
        File tempFile = new File("tmp", "manoday.png");
        try {
            generateDevelopDayImage();
            if (!tempFile.exists()) {
                System.err.println("[INFO] manoday.png 生成失败，图片不存在！");
                return false;
            }

            // 图片转base64
            byte[] imgBytes = Files.readAllBytes(tempFile.toPath());
            String base64Img = Base64.getEncoder().encodeToString(imgBytes);
            String fileUrl = "base64://" + base64Img;

            // 构造NapCat群发消息
            Map<String, Object> imgData = new HashMap<>();
            imgData.put("name", "manoday.png");
            imgData.put("file", fileUrl);

            Map<String, Object> imgNode = new HashMap<>();
            imgNode.put("type", "image");
            imgNode.put("data", imgData);

            Object[] messageList = new Object[]{imgNode};
            Map<String, Object> payloadMap = new HashMap<>();
            payloadMap.put("group_id", GROUP_ID);
            payloadMap.put("message", messageList);

            String payload = new ObjectMapper().writeValueAsString(payloadMap);

            // 发送HTTP POST
            HttpURLConnection conn = (HttpURLConnection) new URL(NAPCAT_API).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.getOutputStream().write(payload.getBytes(StandardCharsets.UTF_8));
            int code = conn.getResponseCode();
            System.out.println("[INFO] 群图片HTTP响应码: " + code);
            conn.getInputStream().close();
            return code == 200;

        } catch (Exception ex) {
            System.err.println("[INFO] 推送图片异常: " + ex.getMessage());
            ex.printStackTrace();
            return false;
        } finally {
            // 自动删除
            File tempFileDel = new File("temp", "manoday.png");
            if (tempFileDel.exists()) {
                if (tempFileDel.delete()) {
                    System.out.println("[INFO] 临时图片已自动清理: " + tempFileDel.getAbsolutePath());
                }
            }
        }
    }

    /**
     * 每天0:00:11自动发送
     */
    public static void startAutoDailyTask() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        Runnable task = new Runnable() {
            @Override
            public void run() {
                sendAndNotifyToGroup();
            }
        };

        long initialDelay = computeInitialDelayToMidnight11();
        long period = 24 * 60 * 60; // 24小时，单位秒

        scheduler.scheduleAtFixedRate(task, initialDelay, period, TimeUnit.SECONDS);
        System.out.println("[INFO] 已启动每日0:00:11自动推送任务");
    }

    // 计算离下一个0:00:11的秒数
    private static long computeInitialDelayToMidnight11() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun = now.toLocalDate().atStartOfDay().plusDays(0).plusSeconds(11);
        if (!now.isBefore(nextRun)) {
            nextRun = nextRun.plusDays(1);
        }
        return Duration.between(now, nextRun).toSeconds();
    }
}