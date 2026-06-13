package top.yzljc.atribot.database;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackDTO {
    private int id;
    private String uuid;
    private String content;
    private String replyContent;
    private String replier;
    private String provider;
    private int uploadedWay;
    private boolean isRead;
    private JsonNode info;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    private Timestamp createdAt;
    @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")
    private Timestamp repliedAt;
}