package top.yzljc.qqbot.minecraft;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import top.yzljc.qqbot.messages.MessageSender;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

/**
 * 检查 Mojang 官方服务状态 (自定义独立检测版)
 */
public class MojangStatus {

    private static final String BACKGROUND_FILE = "mojangstatus.png";
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    // 定义状态枚举
    enum Status {
        ONLINE, OFFLINE
    }

    // 内部类用于存储单项服务结果
    static class ServiceResult {
        String name;
        Status status;

        public ServiceResult(String name, Status status) {
            this.name = name;
            this.status = status;
        }
    }

    /**
     * 消息处理入口
     */
    public static void process(JsonNode json) {
        if (!"message".equals(json.path("post_type").asText())) return;
        if (!"group".equals(json.path("message_type").asText())) return;

        long groupId = json.path("group_id").asLong();
        String rawMsg = json.path("raw_message").asText().trim();

        if ("/mojang".equalsIgnoreCase(rawMsg)) {
            MessageSender.sendGroupMessage(groupId, "正在检查 Mojang 服务状态，请稍候...");
            Executors.newSingleThreadExecutor().submit(() -> performChecksAndSend(groupId));
        }
    }

    private static void performChecksAndSend(long groupId) {
        File tempFile = null;
        try {
            // ==== 并发执行所有检测任务 ====

            // 1. Session Server (验证 UUID)
            CompletableFuture<ServiceResult> checkSession = CompletableFuture.supplyAsync(() -> {
                Status s = checkSessionServer() ? Status.ONLINE : Status.OFFLINE;
                return new ServiceResult("Session Server", s);
            });

            // 2. Textures (检查图片加载)
            CompletableFuture<ServiceResult> checkTexture = CompletableFuture.supplyAsync(() -> {
                Status s = checkTextures() ? Status.ONLINE : Status.OFFLINE;
                return new ServiceResult("Textures Server", s);
            });

            // 3. Mojang API (Updated: 查 jeb_ 的 profile)
            CompletableFuture<ServiceResult> checkApi = CompletableFuture.supplyAsync(() -> {
                Status s = checkMojangApi() ? Status.ONLINE : Status.OFFLINE;
                return new ServiceResult("Mojang API", s);
            });

            // (已移除 Assets Server 检测)

            // 4. Minecraft.net (Ping 官网)
            CompletableFuture<ServiceResult> checkMcNet = CompletableFuture.supplyAsync(() -> {
                Status s = checkTcpConnect("minecraft.net", 443) ? Status.ONLINE : Status.OFFLINE;
                return new ServiceResult("Minecraft.net", s);
            });

            // 5. Minecraft Services (Ping sessionserver)
            CompletableFuture<ServiceResult> checkServices = CompletableFuture.supplyAsync(() -> {
                Status s = checkTcpConnect("sessionserver.mojang.com", 443) ? Status.ONLINE : Status.OFFLINE;
                return new ServiceResult("Minecraft Services", s);
            });

            // 等待所有任务完成
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                    checkSession, checkTexture, checkApi, checkMcNet, checkServices
            );
            allFutures.join();

            // 收集结果 (按顺序)
            List<ServiceResult> results = new ArrayList<>();
            results.add(checkSession.get());
            results.add(checkTexture.get());
            results.add(checkApi.get());
            results.add(checkMcNet.get());
            results.add(checkServices.get());

            // 2. 生成图片
            File tmpDir = new File("tmp");
            if (!tmpDir.exists()) tmpDir.mkdirs();
            String fileName = "mojang_status_" + System.currentTimeMillis() + ".png";
            tempFile = new File(tmpDir, fileName);

            drawStatusImage(results, tempFile);

            // 3. 发送图片
            if (tempFile.exists()) {
                byte[] imgBytes = Files.readAllBytes(tempFile.toPath());
                String base64Img = Base64.getEncoder().encodeToString(imgBytes);
                MessageSender.sendGroupMessage(groupId, null, base64Img);
                System.out.println("[INFO] 图片发送成功 -> Group: " + groupId);
            }

        } catch (Exception e) {
            System.err.println("[INFO] 处理异常: " + e.getMessage());
            e.printStackTrace();
            MessageSender.sendGroupMessage(groupId, "状态检查发生内部错误: " + e.getMessage());
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    // ================= 检测逻辑实现 =================

    /**
     * 1. 检查 Session Server
     * URL: https://sessionserver.mojang.com/session/minecraft/profile/853c80ef3c3749fdaa49938b674adae6
     * 规则: 返回JSON包含 "id" : "853c80ef3c3749fdaa49938b674adae6"
     */
    private static boolean checkSessionServer() {
        String url = "https://sessionserver.mojang.com/session/minecraft/profile/853c80ef3c3749fdaa49938b674adae6";
        try {
            String content = httpGet(url);
            if (content == null) return false;
            JsonNode root = jsonMapper.readTree(content);
            String id = root.path("id").asText();
            return "853c80ef3c3749fdaa49938b674adae6".equalsIgnoreCase(id);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 2. 检查 Textures
     * URL: http://textures.minecraft.net/texture/...
     * 规则: HTTP 200 即为正常
     */
    private static boolean checkTextures() {
        String url = "http://textures.minecraft.net/texture/7fd9ba42a7c81eeea22f1524271ae85a8e045ce0af5a6ae16c6406ae917e68b5";
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            int code = conn.getResponseCode();
            return code == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 3. 检查 Mojang API (Updated)
     * URL: https://api.mojang.com/users/profiles/minecraft/jeb_
     * 规则: 返回JSON包含 "name": "jeb_"
     */
    private static boolean checkMojangApi() {
        String url = "https://api.mojang.com/users/profiles/minecraft/jeb_";
        try {
            String content = httpGet(url);
            if (content == null) return false;
            JsonNode root = jsonMapper.readTree(content);
            String name = root.path("name").asText();
            return "jeb_".equalsIgnoreCase(name);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 4 & 5. 通用 TCP 连接检查 (模拟 Ping)
     */
    private static boolean checkTcpConnect(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 3000); // 3秒超时
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ================= 辅助工具 =================

    private static String httpGet(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (QQBot Check)");
            conn.setRequestProperty("Accept", "application/json");

            int code = conn.getResponseCode();
            if (code != 200) return null;

            try (InputStream in = conn.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 绘图逻辑
     */
    private static void drawStatusImage(List<ServiceResult> results, File outFile) throws Exception {
        File bgFile = new File(BACKGROUND_FILE);
        BufferedImage bg;
        if (bgFile.exists()) {
            bg = ImageIO.read(bgFile);
        } else {
            bg = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = bg.createGraphics();
            g2d.setColor(Color.BLACK);
            g2d.fillRect(0, 0, 800, 600);
            g2d.dispose();
        }

        int width = bg.getWidth();
        int height = bg.getHeight();
        Graphics2D g = bg.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 加载字体
        Font font;
        try {
            File fontFile = new File("MinecraftAE.ttf");
            if (fontFile.exists()) {
                font = Font.createFont(Font.TRUETYPE_FONT, fontFile);
            } else {
                font = new Font("SansSerif", Font.BOLD, 1);
            }
        } catch (Exception e) {
            font = new Font("SansSerif", Font.BOLD, 1);
        }

        // ==== 绘制列表 ====
        g.setFont(font.deriveFont(Font.PLAIN, 24f));

        // 坐标参数
        int startY = 190;
        int lineHeight = 65;
        int listX = 240; // 左边距

        for (ServiceResult entry : results) {
            String serviceName = entry.name;
            Status status = entry.status;

            // 绘制服务名
            g.setColor(Color.WHITE);
            g.drawString(serviceName, listX, startY);

            // 绘制状态
            String statusText;
            Color statusColor;

            if (status == Status.ONLINE) {
                statusText = "正常";
                statusColor = new Color(85, 255, 85); // Green
            } else {
                statusText = "宕机";
                statusColor = new Color(255, 85, 85); // Red
            }

            // 状态靠右显示
            int statusWidth = g.getFontMetrics().stringWidth(statusText);
            int statusX = width - listX - statusWidth;

            g.setColor(statusColor);
            g.drawString(statusText, statusX, startY);

            startY += lineHeight;
        }

        // 绘制底部时间
        g.setFont(font.deriveFont(Font.PLAIN, 16f));
        String timeStr = "Checked at: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        drawCenteredShadowText(g, timeStr, width, height - 30, Color.LIGHT_GRAY, Color.BLACK);

        g.dispose();
        ImageIO.write(bg, "png", outFile);
    }

    private static void drawCenteredShadowText(Graphics2D g, String text, int imgWidth, int y, Color color, Color shadowColor) {
        FontMetrics fm = g.getFontMetrics();
        int x = (imgWidth - fm.stringWidth(text)) / 2;
        g.setColor(shadowColor);
        g.drawString(text, x + 2, y + 2);
        g.setColor(color);
        g.drawString(text, x, y);
    }
}