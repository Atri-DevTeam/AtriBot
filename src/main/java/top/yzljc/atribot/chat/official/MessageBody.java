package top.yzljc.atribot.chat.official;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * @Author YZ_Ljc_
 * @ClassName MessageBody
 * @Created_at 2026/05/06
 * @Project AtriBot
 * @Package top.yzljc.qqbot.official
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageBody {
    
    private String content;
    
    @JsonProperty("msg_type")
    private Integer msgType; // 必填
    
    private Object markdown;
    
    private Object keyboard;
    
    private Object ark;
    
    private Object media;
    
    @JsonProperty("message_reference")
    private Object messageReference;
    
    @JsonProperty("event_id")
    private String eventId;
    
    @JsonProperty("msg_id")
    private String msgId;
    
    @JsonProperty("msg_seq")
    private Integer msgSeq;
    
    @JsonProperty("is_wakeup")
    private Boolean isWakeup;
}