package top.yzljc.atricore.event.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import top.yzljc.atricore.chat.official.thread.PostInfo;
import top.yzljc.atricore.event.Event;

/**
 * 收到 {@code AT_FORUM_POST_CREATE}，即创建帖子评论并 @ 机器人时触发
 *
 * @Author YZ_Ljc_
 * @ClassName OfficialAtForumPostCreateEvent
 * @Created_at 2026/10/05
 * @Project AtriMeow
 * @Package top.yzljc.atricore.event.events
 */
@Getter
@AllArgsConstructor
public class OfficialAtForumPostCreateEvent extends Event {
    private final String guildId;
    private final String channelId;
    private final String authorId;
    private final PostInfo postInfo;
}
