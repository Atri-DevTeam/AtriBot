package top.yzljc.atribot.function.impl.pic;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.configuration.Properties;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 页面维护的初审开关与审核标准，模型凭据仍由 config.yml 管理
 */
@Slf4j
public final class ImageAiReviewSettings {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static Settings current;

    public record Settings(boolean enabled, String prompt, boolean rejectInappropriate, boolean rejectOffTopic) {
        public Settings {
            if (prompt == null || prompt.isBlank() || prompt.length() > 8000)
                throw new IllegalArgumentException("审核标准需为1至8000字");
            prompt = prompt.trim();
        }

        /**
         * 自动拒绝使用固定理由，模型生成的说明仅供管理端查看
         */
        public String rejectionReason(ImageAiReviewClient.Verdict verdict) {
            if (!enabled || !"REJECT".equals(verdict.verdict())) return null;
            return switch (verdict.category()) {
                case "INAPPROPRIATE" -> rejectInappropriate ? "存在不合适的内容" : null;
                case "OFF_TOPIC" -> rejectOffTopic ? "图片元素不符合要求" : null;
                default -> null;
            };
        }
    }

    public static synchronized Settings current() {
        if (current != null) return current;
        Path file = Path.of(Properties.IMAGE_AI_REVIEW_SETTINGS);
        try {
            if (Files.exists(file)) current = MAPPER.readValue(file.toFile(), Settings.class);
        } catch (IOException | IllegalArgumentException e) {
            log.warn("读取图源初审设置失败，使用停用状态", e);
        }
        if (current == null) current = new Settings(false, ImageAiReviewClient.DEFAULT_PROMPT, true, true);
        return current;
    }

    public static synchronized void save(Settings settings) throws IOException {
        save(Path.of(Properties.IMAGE_AI_REVIEW_SETTINGS), settings);
        current = settings;
    }

    static void save(Path file, Settings settings) throws IOException {
        Path target = file.toAbsolutePath();
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), "image-ai-review-", ".tmp");
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), settings);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
