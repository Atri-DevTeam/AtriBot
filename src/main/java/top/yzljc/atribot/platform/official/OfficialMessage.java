package top.yzljc.atribot.platform.official;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import top.yzljc.atribot.platform.Message;
import top.yzljc.atribot.platform.Platform;
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
public class OfficialMessage extends Message {
    private final int type;
    private final String refIdx;
    private final JsonNode attachments;
    private final JsonNode ark;
    private final JsonNode reference;

    public OfficialMessage(Platform platform, String messageId, String content, String timestamp, List<User> mentionedUsers, int type, String refIdx, JsonNode attachments, JsonNode ark, JsonNode reference) {
        super(platform, messageId, content, timestamp, mentionedUsers);
        this.type = type;
        this.refIdx = refIdx;
        this.attachments = attachments;
        this.ark = ark;
        this.reference = reference;
    }
}