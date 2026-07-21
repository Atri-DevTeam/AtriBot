package top.yzljc.atribot.utils.notify;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.database.PendingNoticeDTO;
import top.yzljc.atribot.database.repo.PendingNoticeRepository;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.OfficialC2CMessageCreateEvent;
import top.yzljc.atribot.event.events.OfficialGroupAtMessageCreateEvent;
import top.yzljc.atribot.event.events.OfficialGroupMessageCreateEvent;

/**
 * 被动队列排空器：目标下一次与 Bot 交互时补发欠他的通知。
 *
 * <p>每次交互只补发一条，避免一次性刷屏；剩余的通知会在后续交互里继续补发。
 *
 * @Author YZ_Ljc_
 * @ClassName PendingNoticeDispatcher
 * @Created_at 2026/07/21
 * @Project AtriMeow
 * @Package top.yzljc.atribot.utils.notify
 */
@Slf4j
public class PendingNoticeDispatcher implements Listener {

    @EventHandler
    public void onC2CMessage(OfficialC2CMessageCreateEvent event) {
        if (event.getUser().isBot()) return;
        PendingNoticeDTO notice = PendingNoticeRepository.pollForC2C(event.getUser().getUserId());
        if (notice == null) return;
        deliver(notice, () -> event.sendMessage(TC.md(notice.getContent())));
    }

    @EventHandler
    public void onGroupAtMessage(OfficialGroupAtMessageCreateEvent event) {
        if (event.getUser().isBot()) return;
        deliverToGroup(event.getGroupId(), event.getUser().getUserId(), event::sendMessage);
    }

    @EventHandler
    public void onGroupMessage(OfficialGroupMessageCreateEvent event) {
        if (event.getUser().isBot()) return;
        deliverToGroup(event.getGroupId(), event.getUser().getUserId(), event::sendMessage);
    }

    private void deliverToGroup(String groupId, String speakerUserId, MarkdownSink sink) {
        PendingNoticeDTO notice = PendingNoticeRepository.pollForGroup(groupId, speakerUserId);
        if (notice == null) return;

        // 群内补发时把本人 @ 出来，否则用户在刷屏的群里根本注意不到
        String body = notice.getMentionUserId() != null
                ? Markdown.at(notice.getMentionUserId()) + "\n" + notice.getContent()
                : notice.getContent();
        deliver(notice, () -> sink.send(TC.md(body)));
    }

    private void deliver(PendingNoticeDTO notice, Delivery delivery) {
        try {
            String messageId = delivery.run();
            if (messageId != null) {
                PendingNoticeRepository.markDelivered(notice.getId());
                log.info("被动补发通知成功: id={}, target={}, source={}",
                        notice.getId(), notice.getTargetId(), notice.getSource());
            } else {
                PendingNoticeRepository.markAttemptFailed(notice.getId(), "passive_send_returned_null");
                log.warn("被动补发通知失败，保留在队列中: id={}, target={}", notice.getId(), notice.getTargetId());
            }
        } catch (Exception e) {
            PendingNoticeRepository.markAttemptFailed(notice.getId(), e.getMessage());
            log.warn("被动补发通知异常，保留在队列中: id={}", notice.getId(), e);
        }
    }

    @FunctionalInterface
    private interface Delivery {
        String run();
    }

    @FunctionalInterface
    private interface MarkdownSink {
        String send(Markdown markdown);
    }
}
