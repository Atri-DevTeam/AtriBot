package top.yzljc.atribot.event.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import top.yzljc.atribot.chat.official.thread.ReplyInfo;
import top.yzljc.atribot.event.Event;

/**
 * @Author YZ_Ljc_
 * @ClassName OfficialAtForumReplyCreateEvent
 * @Created_at 2026/10/05
 * @Project AtriMeow
 * @Package top.yzljc.atribot.event.events
 */
@Getter
@AllArgsConstructor
public class OfficialAtForumReplyCreateEvent extends Event {
    private final String guildId;
    private final String channelId;
    private final String authorId;
    private final ReplyInfo replyInfo;
}
