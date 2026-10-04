package top.yzljc.atribot.event.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import top.yzljc.atribot.chat.official.GuildChannelChat;
import top.yzljc.atribot.chat.official.thread.GuildThread;
import top.yzljc.atribot.chat.official.thread.ThreadInfo;
import top.yzljc.atribot.event.Event;

/**
 * @Author YZ_Ljc_
 * @ClassName OfficialAtForumThreadCreateEvent
 * @Created_at 2026/10/04
 * @Project AtriMeow
 * @Package top.yzljc.atribot.event.events
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
