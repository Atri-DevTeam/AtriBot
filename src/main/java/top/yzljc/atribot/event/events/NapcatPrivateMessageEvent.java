package top.yzljc.atribot.event.events;

import lombok.Getter;
import top.yzljc.atribot.event.Event;
import top.yzljc.atribot.platform.Message;
import top.yzljc.atribot.platform.User;

@Getter
public class NapcatPrivateMessageEvent extends Event {
    private final User user;
    private final Message message;
    private final String timestamp;

    public NapcatPrivateMessageEvent(User user, Message message, String timestamp) {
        this.user = user;
        this.message = message;
        this.timestamp = timestamp;
    }
}
