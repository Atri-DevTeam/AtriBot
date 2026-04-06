package top.yzljc.qqbot.event;

import lombok.Setter;
import top.yzljc.qqbot.botservice.message.MessageSender;
import top.yzljc.qqbot.chat.MessageSegment;
import top.yzljc.qqbot.chat.SendPrivateMessage;
import top.yzljc.qqbot.chat.impl.MessageUtils;

import java.util.ArrayList;
import java.util.List;

public class Sender {
    private final long userId;
    private final String nickname;
    private final String card;
    private final String role;
    @Setter
    private long replyGroupId = -1;
    @Setter
    private long replyMessageId = -1;

    public Sender(long userId, String nickname, String card, String role) {
        this.userId = userId;
        this.nickname = nickname;
        this.card = card;
        this.role = role;
    }

    public void replay(String message) {
        reply(message, false);
    }

    public void reply(String message, boolean at) {
        if (replyGroupId != -1 && replyMessageId != -1) {
            MessageUtils.replyMessage(this.userId, this.replyGroupId, replyMessageId, at, message);
        }
        if (replyGroupId == -1 && replyMessageId != -1) {
            MessageUtils.replyMessage(this.userId, replyMessageId, replyMessageId, at, message);
        }
    }

    public void sendMessage(String message) {
        SendPrivateMessage.singleTextMessage(this.userId, message);
    }

    public long userId() {
        return userId;
    }

    public String nickname() {
        return nickname;
    }

    public String card() {
        return card;
    }

    public String role() {
        return role;
    }
}
