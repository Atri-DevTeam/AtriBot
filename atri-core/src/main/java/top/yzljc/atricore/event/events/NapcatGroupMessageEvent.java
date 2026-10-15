package top.yzljc.atricore.event.events;

import lombok.Getter;
import top.yzljc.atricore.chat.napcat.GroupMessage;
import top.yzljc.atricore.chat.napcat.impl.MessageSegment;
import top.yzljc.atricore.event.Event;
import top.yzljc.atricore.platform.napcat.NapcatUser;
import top.yzljc.atricore.platform.napcat.NapcatMessage;

import java.util.List;


/**
 * 收到 OneBot 群消息上报时触发
 *
 * @Author YZ_Ljc_
 * @ClassName NapcatGroupMessageEvent
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.event.events
 */
@Getter
public class NapcatGroupMessageEvent extends Event {
    private final NapcatUser user;
    private final NapcatMessage message;
    private final String groupId;
    private final String timestamp;

    public NapcatGroupMessageEvent(NapcatUser user, NapcatMessage message, String groupId, String timestamp) {
        this.user = user;
        this.message = message;
        this.groupId = groupId;
        this.timestamp = timestamp;
    }

    public String sendMessage(String content) {
        return this.user.sendMessage(this.groupId, this.message.getMessageId(), content);
    }

    public String sendMessage(List<MessageSegment> data) {
        return GroupMessage.chatMessage(this.groupId, data);
    }

    public boolean recall() {
        return this.message.recall();
    }
}
