package top.yzljc.atribot.platform.qq;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import top.yzljc.atribot.chat.official.C2CChat;
import top.yzljc.atribot.chat.official.GroupChat;
import top.yzljc.atribot.command.CommandManager;
import top.yzljc.atribot.event.EventType;
import top.yzljc.atribot.function.command.SignCommand;
import top.yzljc.atribot.platform.Message;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.Recallable;
import top.yzljc.atribot.platform.User;

import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName QQMessage
 * @Created_at 2026/06/18
 * @Project AtriMeow
 * @Package top.yzljc.atribot.platform.qq
 */
@Getter
public class QQMessage extends Message implements Recallable {
    /**
     * 群聊为群 OpenID，C2C 为会话用户 OpenID。
     */
    private final String conversationId;
    private final JsonNode raw;
    private final int type;
    private final String refIdx;
    private final JsonNode attachments;
    private final JsonNode ark;
    private final MessageReference reference;
    private final EventType messageEventType;

    public QQMessage(Platform platform, String messageId, String content, String timestamp, List<User> mentionedUsers, int type, String refIdx, JsonNode attachments, JsonNode ark, MessageReference reference, EventType messageEventType, String conversationId, JsonNode raw) {
        super(platform, messageId, content, timestamp, mentionedUsers);
        if (platform != Platform.OFFICIAL_GROUP && platform != Platform.OFFICIAL_C2C) {
            throw new IllegalArgumentException("QQMessage 仅支持 QQ 群聊和 C2C");
        }
        if (conversationId == null || conversationId.isBlank()) {
            throw new IllegalArgumentException("消息会话 OpenID 不能为空");
        }
        this.conversationId = conversationId;
        this.raw = raw;
        this.type = type;
        this.refIdx = refIdx;
        this.attachments = attachments;
        this.ark = ark;
        this.reference = reference;
        this.messageEventType = messageEventType;
    }

    /**
     * 判断消息是否为框架指令输入
     *
     * @return 文本符合指令输入规则或命中签到词时返回 true
     */
    public boolean isCommand() {
        String content = getContent();
        return content != null && (CommandManager.isCommand(content) || SignCommand.isMatch(content.trim()));
    }

    @Override
    public boolean recall() {
        return getPlatform() == Platform.OFFICIAL_C2C
                ? C2CChat.recallMessage(conversationId, getMessageId())
                : GroupChat.recallMessage(conversationId, getMessageId());
    }
}
