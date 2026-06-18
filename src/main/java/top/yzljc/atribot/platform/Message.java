package top.yzljc.atribot.platform;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName Message
 * @Created_at 2026/06/16
 * @Project AtriMeow
 * @Package top.yzljc.atribot.platform
 */
@Getter
@AllArgsConstructor
public class Message {
    private final Platform platform;
    private final String messageId;
    private final String content;
    private final String timestamp;
    private final List<User> mentionedUsers;
}