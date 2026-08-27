package top.yzljc.atribot.platform.qq;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import top.yzljc.atribot.chat.official.C2CChat;
import top.yzljc.atribot.chat.official.GroupChat;
import top.yzljc.atribot.event.EventType;
import top.yzljc.atribot.function.command.SignCommand;
import top.yzljc.atribot.platform.Message;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.Recallable;
import top.yzljc.atribot.platform.User;

import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName OfficialMessage
 * @Created_at 2026/06/18
 * @Project AtriMeow
 * @Package top.yzljc.atribot.platform.official
 */
@Getter
public class QQMessage extends Message implements Recallable {
    private final int type;
    private final String refIdx;
    private final JsonNode attachments;
    private final JsonNode ark;
    private final JsonNode reference;
    private final EventType messageEventType;

    public QQMessage(Platform platform, String messageId, String content, String timestamp, List<User> mentionedUsers, int type, String refIdx, JsonNode attachments, JsonNode ark, JsonNode reference, EventType messageEventType) {
        super(platform, messageId, content, timestamp, mentionedUsers);
        this.type = type;
        this.refIdx = refIdx;
        this.attachments = attachments;
        this.ark = ark;
        this.reference = reference;
        this.messageEventType = messageEventType;
    }

    public boolean isCommand() {
        var content = super.getContent().trim();
        return content.startsWith("/") || SignCommand.isMatch(content) || content.equals("指令帮助") || content.equals("反馈与建议");
    }

    @Override
    public boolean recall(String id, String messageId) {
        if (this.getPlatform().equals(Platform.OFFICIAL_C2C)) {
            return C2CChat.recallMessage(id, messageId);
        } else {
            return GroupChat.recallMessage(id, messageId);
        }
    }
}