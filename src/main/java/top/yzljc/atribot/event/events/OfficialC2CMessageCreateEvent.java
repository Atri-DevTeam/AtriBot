package top.yzljc.atribot.event.events;

import lombok.Getter;
import top.yzljc.atribot.chat.official.C2CChat;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.event.Event;
import top.yzljc.atribot.platform.User;
import top.yzljc.atribot.platform.official.OfficialMessage;

import java.util.List;

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
    private final User user;
    private final OfficialMessage message;
    private final String timestamp;

    public OfficialC2CMessageCreateEvent(User user, OfficialMessage message, String timestamp) {
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
        return C2CChat.replyStreamDeltas(this.user.getUserId(), this.message.getMessageId(), markdownDeltas);
    }

    public String sendStreamTextMessageD(List<String> textDeltas) {
        return C2CChat.replyTextStreamDeltas(this.user.getUserId(), this.message.getMessageId(), textDeltas);
    }

    public boolean shouldIgnore() {
        return this.user.isBlocked();
    }
}
