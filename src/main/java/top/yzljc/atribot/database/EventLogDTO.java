package top.yzljc.atribot.database;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventLogDTO {
    private long id;
    private String eventType;
    private String eventId;
    private Integer seq;
    private String rawData;
    private Timestamp createTime;
}
