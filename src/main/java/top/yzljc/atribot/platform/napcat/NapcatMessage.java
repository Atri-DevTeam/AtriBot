package top.yzljc.atribot.platform.napcat;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import top.yzljc.atribot.platform.Message;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.User;

import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName NapcatMessage
 * @Created_at 2026/06/18
 * @Project AtriMeow
 * @Package top.yzljc.atribot.platform.napcat
 */
@Getter
public class NapcatMessage extends Message {
    private final JsonNode attachments;

    public NapcatMessage(Platform platform, String messageId, String content, String timestamp, List<User> mentionedUsers, JsonNode attachments) {
        super(platform, messageId, content, timestamp, mentionedUsers);
        this.attachments = attachments;
    }
}