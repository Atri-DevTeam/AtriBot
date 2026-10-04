package top.yzljc.atribot.event.events;

import lombok.Getter;
import lombok.Setter;
import top.yzljc.atribot.chat.official.C2CChat;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.RT;
import top.yzljc.atribot.event.Event;
import top.yzljc.atribot.platform.qq.QQUser;
import top.yzljc.atribot.platform.qq.QQMessage;

import java.util.List;
import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName OfficePrivateChatEvent
 * @Created_at 2026/05/06
 * @Project AtriBot
 * @Package top.yzljc.qqbot.event.impl
 * @Description 本事件中所有回复消息的方法均为被动
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
