package top.yzljc.atricore.event.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import top.yzljc.atricore.chat.official.thread.ReplyInfo;
import top.yzljc.atricore.event.Event;

/**
 * 收到 {@code AT_FORUM_REPLY_CREATE}，即创建评论回复并 @ 机器人时触发
 *
 * @Author YZ_Ljc_
 * @ClassName OfficialAtForumReplyCreateEvent
 * @Created_at 2026/10/05
 * @Project AtriMeow
 * @Package top.yzljc.atricore.event.events
 */
@Getter
@AllArgsConstructor
public class OfficialAtForumReplyCreateEvent extends Event {
    private final String guildId;
    private final String channelId;
    private final String authorId;
    private final ReplyInfo replyInfo;
}
