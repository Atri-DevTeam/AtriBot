package top.yzljc.atribot.database;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName ErrorReportDTO
 * @Project AtriMeow
 * @Package top.yzljc.atribot.database
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorReportDTO {
    private String traceId;
    private String className;
    private String exceptionType;
    private String exceptionMessage;
    private List<String> stackTrace;
    private String causeType;
    private String causeMessage;
    private List<String> causeStackTrace;
    private Timestamp createTime;
}
