package top.yzljc.atribot.database;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OfficialSendLogDTO {
    private long id;
    private String traceId;
    private String entryType;
    private String scene;
    private String method;
    private String url;
    private String requestJson;
    private Integer responseStatus;
    private String responseBody;
    private Integer errorCode;
    private String errorReason;
    private String errorMessage;
    private Timestamp createTime;
}
