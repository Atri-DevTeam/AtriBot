package top.yzljc.atribot.function.general.impl;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.OfficialC2CSendFailEvent;
import top.yzljc.atribot.event.events.OfficialGroupSendFailEvent;
import top.yzljc.atribot.event.impl.ErrorCode;
import top.yzljc.atribot.service.request.HttpService;
import top.yzljc.atribot.service.runtime.ThreadManager;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 把 QQ 的「富媒体信息转存失败」翻译成一次切线上报。
 *
 * <p>出口被服务商限速时，我们自己只能观测到"图推得慢"，而 QQ 那边是直接判死：
 * 它拉图有 3 秒硬超时，超了就回 {@link ErrorCode#MEDIA_TRANSFER_FAILED}(40034004)。
 * 这个信号比本机攒慢样本更早也更硬 —— 等我们自己攒够，用户已经好几条消息看不到图了。
 * 所以收到就立刻通知生图端切到 OSS。
 *
 * <p>注意别把它当成万能错误处理：40034004 也可能是图片本身有问题（尺寸、格式）。
 * 所以这里只做"上报"，切不切、切多久由生图端自己按冷却期决定，本端不擅自改路线。
 *
 * @Author Claude Opus 5
 * @ClassName DeliveryFailureReporter
 * @Created_at 2026/08/01
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.general.impl
 */
@Slf4j
public class DeliveryFailureReporter implements Listener {

    /** 上报节流：一次限速会连着炸出一串失败，没必要每条都打一次 */
    private static final long REPORT_INTERVAL_MS = 30_000;

    private static final AtomicLong lastReportAt = new AtomicLong();

    @EventHandler
    public void onGroupSendFail(OfficialGroupSendFailEvent event) {
        maybeReport(event.getErrorCode(), "群 " + event.getGroupOpenId());
    }

    @EventHandler
    public void onC2CSendFail(OfficialC2CSendFailEvent event) {
        maybeReport(event.getErrorCode(), "单聊 " + event.getUserId());
    }

    private void maybeReport(int errorCode, String scene) {
        if (errorCode != ErrorCode.MEDIA_TRANSFER_FAILED.getErrorCode()) {
            return;
        }

        long now = System.currentTimeMillis();
        long last = lastReportAt.get();
        if (now - last < REPORT_INTERVAL_MS || !lastReportAt.compareAndSet(last, now)) {
            return;
        }

        log.warn("QQ 拉图失败({})，通知生图端切换下发路线: {}",
                ErrorCode.MEDIA_TRANSFER_FAILED.getMessage(), scene);
        ThreadManager.execute(() -> report(scene));
    }

    private void report(String scene) {
        Map<String, Object> body = Map.of(
                "reason", ErrorCode.MEDIA_TRANSFER_FAILED.getErrorCode() + " "
                        + ErrorCode.MEDIA_TRANSFER_FAILED.getMessage() + "（" + scene + "）",
                "source", "atribot");
        String bearer = "Bearer " + Config.getInstance().getAtribotKeySecret();

        for (String url : ResourcesProperties.DELIVERY_REPORT) {
            try {
                var resp = HttpService.postJson(url, body, "Authorization", bearer);
                if (resp == null || resp.path("status").asInt() != 200) {
                    log.warn("切线上报未被接受: url={}, resp={}", url, resp);
                    continue;
                }
                log.info("切线上报成功: url={}, way={}", url, resp.path("data").path("way").asText());
            } catch (Exception e) {
                log.warn("切线上报异常: url={}", url, e);
            }
        }
    }
}
