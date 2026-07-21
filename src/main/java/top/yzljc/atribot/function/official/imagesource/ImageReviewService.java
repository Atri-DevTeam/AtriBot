package top.yzljc.atribot.function.official.imagesource;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.database.ImageReviewStatus;
import top.yzljc.atribot.database.ImageSourceDTO;
import top.yzljc.atribot.database.repo.ImageSourceRepository;
import top.yzljc.atribot.service.runtime.ThreadManager;
import top.yzljc.atribot.utils.notify.NotificationService;

import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;

/**
 * 图源审核动作的统一入口：落库 → 回传远端 → 通知投稿人。
 *
 * <p>WebUI 与后续可能的指令端都走这里，避免通知逻辑散落多处。
 *
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
     * 审核一条投稿（WebUI / 指令侧入口，结论会回传远端）。
     *
     * @param status 目标状态；置回 {@link ImageReviewStatus#PENDING} 表示撤销审核，不发通知
     * @return 是否成功
     */
    public static boolean review(String id, ImageReviewStatus status, String reviewer, String remark) {
        return review(id, status, reviewer, remark, true);
    }

    /**
     * 审核一条投稿。
     *
     * @param reportToRemote 远端回调进来的审核结论要传 false，否则会把结论又推回远端，绕成回环
     */
    public static boolean review(String id, ImageReviewStatus status, String reviewer, String remark,
                                 boolean reportToRemote) {
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

        // 回传远端与推送通知都涉及网络，别阻塞 WebUI / 回调请求线程
        ThreadManager.execute(() -> {
            if (reportToRemote) {
                ImageSourceClient.reportReview(dto);
            }
            notifyUploader(dto);
        });
        return true;
    }

    /**
     * 远端回调触发的审核落地：按图片 uuid 定位记录，只落库 + 通知投稿人，不再回传远端。
     *
     * @return 是否命中并处理了记录
     */
    public static boolean applyRemoteReview(String imageUuid, ImageReviewStatus status, String reviewer, String remark) {
        ImageSourceDTO dto = ImageSourceRepository.findByImageUuid(imageUuid);
        if (dto == null) {
            log.warn("远端审核回调未匹配到投稿: uuid={}", imageUuid);
            return false;
        }
        return review(dto.getId(), status, reviewer != null ? reviewer : "remote", remark, false);
    }

    /**
     * 把审核结果推给投稿人：先试主动消息，不可用则进被动队列。
     */
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
        sb.append("\n---\n");
        sb.append("审核时间: ").append(formatTime(dto.getReviewTime())).append("\n");
        sb.append("审核结果: ").append(approved ? "已收录进图库" : "未通过").append("\n");
        if (dto.getReviewRemark() != null && !dto.getReviewRemark().isBlank()) {
            sb.append("审核说明: ").append(dto.getReviewRemark()).append("\n");
        }
        sb.append("\n---\n");
        sb.append(approved
                ? "感谢你的投稿，图片已进入图库啦！"
                : "别灰心，欢迎换一张再来投稿~");

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
