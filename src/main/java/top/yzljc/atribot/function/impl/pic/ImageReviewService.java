package top.yzljc.atribot.function.impl.pic;

import lombok.extern.slf4j.Slf4j;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import top.yzljc.atribot.database.repo.PicRepository;
import top.yzljc.atricore.chat.official.Markdown;
import top.yzljc.atricore.chat.official.TC;
import top.yzljc.atribot.database.repo.LootRepository;
import top.yzljc.atribot.database.ImageSourceDTO;
import top.yzljc.atricore.service.runtime.ThreadManager;
import top.yzljc.atribot.utils.FormatTools;
import top.yzljc.atribot.utils.notify.NotificationService;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

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
    private static final Cache<String, SourceMessage> SOURCE_MESSAGES = CacheBuilder.newBuilder()
            .maximumSize(4096).expireAfterWrite(Duration.ofMinutes(5)).build();
    private static final Object[] REVIEW_LOCKS = java.util.stream.IntStream.range(0, 64)
            .mapToObj(i -> new Object()).toArray();

    private record SourceMessage(String id, Timestamp time) {
    }

    /**
     * 临时保留投稿消息的回复凭据，过期或重启后由通知队列补发
     */
    public static void rememberSource(String imageUuid, String messageId, String messageTime) {
        if (imageUuid == null || messageId == null || messageId.isBlank() || messageTime == null || messageTime.isBlank())
            return;
        try {
            SOURCE_MESSAGES.put(imageUuid, new SourceMessage(messageId, Timestamp.from(OffsetDateTime.parse(messageTime).toInstant())));
        } catch (DateTimeParseException ignored) {
            // 无法确认来源时间时不尝试直接回复
        }
    }

    /**
     * 审核一条投稿
     *
     * @param status 目标状态；置回 {@link ImageReviewStatus#PENDING} 表示撤销审核，不发通知
     * @return 是否成功
     */
    public static boolean review(String id, ImageReviewStatus status, String reviewer, String remark) {
        return review(id, status, reviewer, remark, false);
    }

    /**
     * 审核一条投稿，可在拒绝时跳过用户通知
     *
     * @param silent 是否静默拒绝，仅适用于 DENIED 状态
     * @return 是否成功，静默参数用于其他状态时返回 false
     */
    public static boolean review(String id, ImageReviewStatus status, String reviewer, String remark, boolean silent) {
        if (silent && status != ImageReviewStatus.DENIED) return false;
        synchronized (reviewLock(id)) {
            return reviewLocked(id, status, reviewer, remark, silent, false);
        }
    }

    /**
     * 初审只处理仍待审核的投稿，避免覆盖人工审核结果
     */
    public static boolean rejectIfPending(String id, String remark) {
        synchronized (reviewLock(id)) {
            return reviewLocked(id, ImageReviewStatus.DENIED, "AI 初审", remark, false, true);
        }
    }

    private static Object reviewLock(String id) {
        return REVIEW_LOCKS[Math.floorMod(java.util.Objects.hashCode(id), REVIEW_LOCKS.length)];
    }

    private static boolean reviewLocked(String id, ImageReviewStatus status, String reviewer, String remark,
                                        boolean silent, boolean pendingOnly) {
        ImageSourceDTO dto = PicRepository.findById(id);
        if (dto == null) {
            log.warn("审核目标不存在: id={}", id);
            return false;
        }

        ImageReviewStatus oldStatus = ImageReviewStatus.of(dto.getReviewStatus());
        if (pendingOnly && oldStatus != ImageReviewStatus.PENDING) return false;

        // 规则：审核动作 = 远端把图片移动到与新状态对应的目录，让 /public/acg 只命中过审图。
        // PENDING -> pending/，REVIEWED -> 主目录，DENIED -> reject/。
        // 状态没变（如重复点同一种审核）不用打扰远端；远端移动失败则本次审核不生效。
        if (status != oldStatus) {
            ImageSourceClient.RemoteResult moveResult = ImageSourceClient.setStatus(dto, status);
            if (!moveResult.ok()) {
                log.warn("审核状态变更时远端图片未归位，审核不生效: id={}, status={}, reason={}",
                        id, status, moveResult.message());
                return false;
            }
        }

        if (!PicRepository.review(id, status, reviewer, remark, silent)) {
            return false;
        }

        dto.setReviewStatus(status.name());
        dto.setReviewer(reviewer);
        dto.setReviewRemark(remark);
        dto.setReviewTime(new Timestamp(System.currentTimeMillis()));

        // 撤销审核和静默拒绝均不提交用户通知
        if (status == ImageReviewStatus.PENDING || silent) {
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
            SourceMessage message = dto.getImageUuid() == null ? null : SOURCE_MESSAGES.getIfPresent(dto.getImageUuid());
            NotificationService.notify(dto.getPlatform(), dto.getUploaderId(), dto.getGroupId(), TC.md(markdown),
                    SOURCE, dto.getId(), message == null ? null : message.id(), message == null ? null : message.time());

            if (ImageReviewStatus.of(dto.getReviewStatus()).equals(ImageReviewStatus.REVIEWED)) {
                LootRepository.addCoins(dto.getUploaderId(), 100, "image_upload_reward");
                log.info("已发放图源投稿奖励: userId={}, amount=100, way=image_upload_reward", dto.getUploaderId());
            }

            // 入队即视为已交办，后续送达由队列负责，避免审核页反复重推
            PicRepository.markNotified(dto.getId());
        } catch (Exception e) {
            log.error("推送图源审核结果失败: id={}", dto.getId(), e);
        }
    }

    private static String buildResultMarkdown(ImageSourceDTO dto) {
        boolean approved = ImageReviewStatus.of(dto.getReviewStatus()) == ImageReviewStatus.REVIEWED;

        StringBuilder sb = new StringBuilder();
        sb.append(approved ? "**你的图源投稿已通过审核**" : "**你的图源投稿未通过审核**").append("\n");
        sb.append("投稿编号: ").append(shortId(dto.getId())).append("\n");
        sb.append("投稿时间: ").append(FormatTools.formatTimestamp(dto.getCreateTime(), "-")).append("\n");
        sb.append("审核时间: ").append(FormatTools.formatTimestamp(dto.getReviewTime(), "-")).append("\n");
        if (dto.getReviewRemark() != null && !dto.getReviewRemark().isBlank()) {
            sb.append("审核说明: ").append(dto.getReviewRemark()).append("\n");
        }
        sb.append("> ").append(Markdown.enterCommand("/submit ", "我也要投稿"));

        return sb.toString();
    }

    private static String shortId(String id) {
        if (id == null) return "-";
        return id.length() <= 8 ? id : id.substring(0, 8);
    }
}
