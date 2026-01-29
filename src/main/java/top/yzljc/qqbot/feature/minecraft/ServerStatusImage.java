package top.yzljc.qqbot.feature.minecraft;

import top.yzljc.qqbot.botkits.image.AbstractImage;
import top.yzljc.qqbot.botkits.image.DrawMotd;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class ServerStatusImage {

    /**
     * 外部接口：绘制服务器状态卡片
     * @param serverName 服务器名称（保留但不作图内显示）
     * @param ipPort ip:port
     * @param state "在线"/"离线"
     * @param outputPath 导出图片路径
     */
    public static void generateStatusImage(String serverName, String ipPort, String state, String outputPath) throws Exception {

        String host;
        int port;
        int idx = ipPort.lastIndexOf(':');
        if (idx >= 0) {
            host = ipPort.substring(0, idx).trim();
            port = Integer.parseInt(ipPort.substring(idx + 1));
        } else {
            host = ipPort;
            port = 25565;
        }
        DrawMotd.MotdResult motdResult = DrawMotd.fetchMotdData(host, port);

        File tmpImg = File.createTempFile("motdtile_status_", ".png");
        DrawMotd.MotdImageGen.generate(host, port, motdResult, tmpImg);
        BufferedImage baseImg = ImageIO.read(tmpImg);

        new OverlayStatusTool().overlayState(baseImg, state);

        ImageIO.write(baseImg, "png", new File(outputPath));
        if (tmpImg.exists()) tmpImg.delete();
    }

    private static class OverlayStatusTool extends AbstractImage {
        public void overlayState(BufferedImage baseImg, String label) {
            this.image = baseImg;
            this.width = image.getWidth();
            this.height = image.getHeight();
            this.g = image.createGraphics();
            setupRenderingHints();

            int panelRight = width - 32;
            int iconSize = 56;
            int pad = 24;

            int yIconTop = 24 + 12;
            int yIcon = yIconTop - 18;
            int xIcon = panelRight - pad - iconSize;

            Color color = "在线".equals(label) ? new Color(80, 200, 80) : new Color(200, 80, 80);

            int fontSize = 32;
            Font font = loadFont(Font.BOLD, (float) fontSize);
            g.setFont(font);
            FontMetrics fm = g.getFontMetrics();
            int labelW = fm.stringWidth(label);
            int labelH = fm.getAscent();

            int xText = xIcon - labelW - 12;
            int yText = yIcon + iconSize / 2 + labelH / 2 + 25;

            drawShadowText(label, xText, yText, color, Color.BLACK);

            g.dispose();
        }
    }
}