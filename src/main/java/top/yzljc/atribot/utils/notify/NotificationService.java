package top.yzljc.atribot.utils.notify;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atricore.chat.official.C2CChat;
import top.yzljc.atricore.chat.official.GroupChat;
import top.yzljc.atricore.chat.official.Markdown;
import top.yzljc.atricore.chat.official.TC;
import top.yzljc.atricore.chat.official.RT;
import top.yzljc.atribot.database.PendingNoticeDTO;
import top.yzljc.atribot.database.repo.PendingNoticeRepository;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * 统一通知投递：优先主动消息，不可用或失败时降级为被动队列。
 *
 * <p>投递策略：
 * <ol>
 *   <li>先查主动消息权限（群 {@code allow_proactive_msg} / 私聊 {@code c2c_push}），
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
        return notifyUser(userOpenId, markdown == null ? null : TC.md(markdown), source, sourceId);
    }

    private static boolean notifyUser(String userOpenId, Markdown markdown, String source, String sourceId) {
        if (userOpenId == null || userOpenId.isBlank() || markdown == null || markdown.getText().isBlank()) {
            return false;
        }

        if (!OfficialUsers.isC2CPushEnabled(userOpenId)) {
            log.info("用户未开放主动消息，通知转入被动队列: user={}, source={}", userOpenId, source);
            enqueue(TARGET_C2C, userOpenId, null, markdown.getText(), source, sourceId, "c2c_push_disabled");
            return false;
        }

        String messageId = safeSend(() -> C2CChat.sendMessage(userOpenId, markdown));
        if (messageId != null) {
            log.info("主动通知送达: user={}, source={}, messageId={}", userOpenId, source, messageId);
            return true;
        }

        log.warn("主动通知失败，转入被动队列: user={}, source={}", userOpenId, source);
        enqueue(TARGET_C2C, userOpenId, null, markdown.getText(), source, sourceId, "active_push_failed");
        return false;
    }

    /**
     * 通知一个官方 Bot 群聊。
     *
     * @param mentionUserId 仅当该用户在群内发言时才补发被动消息；传 null 表示不限定
     */
    public static boolean notifyGroup(String groupOpenId, String mentionUserId, String markdown,
                                      String source, String sourceId) {
        return notifyGroup(groupOpenId, mentionUserId, markdown == null ? null : TC.md(markdown), source, sourceId);
    }

    private static boolean notifyGroup(String groupOpenId, String mentionUserId, Markdown markdown,
                                      String source, String sourceId) {
        if (groupOpenId == null || groupOpenId.isBlank() || markdown == null || markdown.getText().isBlank()) {
            return false;
        }

        if (!OfficialGroups.allowProactiveMsg(groupOpenId)) {
            log.info("群未开放主动消息，通知转入被动队列: group={}, source={}", groupOpenId, source);
            enqueue(TARGET_GROUP, groupOpenId, mentionUserId, markdown.getText(), source, sourceId, "group_active_disabled");
            return false;
        }

        Markdown body = mention(markdown, mentionUserId);
        String messageId = safeSend(() -> GroupChat.sendMessage(groupOpenId, body));
        if (messageId != null) {
            log.info("主动通知送达: group={}, source={}, messageId={}", groupOpenId, source, messageId);
            return true;
        }

        log.warn("主动通知失败，转入被动队列: group={}, source={}", groupOpenId, source);
        enqueue(TARGET_GROUP, groupOpenId, mentionUserId, markdown.getText(), source, sourceId, "active_push_failed");
        return false;
    }

    /**
     * 按投稿/反馈的原始来源选择通知通道：群内提交的回到群里并 @ 本人，私聊提交的走私聊。
     */
    public static boolean notify(String platform, String userOpenId, String groupOpenId, String markdown,
                                 String source, String sourceId) {
        return notify(platform, userOpenId, groupOpenId, markdown == null ? null : TC.md(markdown), source, sourceId);
    }

    private static boolean notify(String platform, String userOpenId, String groupOpenId, Markdown markdown,
                                  String source, String sourceId) {
        boolean isGroup = groupOpenId != null && !groupOpenId.isBlank();
        if (isGroup) {
            return notifyGroup(groupOpenId, userOpenId, markdown, source, sourceId);
        }
        return notifyUser(userOpenId, markdown, source, sourceId);
    }

    /**
     * 已开放主动消息的群优先主动通知；其余来源在五分钟内直接被动回复，失败后进入被动队列
     * 来源过期或缺失时沿用主动通知流程
     *
     * @param platform 来源平台，仅 QQ 群聊和私聊支持此被动回复方式
     * @param userOpenId 用户 OpenID
     * @param groupOpenId 群 OpenID，私聊传 null
     * @param markdown 完整回复对象；入队后由业务来源记录重建正文和键盘
     * @param source 业务来源
     * @param sourceId 业务记录编号，用于通知去重
     * @param messageId 提交或补充内容的来源消息 ID
     * @param messageTime 来源消息时间
     * @return 已发送返回 true，已入队返回 false
     */
    public static boolean notify(String platform, String userOpenId, String groupOpenId, Markdown markdown,
                                 String source, String sourceId, String messageId, Timestamp messageTime) {
        boolean group = TARGET_GROUP.equals(platform);
        if (group && groupOpenId != null && !groupOpenId.isBlank() && OfficialGroups.allowProactiveMsg(groupOpenId))
            return notifyGroup(groupOpenId, userOpenId, markdown, source, sourceId);
        if (!group && !TARGET_C2C.equals(platform))
            return notify(platform, userOpenId, groupOpenId, markdown, source, sourceId);
        return deliverRecent(messageId, messageTime, System.currentTimeMillis(),
                () -> group
                        ? GroupChat.replyMessage(groupOpenId, RT.message(messageId), userOpenId, markdown)
                        : C2CChat.replyMessage(userOpenId, RT.message(messageId), markdown),
                () -> {
                    if (!enqueue(group ? TARGET_GROUP : TARGET_C2C, group ? groupOpenId : userOpenId,
                            group ? userOpenId : null, markdown.getText(), source, sourceId, "passive_reply_failed"))
                        throw new IllegalStateException("被动回复通知入队失败");
                },
                () -> notify(platform, userOpenId, groupOpenId, markdown, source, sourceId));
    }

    static Markdown mention(Markdown markdown, String userOpenId) {
        return userOpenId == null || userOpenId.isBlank() ? markdown
                : markdown.withText(Markdown.at(userOpenId) + "\n" + markdown.getText());
    }

    static boolean deliverRecent(String messageId, Timestamp messageTime, long now,
                                 Supplier<String> send, Runnable queue, BooleanSupplier fallback) {
        long age = messageTime == null ? -1 : now - messageTime.getTime();
        if (messageId == null || messageId.isBlank() || age < 0 || age >= Duration.ofMinutes(5).toMillis())
            return fallback.getAsBoolean();
        String result = null;
        try {
            result = send.get();
        } catch (Exception e) {
            log.warn("工单被动回复失败，准备入队补发", e);
        }
        if (result != null && !result.isBlank()) return true;
        queue.run();
        return false;
    }

    private static boolean enqueue(String targetType, String targetId, String mentionUserId, String markdown,
                                String source, String sourceId, String reason) {
        if (source != null && sourceId != null && PendingNoticeRepository.hasPending(source, sourceId)) {
            log.info("该业务对象已有待送达通知，跳过重复入队: source={}, sourceId={}", source, sourceId);
            return true;
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
        return PendingNoticeRepository.enqueue(notice) != null;
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
