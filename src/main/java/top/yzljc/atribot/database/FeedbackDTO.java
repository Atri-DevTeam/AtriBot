package top.yzljc.atribot.database;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackDTO {
    private String id;
    private String platform;
    private String userId;
    private String username;
    private String groupId;
    private String submitContent;
    private Timestamp createTime;
    @JsonProperty("isRead")
    private boolean isRead;
    private String replyContent;
    private Timestamp replyTime;
    @JsonProperty("isHidden")
    private boolean isHidden;
}