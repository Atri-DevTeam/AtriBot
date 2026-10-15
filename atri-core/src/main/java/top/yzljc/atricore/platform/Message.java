package top.yzljc.atricore.platform;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * 平台消息的公共字段，附件、会话和引用信息由各平台消息类型提供
 *
 * @Author YZ_Ljc_
 * @ClassName Message
 * @Created_at 2026/06/16
 * @Project AtriMeow
 * @Package top.yzljc.atricore.platform
 */
@Getter
@AllArgsConstructor
public abstract class Message {
    private final Platform platform;
    private final String messageId;
    private final String content;
    private final String timestamp;
    private final List<User> mentionedUsers;
}