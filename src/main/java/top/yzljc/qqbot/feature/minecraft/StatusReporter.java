package top.yzljc.qqbot.feature.minecraft;

import top.yzljc.qqbot.botkits.message.MessageSender;

import java.io.File;
import java.nio.file.Files;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StatusReporter {

    private static final Logger log = LoggerFactory.getLogger(StatusReporter.class);

    /**
     * 生成状态图片并推送到群
     *
     * @param groupId 群号
     * @param name    服务器名称
     * @param ip      服务器IP
     * @param port    服务器端口
     * @param id      服务器ID（用于生成文件名）
     * @param isOnline 是否在线
     */
    public static void sendReport(long groupId, String name, String ip, int port, String id, boolean isOnline) {
        File tempFile = null;
        String statusDesc = isOnline ? "在线" : "离线";

        try {
            log.info("开始构建推送消息……");

            String textContent = String.format(
                    "[!] 服务器状态更新\n服务器：%s\n地址：%s:%d\n状态：%s",
                    name, ip, port, statusDesc
            );

            File tmpDir = new File("tmp");
            if (!tmpDir.exists()) {
                tmpDir.mkdirs();
            }

            String fileName = String.format("status_%s_%d.png", id, System.currentTimeMillis());
            tempFile = new File(tmpDir, fileName);

            log.info("准备生成图片：{}", tempFile.getAbsolutePath());
            String ipPort = ip + ":" + port;

            MinecraftStatusImage.generateStatusImage(name, ipPort, statusDesc, tempFile.getAbsolutePath());

            String base64Img = null;
            if (tempFile.exists()) {
                log.info("图片生成成功，大小：{}", tempFile.length());
                byte[] imgBytes = Files.readAllBytes(tempFile.toPath());
                base64Img = Base64.getEncoder().encodeToString(imgBytes);
            } else {
                log.warn("图片生成失败，文件不存在，将只发送文本");
            }

            MessageSender.sendGroupMessage(groupId, textContent, base64Img);

        } catch (Exception ex) {
            log.error("推送流程异常：{}", ex.getMessage());
            ex.printStackTrace();
        } finally {
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
                log.info("临时图片已清理");
            }
        }
    }
}
