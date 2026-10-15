package top.yzljc.atricore.chat.official;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

/**
 * QQ 消息请求数据，交给 Jackson 按平台字段名序列化
 * <p>对象字段应传入可序列化的数据对象或 Map，预先序列化的字符串会按字符串发送
 *
 * @Author YZ_Ljc_
 * @ClassName MessageBody
 * @Created_at 2026/05/06
 * @Project AtriBot
 * @Package top.yzljc.atricore.chat.official
 */
@Data
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageBody {

    private String content;

    @JsonProperty("msg_type")
    private Integer msgType; // 必填

    private Object markdown;

    private Object keyboard;

    /**
     * C2C 提示键盘，具体展示能力取决于客户端
     */
    @JsonProperty("prompt_keyboard")
    private Object promptKeyboard;

    private Object ark;

    private Object media;

    /**
     * 单聊输入状态通知
     */
    @JsonProperty("input_notify")
    private Object inputNotify;

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

    /**
     * 仅频道可用
     */
    @JsonProperty("image")
    private String image;

    @JsonProperty("embed")
    private Object embed;

    @JsonIgnore
    private String recordAttachments;

    @JsonProperty("card")
    private Object card;

    /**
     * 维护消息提示用字段
     */
    @JsonIgnore
    private boolean maintenanceReply;
}
