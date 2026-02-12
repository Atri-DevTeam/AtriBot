package top.yzljc.qqbot.feature.minecraft;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import top.yzljc.qqbot.botkits.message.MessageSender;
import top.yzljc.qqbot.botkits.image.AbstractImage;

import java.awt.*;
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
import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.botkits.thread.ThreadManager;
import top.yzljc.qqbot.command.CommandContext;
import top.yzljc.qqbot.command.ExecuteCommand;
import top.yzljc.qqbot.config.ConfigFile;

public class MojangStatus implements ExecuteCommand {

    private static final Logger log = LoggerFactory.getLogger(MojangStatus.class);
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    enum Status {
        ONLINE, OFFLINE
    }

    static class ServiceResult {
        String name;
        Status status;
        public ServiceResult(String name, Status status) {
            this.name = name;
            this.status = status;
        }
    }

    private static class ImageGen extends AbstractImage {

        public void generate(List<ServiceResult> results, File outFile) throws Exception {
            // 尝试加载背景，失败则创建纯黑背景
            try {
                initFromBackground(ConfigFile.IMG_MOJANGSTATUS.getFileName());
            } catch (Exception e) {
                initBlank(800, 600);
            }

            // 加载字体
            Font font = loadFont(Font.PLAIN, 24f);
            g.setFont(font);

            int startY = 190;
            int lineHeight = 65;
            int listX = 240; // 左边距

            for (ServiceResult entry : results) {
                String serviceName = entry.name;
                Status status = entry.status;

                g.setColor(Color.WHITE);
                g.drawString(serviceName, listX, startY);

                String statusText;
                Color statusColor;

                if (status == Status.ONLINE) {
                    statusText = "正常";
                    statusColor = new Color(85, 255, 85); // Green
                } else {
                    statusText = "宕机";
                    statusColor = new Color(255, 85, 85); // Red
                }

                // 状态靠右显示，使用父类 width 计算右边距
                int statusWidth = g.getFontMetrics().stringWidth(statusText);
                int statusX = width - listX - statusWidth;

                g.setColor(statusColor);
                g.drawString(statusText, statusX, startY);

                startY += lineHeight;
            }

            // 绘制底部时间 (使用父类方法 drawCenteredShadowText)
            g.setFont(font.deriveFont(Font.PLAIN, 16f));
            String timeStr = "Checked at: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            drawCenteredShadowText(timeStr, height - 30, Color.LIGHT_GRAY, Color.BLACK);

            // 保存并释放
            saveAndDispose(outFile);
        }
    }

    @Override
    public void execute(CommandContext ct) {
        long groupId = ct.getGroupId();
        if (!ct.getIsEnabled()) return;
        MessageSender.sendGroupMessage(groupId, "正在检查 Mojang 服务状态，请稍候...");
        log.info("开始检查 Mojang 服务状态 -> Group: {}", groupId);
        ThreadManager.execute(() -> performChecksAndSend(groupId));
    }

    private static void performChecksAndSend(long groupId) {
        File tempFile = null;
        try {
            // Session Server
            CompletableFuture<ServiceResult> checkSession = CompletableFuture.supplyAsync(() -> {
                Status s = checkSessionServer() ? Status.ONLINE : Status.OFFLINE;
                return new ServiceResult("Session Server", s);
            });

            // Textures
            CompletableFuture<ServiceResult> checkTexture = CompletableFuture.supplyAsync(() -> {
                Status s = checkTextures() ? Status.ONLINE : Status.OFFLINE;
                return new ServiceResult("Textures Server", s);
            });

            // Mojang API
            CompletableFuture<ServiceResult> checkApi = CompletableFuture.supplyAsync(() -> {
                Status s = checkMojangApi() ? Status.ONLINE : Status.OFFLINE;
                return new ServiceResult("Mojang API", s);
            });

            // Minecraft.net
            CompletableFuture<ServiceResult> checkMcNet = CompletableFuture.supplyAsync(() -> {
                Status s = checkTcpConnect("minecraft.net") ? Status.ONLINE : Status.OFFLINE;
                return new ServiceResult("Minecraft.net", s);
            });

            // Minecraft Services
            CompletableFuture<ServiceResult> checkServices = CompletableFuture.supplyAsync(() -> {
                Status s = checkTcpConnect("sessionserver.mojang.com") ? Status.ONLINE : Status.OFFLINE;
                return new ServiceResult("Minecraft Services", s);
            });

            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                    checkSession, checkTexture, checkApi, checkMcNet, checkServices
            );
            allFutures.join();

            List<ServiceResult> results = new ArrayList<>();
            results.add(checkSession.get());
            results.add(checkTexture.get());
            results.add(checkApi.get());
            results.add(checkMcNet.get());
            results.add(checkServices.get());

            File tmpDir = new File("tmp");
            if (!tmpDir.exists()) tmpDir.mkdirs();
            String fileName = "mojang_status_" + System.currentTimeMillis() + ".png";
            tempFile = new File(tmpDir, fileName);

            // 调用内部生成器
            new ImageGen().generate(results, tempFile);

            if (tempFile.exists()) {
                byte[] imgBytes = Files.readAllBytes(tempFile.toPath());
                String base64Img = Base64.getEncoder().encodeToString(imgBytes);
                MessageSender.sendGroupMessage(groupId, null, base64Img);
                log.info("图片发送成功 -> Group: {}", groupId);
            }

        } catch (Exception e) {
            log.error("处理异常：{}", e.getMessage());
            MessageSender.sendGroupMessage(groupId, "状态检查发生内部错误：" + e.getMessage());
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

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

    private static boolean checkTextures() {
        String url = "http://textures.minecraft.net/texture/7fd9ba42a7c81eeea22f1524271ae85a8e045ce0af5a6ae16c6406ae917e68b5";
        try {
            HttpURLConnection conn = (HttpURLConnection) new URI(url).toURL().openConnection();
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

    private static boolean checkTcpConnect(String host) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, 443), 3000); // 3秒超时
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String httpGet(String urlStr) {
        try {
            URL url = new URI(urlStr).toURL();
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
}