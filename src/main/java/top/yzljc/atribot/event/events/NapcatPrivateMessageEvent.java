package top.yzljc.atribot.event.events;

import lombok.Getter;
import top.yzljc.atribot.event.Event;
import top.yzljc.atribot.platform.Message;
import top.yzljc.atribot.platform.User;
import top.yzljc.atribot.platform.napcat.NapcatMessage;

@Getter
public class NapcatPrivateMessageEvent extends Event {
    private final User user;
    private final NapcatMessage message;
    private final String timestamp;

    public NapcatPrivateMessageEvent(User user, NapcatMessage message, String timestamp) {
        this.user = user;
        this.message = message;
        this.timestamp = timestamp;
    }
}
