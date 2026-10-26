package top.yzljc.atribot.utils.notify;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atricore.chat.official.Markdown;
import top.yzljc.atricore.chat.official.TC;
import top.yzljc.atribot.database.PendingNoticeDTO;
import top.yzljc.atribot.database.repo.PendingNoticeRepository;
import top.yzljc.atribot.database.repo.FeedbackRepository;
import top.yzljc.atribot.function.command.FeedbackCommand;
import top.yzljc.atricore.event.EventHandler;
import top.yzljc.atricore.event.Listener;
import top.yzljc.atricore.event.events.OfficialC2CMessageCreateEvent;
import top.yzljc.atricore.event.events.OfficialGroupAtMessageCreateEvent;
import top.yzljc.atricore.event.events.OfficialGroupMessageCreateEvent;
import top.yzljc.atricore.event.events.OfficialGroupMemberAddEvent;
import top.yzljc.atribot.auth.official.OfficialGroups;

/**
 * 被动队列排空器：目标下一次与 Bot 交互时补发欠他的通知。
 *
 * <p>普通通知每次交互只补发一条；WebUI 群留言由独立队列依次发送，失败时保留剩余留言。
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
    public void onGroupMemberAdd(OfficialGroupMemberAddEvent event) {
        if (OfficialGroups.isGroupBlacklisted(event.getGroupOpenId())) return;
        GroupLeaveMessageService.deliver(event.getGroupOpenId(), true, markdown -> event.sendMessage(markdown, false));
    }

    @EventHandler
    public void onC2CMessage(OfficialC2CMessageCreateEvent event) {
        if (event.getUser().isBot()) return;
        PendingNoticeDTO notice = PendingNoticeRepository.pollForC2C(event.getUser().getUserId());
        if (notice == null) return;
        deliver(notice, event::sendMessage);
    }

    @EventHandler
    public void onGroupAtMessage(OfficialGroupAtMessageCreateEvent event) {
        if (event.getUser().isBot()) return;
        if (!event.shouldIgnore()) GroupLeaveMessageService.deliver(event.getGroupId(),
                markdown -> event.getUser().sendMessage(event.getGroupId(), event.getMessage().getMessageId(), markdown, false));
        deliverToGroup(event.getGroupId(), event.getUser().getUserId(), markdown -> event.sendMessage(markdown, false));
    }

    @EventHandler
    public void onGroupMessage(OfficialGroupMessageCreateEvent event) {
        if (event.getUser().isBot()) return;
        if (!event.shouldIgnore()) GroupLeaveMessageService.deliver(event.getGroupId(),
                markdown -> event.getUser().sendMessage(event.getGroupId(), event.getMessage().getMessageId(), markdown, false));
        deliverToGroup(event.getGroupId(), event.getUser().getUserId(), markdown -> event.sendMessage(markdown, false));
    }

    private void deliverToGroup(String groupId, String speakerUserId, MarkdownSink sink) {
        PendingNoticeDTO notice = PendingNoticeRepository.pollForGroup(groupId, speakerUserId);
        if (notice == null) return;
        deliver(notice, markdown -> sink.send(NotificationService.mention(markdown, notice.getMentionUserId())));
    }

    private Markdown prepareMessage(PendingNoticeDTO notice) {
        if (!"feedback".equals(notice.getSource())) return TC.md(notice.getContent());
        try {
            Markdown markdown = FeedbackCommand.queuedReplyMessage(notice.getSourceId());
            if (markdown != null) return markdown;
            PendingNoticeRepository.markAttemptFailed(notice.getId(), "feedback_reply_not_found");
            log.warn("待补发工单回复不存在，通知: {}", notice.getId());
        } catch (Exception e) {
            PendingNoticeRepository.markAttemptFailed(notice.getId(), "feedback_reply_load_failed");
            log.warn("读取待补发工单回复失败，通知: {}", notice.getId(), e);
        }
        return null;
    }

    private void deliver(PendingNoticeDTO notice, MarkdownSink sink) {
        var lock = "feedback".equals(notice.getSource()) && notice.getSourceId() != null
                ? FeedbackRepository.deliveryLock(notice.getSourceId().split(":", 2)[0]) : null;
        if (lock != null && !lock.tryLock()) return;
        try {
            if (lock != null && !PendingNoticeRepository.isPending(notice.getId())) return;
            Markdown markdown = prepareMessage(notice);
            if (markdown == null) return;
            String messageId = sink.send(markdown);
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
        } finally { if (lock != null) lock.unlock(); }
    }

    @FunctionalInterface
    private interface MarkdownSink {
        String send(Markdown markdown);
    }
}
