package top.yzljc.qqbot.minecraft;

import top.yzljc.qqbot.img.MinecraftStatusImage;
import top.yzljc.qqbot.messages.MessageSender;

import java.io.File;
import java.nio.file.Files;
import java.util.Base64;

public class StatusReporter {

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
            System.out.println("[INFO] 开始构建推送消息...");

            // 1. 构造文本内容
            String textContent = String.format(
                    "[!] 服务器状态更新\n服务器：%s\n地址：%s:%d\n状态：%s",
                    name, ip, port, statusDesc
            );

            // 2. 图片生成逻辑
            File tmpDir = new File("tmp");
            if (!tmpDir.exists()) {
                tmpDir.mkdirs();
            }

            String fileName = String.format("status_%s_%d.png", id, System.currentTimeMillis());
            tempFile = new File(tmpDir, fileName);

            System.out.println("[INFO] 准备生成图片: " + tempFile.getAbsolutePath());
            String ipPort = ip + ":" + port;

            // 调用生成方法
            MinecraftStatusImage.generateStatusImage(name, ipPort, statusDesc, tempFile.getAbsolutePath());

            String base64Img = null;
            if (tempFile.exists()) {
                System.out.println("[INFO] 图片生成成功，大小: " + tempFile.length() + " 字节");
                byte[] imgBytes = Files.readAllBytes(tempFile.toPath());
                base64Img = Base64.getEncoder().encodeToString(imgBytes);
            } else {
                System.err.println("[Error] 图片生成失败，文件不存在！将只发送文本。");
            }

            // 3. 调用 MessageSender 发送
            // 如果 base64Img 为 null，MessageSender 会自动只发文本
            MessageSender.sendGroupMessage(groupId, textContent, base64Img);

        } catch (Exception ex) {
            System.err.println("[Error] 推送流程异常: " + ex.getMessage());
            ex.printStackTrace();
        } finally {
            // 4. 清理临时文件
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
                System.out.println("[INFO] 临时图片已清理");
            }
        }
    }
}