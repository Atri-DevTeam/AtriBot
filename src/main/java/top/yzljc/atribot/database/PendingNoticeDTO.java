package top.yzljc.atribot.database;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

/**
 * 待送达通知。
 *
 * <p>主动消息不可用或发送失败时，通知会落到这张表，等待目标下一次与 Bot 交互时以被动消息补发。
 *
 * @Author YZ_Ljc_
 * @ClassName PendingNoticeDTO
 * @Created_at 2026/07/21
 * @Project AtriMeow
 * @Package top.yzljc.atribot.database
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PendingNoticeDTO {
    private String id;
    /** OFFICIAL_C2C / OFFICIAL_GROUP */
    private String targetType;
    /** 群聊为 group_openid，私聊为用户 openid */
    private String targetId;
    /** 群聊场景下等待该用户发言时再补发，并在正文中 @ 他；为空表示群内任意消息都可触发 */
    private String mentionUserId;
    /** Markdown 正文 */
    private String content;
    /** 业务来源，如 image_source / feedback */
    private String source;
    private String sourceId;
    private Timestamp createTime;
    @JsonProperty("isDelivered")
    private boolean isDelivered;
    private Timestamp deliverTime;
    private int attempts;
    private String lastError;
}
