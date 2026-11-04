package top.yzljc.atribot.function.impl.pic;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.database.ImageSourceDTO;
import top.yzljc.atribot.database.repo.PicRepository;
import top.yzljc.atribot.service.ai.AiProvider;
import top.yzljc.atricore.service.runtime.ThreadManager;

import java.time.Instant;

/**
 * 单工作任务顺序初审，待处理记录保存在投稿表，重启后继续处理
 */
@Slf4j
public final class ImageAiReviewService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static boolean running;

    public static synchronized boolean enqueue(String id) {
        if (!ImageAiReviewSettings.current().enabled()) return false;
        boolean queued = PicRepository.queueAiReview(id);
        start();
        return queued;
    }

    /**
     * 启动时或启用初审后恢复尚未处理的任务，不扫描历史投稿进行自动补审
     */
    public static synchronized void start() {
        if (running || !ImageAiReviewSettings.current().enabled()) return;
        running = true;
        try {
            ThreadManager.execute(ImageAiReviewService::drain);
        } catch (RuntimeException e) {
            running = false;
            log.warn("图源初审任务启动失败", e);
        }
    }

    private static void drain() {
        try {
            while (true) {
                ImageSourceDTO image;
                synchronized (ImageAiReviewService.class) {
                    image = ImageAiReviewSettings.current().enabled() ? PicRepository.nextAiReview() : null;
                    if (image == null) {
                        running = false;
                        return;
                    }
                }
                var config = Config.getInstance();
                var properties = config.getAiBotProperties(AiProvider.DEEPSEEK_IMAGE);
                var result = MAPPER.createObjectNode();
                result.put("model", properties.getModel());
                ImageAiReviewClient.Verdict verdict = null;
                try {
                    verdict = new ImageAiReviewClient().review(properties, ImageAiReviewSettings.current().prompt(), ImageSourceClient.viewUrl(image));
                    result.put("status", verdict.verdict());
                    result.put("reason", verdict.reason());
                    result.put("category", verdict.category());
                    log.info("图源初审完成: id={}, result={}", image.getId(), verdict.verdict());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    result.put("status", "FAILED");
                    result.put("reason", "初审失败，可重试或直接人工审核");
                    log.warn("图源初审失败: id={}, error={}", image.getId(), e.getClass().getSimpleName());
                }
                result.put("reviewedAt", Instant.now().toString());
                if (verdict != null) {
                    String reason = ImageAiReviewSettings.current().rejectionReason(verdict);
                    if (reason != null) {
                        try {
                            ImageReviewService.rejectIfPending(image.getId(), reason);
                        } catch (Exception e) {
                            log.warn("图源自动拒绝失败，保留人工审核: id={}", image.getId(), e);
                        }
                    }
                }
                PicRepository.completeAiReview(image.getId(), result);
                if (Thread.currentThread().isInterrupted()) break;
            }
        } catch (Exception e) {
            log.warn("图源初审任务中断，未完成记录保留待处理状态", e);
        }
        synchronized (ImageAiReviewService.class) {
            running = false;
        }
    }
}
