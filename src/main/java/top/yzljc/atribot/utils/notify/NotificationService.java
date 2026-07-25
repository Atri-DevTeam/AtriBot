package top.yzljc.atribot.utils.notify;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.chat.official.C2CChat;
import top.yzljc.atribot.chat.official.GroupChat;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.database.PendingNoticeDTO;
import top.yzljc.atribot.database.repo.PendingNoticeRepository;

import java.sql.Timestamp;

/**
 * 统一通知投递：优先主动消息，不可用或失败时降级为被动队列。
 *
 * <p>投递策略：
 * <ol>
 *   <li>先查主动消息权限（群 {@code is_allowed_active} / 私聊 {@code c2c_push}），
 *       未开放则<b>直接入队</b>，不做无谓的 API 调用；</li>
 *   <li>有权限则尝试主动推送，成功即结束；</li>
 *   <li>主动推送返回 null（含无权限、限频、markdown 未报备等一切失败）则兜底入队。</li>
 * </ol>
 *
 * <p>兜底之所以以返回值而非 {@code OfficialActiveMessageFailEvent} 为准，是因为失败事件只携带
 * {@code (targetId, errCode, message)}，既无法关联到具体某条消息，也会被被动回复触发，
 * 用它做重投会串消息。失败事件仍由 {@code FullMessageEnableCommand} 负责回写权限标记，
 * 下一条通知就会走「无权限直接入队」这条路径。
 *
 * <p>队列由目标下一次与 Bot 交互时排空，见 {@link PendingNoticeDispatcher}。
 *
 * @Author YZ_Ljc_
 * @ClassName NotificationService
 * @Created_at 2026/07/21
 * @Project AtriMeow
 * @Package top.yzljc.atribot.utils.notify
 */
@Slf4j
public class NotificationService {

    public static final String TARGET_C2C = "OFFICIAL_C2C";
    public static final String TARGET_GROUP = "OFFICIAL_GROUP";

    /**
     * 通知一位官方 Bot 私聊用户。
     *
     * @param userOpenId 用户 openid
     * @param markdown   Markdown 正文
     * @param source     业务来源标记，如 image_source
     * @param sourceId   业务对象 id，用于去重
     * @return true 表示已主动送达，false 表示已转入被动队列
     */
    public static boolean notifyUser(String userOpenId, String markdown, String source, String sourceId) {
        if (userOpenId == null || userOpenId.isBlank() || markdown == null || markdown.isBlank()) {
            return false;
        }

        if (!OfficialUsers.isC2CPushEnabled(userOpenId)) {
            log.info("用户未开放主动消息，通知转入被动队列: user={}, source={}", userOpenId, source);
            enqueue(TARGET_C2C, userOpenId, null, markdown, source, sourceId, "c2c_push_disabled");
            return false;
        }

        String messageId = safeSend(() -> C2CChat.sendMessage(userOpenId, TC.md(markdown)));
        if (messageId != null) {
            log.info("主动通知送达: user={}, source={}, messageId={}", userOpenId, source, messageId);
            return true;
        }

        log.warn("主动通知失败，转入被动队列: user={}, source={}", userOpenId, source);
        enqueue(TARGET_C2C, userOpenId, null, markdown, source, sourceId, "active_push_failed");
        return false;
    }

    /**
     * 通知一个官方 Bot 群聊。
     *
     * @param mentionUserId 仅当该用户在群内发言时才补发被动消息；传 null 表示不限定
     */
    public static boolean notifyGroup(String groupOpenId, String mentionUserId, String markdown,
                                      String source, String sourceId) {
        if (groupOpenId == null || groupOpenId.isBlank() || markdown == null || markdown.isBlank()) {
            return false;
        }

        if (!OfficialGroups.isAllowedActiveMessages(groupOpenId)) {
            log.info("群未开放主动消息，通知转入被动队列: group={}, source={}", groupOpenId, source);
            enqueue(TARGET_GROUP, groupOpenId, mentionUserId, markdown, source, sourceId, "group_active_disabled");
            return false;
        }

        String body = mentionUserId != null ? Markdown.at(mentionUserId) + "\n" + markdown : markdown;
        String messageId = safeSend(() -> GroupChat.sendMessage(groupOpenId, TC.md(body)));
        if (messageId != null) {
            log.info("主动通知送达: group={}, source={}, messageId={}", groupOpenId, source, messageId);
            return true;
        }

        log.warn("主动通知失败，转入被动队列: group={}, source={}", groupOpenId, source);
        enqueue(TARGET_GROUP, groupOpenId, mentionUserId, markdown, source, sourceId, "active_push_failed");
        return false;
    }

    /**
     * 按投稿/反馈的原始来源选择通知通道：群内提交的回到群里并 @ 本人，私聊提交的走私聊。
     */
    public static boolean notify(String platform, String userOpenId, String groupOpenId, String markdown,
                                 String source, String sourceId) {
        boolean isGroup = groupOpenId != null && !groupOpenId.isBlank();
        if (isGroup) {
            return notifyGroup(groupOpenId, userOpenId, markdown, source, sourceId);
        }
        return notifyUser(userOpenId, markdown, source, sourceId);
    }

    private static void enqueue(String targetType, String targetId, String mentionUserId, String markdown,
                                String source, String sourceId, String reason) {
        if (source != null && sourceId != null && PendingNoticeRepository.hasPending(source, sourceId)) {
            log.info("该业务对象已有待送达通知，跳过重复入队: source={}, sourceId={}", source, sourceId);
            return;
        }
        PendingNoticeDTO notice = new PendingNoticeDTO();
        notice.setTargetType(targetType);
        notice.setTargetId(targetId);
        notice.setMentionUserId(mentionUserId);
        notice.setContent(markdown);
        notice.setSource(source);
        notice.setSourceId(sourceId);
        notice.setCreateTime(new Timestamp(System.currentTimeMillis()));
        notice.setLastError(reason);
        PendingNoticeRepository.enqueue(notice);
    }

    /**
     * 发送层理论上只返回 null 不抛异常，这里再兜一层，避免个别实现抛出后丢掉整条通知。
     */
    private static String safeSend(Sender sender) {
        try {
            return sender.send();
        } catch (Exception e) {
            log.warn("主动消息发送抛出异常", e);
            return null;
        }
    }

    @FunctionalInterface
    private interface Sender {
        String send();
    }
}
