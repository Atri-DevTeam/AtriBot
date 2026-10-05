package top.yzljc.atribot.event.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import top.yzljc.atribot.chat.official.thread.PostInfo;
import top.yzljc.atribot.event.Event;

/**
 * @Author YZ_Ljc_
 * @ClassName OfficialAtForumPostCreateEvent
 * @Created_at 2026/10/05
 * @Project AtriMeow
 * @Package top.yzljc.atribot.event.events
 */
@Getter
@AllArgsConstructor
public class OfficialAtForumPostCreateEvent extends Event {
    private final String guildId;
    private final String channelId;
    private final String authorId;
    private final PostInfo postInfo;
}
