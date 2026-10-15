package top.yzljc.atricore.event.events;

import lombok.Getter;
import lombok.Setter;
import top.yzljc.atricore.chat.official.C2CChat;
import top.yzljc.atricore.chat.official.Markdown;
import top.yzljc.atricore.chat.official.RT;
import top.yzljc.atricore.event.Event;
import top.yzljc.atricore.platform.qq.QQUser;
import top.yzljc.atricore.platform.qq.QQMessage;

import java.util.List;

/**
 * 收到私聊消息 {@code C2C_MESSAGE_CREATE} 时触发
 *
 * @Author YZ_Ljc_
 * @ClassName OfficialC2CMessageCreateEvent
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.event.events
 */
@Getter
public class OfficialC2CMessageCreateEvent extends Event {
    private final QQUser user;
    private final QQMessage message;
    private final String timestamp;
    @Setter
    private SwitchButtons switchButtons;

    public OfficialC2CMessageCreateEvent(QQUser user, QQMessage message, String timestamp) {
        this.user = user;
        this.message = message;
        this.timestamp = timestamp;
    }

    public String sendMessage(String content) {
        return this.user.sendMessage(this.message.getMessageId(), content);
    }

    public String sendMessage(Markdown markdown) {
        return this.user.sendMessage(this.message.getMessageId(), markdown);
    }

    public String sendMessage(Markdown markdown, Object keyboard) {
        return this.user.sendMessage(this.message.getMessageId(), markdown, keyboard);
    }

    public String sendStreamMarkdownMessageD(List<Markdown> markdownDeltas) {
        return C2CChat.replyStreamDeltas(this.user.getUserId(), RT.message(this.message.getMessageId()), markdownDeltas);
    }

    public String sendStreamTextMessageD(List<String> textDeltas) {
        return C2CChat.replyTextStreamDeltas(this.user.getUserId(), RT.message(this.message.getMessageId()), textDeltas);
    }

    public boolean shouldIgnore() {
        return this.user.isBlocked();
    }

    public record SwitchButtons(List<SwitchButton> switchButtons) {
        public record SwitchButton(String k, boolean v) {}

        public boolean isEnabled(String k) {
            for (SwitchButton button : switchButtons) {
                if (button.k().equals(k)) {
                    return button.v();
                }
            }
            return false;
        }
    }
}
