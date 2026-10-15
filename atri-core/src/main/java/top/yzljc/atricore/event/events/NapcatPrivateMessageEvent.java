package top.yzljc.atricore.event.events;

import lombok.Getter;
import top.yzljc.atricore.event.Event;
import top.yzljc.atricore.platform.napcat.NapcatUser;
import top.yzljc.atricore.platform.napcat.NapcatMessage;

/**
 * 收到 OneBot 私聊消息上报时触发
 *
 * @Author YZ_Ljc_
 * @ClassName NapcatPrivateMessageEvent
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.event.events
 */
@Getter
public class NapcatPrivateMessageEvent extends Event {
    private final NapcatUser user;
    private final NapcatMessage message;
    private final String timestamp;

    public NapcatPrivateMessageEvent(NapcatUser user, NapcatMessage message, String timestamp) {
        this.user = user;
        this.message = message;
        this.timestamp = timestamp;
    }
}
