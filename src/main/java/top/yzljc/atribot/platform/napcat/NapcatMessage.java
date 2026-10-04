package top.yzljc.atribot.platform.napcat;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.chat.napcat.PrivateMessage;
import top.yzljc.atribot.chat.napcat.impl.MessageSegment;
import top.yzljc.atribot.platform.Message;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.Recallable;
import top.yzljc.atribot.platform.User;

import java.util.LinkedList;
import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName NapcatMessage
 * @Created_at 2026/06/18
 * @Project AtriMeow
 * @Package top.yzljc.atribot.platform.napcat
 */
@Getter
public class NapcatMessage extends Message implements Recallable {
    private final JsonNode raw;
    private final JsonNode attachments;
    private final LinkedList<MessageSegment> segments;

    public NapcatMessage(Platform platform, String messageId, String content, String timestamp, List<User> mentionedUsers, JsonNode attachments, LinkedList<MessageSegment> segments, JsonNode raw) {
        super(platform, messageId, content, timestamp, mentionedUsers);
        if (platform != Platform.NAPCAT_GROUP && platform != Platform.NAPCAT_PRIVATE) {
            throw new IllegalArgumentException("NapcatMessage 仅支持 Napcat 群聊和私聊");
        }
        this.raw = raw;
        this.attachments = attachments;
        this.segments = segments;
    }

    @Override
    public boolean recall() {
        return getPlatform() == Platform.NAPCAT_GROUP
                ? GroupMessage.recallMessage(getMessageId())
                : PrivateMessage.recallMessage(getMessageId());
    }
}
