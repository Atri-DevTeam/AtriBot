package top.yzljc.qqbot.event.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import top.yzljc.qqbot.event.Event;

/**
 * @Author YZ_Ljc_
 * @ClassName OfficePrivateChatEvent
 * @Created_at 2026/05/06
 * @Project AtriBot
 * @Package top.yzljc.qqbot.event.impl
 */
@Getter
@AllArgsConstructor
public class OfficialPrivateChatEvent extends Event {
    private final String msgId;
    private final String content;
    private final long timestamp;
    private final String OpenId;
    private final Object attachments;
}