package top.yzljc.atricore.event.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import top.yzljc.atricore.chat.official.GuildChannelChat;
import top.yzljc.atricore.chat.official.thread.GuildThread;
import top.yzljc.atricore.chat.official.thread.ThreadInfo;
import top.yzljc.atricore.event.Event;

/**
 * 收到 {@code AT_FORUM_THREAD_CREATE}，即创建帖子并 @ 机器人时触发
 *
 * @Author YZ_Ljc_
 * @ClassName OfficialAtForumThreadCreateEvent
 * @Created_at 2026/10/04
 * @Project AtriMeow
 * @Package top.yzljc.atricore.event.events
 */
@Getter
@AllArgsConstructor
public class OfficialAtForumThreadCreateEvent extends Event {
    private final String guildId;
    private final String channelId;
    private final String authorId;
    private final ThreadInfo threadInfo;

    public String createThread(GuildThread thread) {
        return GuildChannelChat.createThread(this.channelId, thread);
    }
}
