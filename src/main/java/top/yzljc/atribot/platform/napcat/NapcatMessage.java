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
    private final JsonNode attachments;
    private final LinkedList<MessageSegment> segments;

    public NapcatMessage(Platform platform, String messageId, String content, String timestamp, List<User> mentionedUsers, JsonNode attachments, LinkedList<MessageSegment> segments) {
        super(platform, messageId, content, timestamp, mentionedUsers);
        this.attachments = attachments;
        this.segments = segments;
    }

    @Override
    public boolean recall(String id, String messageId) {
        return recall(messageId);
    }

    @Override
    public boolean recall(String messageId) {
        if (this.getPlatform().equals(Platform.NAPCAT_GROUP)) {
            return GroupMessage.recallMessage(messageId);
        } else {
            return PrivateMessage.recallMessage(messageId);
        }
    }
}