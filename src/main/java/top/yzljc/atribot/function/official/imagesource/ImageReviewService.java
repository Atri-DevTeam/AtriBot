package top.yzljc.atribot.function.official.imagesource;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.database.ImageReviewStatus;
import top.yzljc.atribot.database.ImageSourceDTO;
import top.yzljc.atribot.database.repo.ImageSourceRepository;
import top.yzljc.atribot.service.runtime.ThreadManager;
import top.yzljc.atribot.utils.notify.NotificationService;

import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;

/**
 * @Author YZ_Ljc_
 * @ClassName ImageReviewService
 * @Created_at 2026/07/21
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.official.imagesource
 */
@Slf4j
public class ImageReviewService {

    private static final String SOURCE = "image_source";
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 审核一条投稿
     *
     * @param status 目标状态；置回 {@link ImageReviewStatus#PENDING} 表示撤销审核，不发通知
     * @return 是否成功
     */
    public static boolean review(String id, ImageReviewStatus status, String reviewer, String remark) {
        ImageSourceDTO dto = ImageSourceRepository.findById(id);
        if (dto == null) {
            log.warn("审核目标不存在: id={}", id);
            return false;
        }

        if (!ImageSourceRepository.review(id, status, reviewer, remark)) {
            return false;
        }

        dto.setReviewStatus(status.name());
        dto.setReviewer(reviewer);
        dto.setReviewRemark(remark);
        dto.setReviewTime(new Timestamp(System.currentTimeMillis()));

        // 撤销审核只回到未审核状态，不打扰投稿人
        if (status == ImageReviewStatus.PENDING) {
            return true;
        }

        ThreadManager.execute(() -> {
            notifyUploader(dto);
        });
        return true;
    }

    private static void notifyUploader(ImageSourceDTO dto) {
        try {
            String markdown = buildResultMarkdown(dto);
            NotificationService.notify(dto.getPlatform(), dto.getUploaderId(), dto.getGroupId(),
                    markdown, SOURCE, dto.getId());
            // 入队即视为已交办，后续送达由队列负责，避免审核页反复重推
            ImageSourceRepository.markNotified(dto.getId());
        } catch (Exception e) {
            log.error("推送图源审核结果失败: id={}", dto.getId(), e);
        }
    }

    private static String buildResultMarkdown(ImageSourceDTO dto) {
        boolean approved = ImageReviewStatus.of(dto.getReviewStatus()) == ImageReviewStatus.REVIEWED;

        StringBuilder sb = new StringBuilder();
        sb.append(approved ? "**你的图源投稿已通过审核**" : "**你的图源投稿未通过审核**").append("\n");
        sb.append("投稿编号: ").append(shortId(dto.getId())).append("\n");
        sb.append("投稿时间: ").append(formatTime(dto.getCreateTime())).append("\n");
        sb.append("审核时间: ").append(formatTime(dto.getReviewTime())).append("\n");
        if (dto.getReviewRemark() != null && !dto.getReviewRemark().isBlank()) {
            sb.append("审核说明: ").append(dto.getReviewRemark()).append("\n");
        }
        sb.append("> ").append(Markdown.enterCommand("/投稿 ", "我也要投稿"));

        return sb.toString();
    }

    private static String formatTime(Timestamp ts) {
        if (ts == null) return "-";
        return ts.toLocalDateTime().format(TIME_FMT);
    }

    private static String shortId(String id) {
        if (id == null) return "-";
        return id.length() <= 8 ? id : id.substring(0, 8);
    }
}