package top.yzljc.atricore.chat.official;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * 消息引用，与被动回复使用的 msg_id 相互独立
 *
 * @param messageId             被引用消息的索引
 * @param ignoreGetMessageError 是否忽略被引用消息获取失败，null 使用平台默认行为
 * @Author YZ_Ljc_
 * @ClassName MessageReference
 * @Created_at 2026/11/04
 * @Project AtriMeow
 * @Package top.yzljc.atricore.chat.official
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MessageReference(@JsonProperty("message_id") String messageId,
                               @JsonProperty("ignore_get_message_error") Boolean ignoreGetMessageError) {
    public MessageReference {
        Objects.requireNonNull(messageId, "被引用消息索引不能为空");
    }

    public MessageReference(String messageId) {
        this(messageId, null);
    }
}
