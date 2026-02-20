package top.yzljc.qqbot.botkits.image;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * 抽象图片生成器基类
 * 负责管理 Graphics2D 生命周期、字体加载、抗锯齿和通用绘图方法
 */
public abstract class AbstractImage {
    static Settings settings = Config.getInstance();
    private static final String DEFAULT_FONT = settings.getTtfFileName();
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected BufferedImage image;
    protected Graphics2D g;
    protected int width;
    protected int height;

    /**
     * 初始化：从现有背景图片加载
     */
    protected void initFromBackground(String bgPath) throws IOException {
        File bgFile = new File(bgPath);
        if (bgFile.exists() && bgFile.isFile()) {
            this.image = ImageIO.read(bgFile);
            this.width = image.getWidth();
            this.height = image.getHeight();
            this.g = image.createGraphics();
            setupRenderingHints();
            return;
        }

        InputStream resourceStream = null;
        try {
            resourceStream = getClass().getClassLoader().getResourceAsStream(bgPath);
            if (resourceStream != null) {
                this.image = ImageIO.read(resourceStream);
                this.width = image.getWidth();
                this.height = image.getHeight();
                this.g = image.createGraphics();
                setupRenderingHints();
                return;
            }
        } finally {
            if (resourceStream != null) try { resourceStream.close(); } catch (Exception ignore) {}
        }
        throw new IOException("背景图片未找到: " + bgPath);
    }

    /**
     * 初始化：创建空白画布
     */
    protected void initBlank(int width, int height) {
        this.image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        this.width = width;
        this.height = height;
        this.g = image.createGraphics();
        // 默认填充黑色背景，防止透明
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, width, height);
        setupRenderingHints();
    }

    /**
     * 设置抗锯齿
     */
    protected void setupRenderingHints() {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    /**
     * 加载字体
     */
    protected Font loadFont(float size) {
        return loadFont(Font.PLAIN, size);
    }

    protected Font loadFont(int style, float size) {
        File fontFile = new File(DEFAULT_FONT);
        if (fontFile.exists() && fontFile.isFile()) {
            try {
                return Font.createFont(Font.TRUETYPE_FONT, fontFile).deriveFont(style, size);
            } catch (Exception e) {
                log.warn("根目录自定义字体加载失败，尝试从 Resource 目录加载...", e);
            }
        }

        try (InputStream resourceStream = getClass().getClassLoader().getResourceAsStream("DefaultFont.ttf")) {
            if (resourceStream != null) {
                return Font.createFont(Font.TRUETYPE_FONT, resourceStream).deriveFont(style, size);
            }
        } catch (Exception e) {
            log.warn("Resource 目录自定义字体加载失败", e);
        }

        return new Font(Font.SANS_SERIF, style, (int) size);
    }

    /**
     * 绘制带阴影的文字
     */
    protected void drawShadowText(String text, int x, int y, Color color, Color shadowColor) {
        g.setColor(shadowColor);
        g.drawString(text, x + 2, y + 2);
        g.setColor(color);
        g.drawString(text, x, y);
    }

    /**
     * 绘制水平居中的带阴影文字
     */
    protected void drawCenteredShadowText(String text, int y, Color color, Color shadowColor) {
        FontMetrics fm = g.getFontMetrics();
        int x = (width - fm.stringWidth(text)) / 2;
        drawShadowText(text, x, y, color, shadowColor);
    }

    /**
     * 保存并释放资源
     */
    public void saveAndDispose(File outFile) throws IOException {
        if (g != null) {
            g.dispose();
        }
        ImageIO.write(image, "png", outFile);
        if (!outFile.exists() || outFile.length() == 0) {
            throw new IOException("图片保存失败: " + outFile.getAbsolutePath());
        }
    }

}