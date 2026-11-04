package top.yzljc.atricore.chat.official;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import top.yzljc.atricore.chat.official.ark.Ark;
import top.yzljc.atricore.chat.official.button.ActionButton;
import top.yzljc.atricore.chat.official.button.KeyboardPayload;
import top.yzljc.atricore.chat.official.button.PromptKeyboard;
import top.yzljc.atricore.chat.official.card.Card;

import java.util.Objects;
import java.util.function.ToIntFunction;

import lombok.Builder;
import lombok.Data;
import top.yzljc.atricore.chat.official.media.Media;

/**
 * QQ 消息请求数据，交给 Jackson 按平台字段名序列化
 * <p>消息组件使用具体类型，发送时在独立副本上补充消息类型与回复序号
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
    private Integer msgType;

    private Markdown markdown;

    private KeyboardPayload keyboard;

    /**
     * C2C 提示键盘，具体展示能力取决于客户端
     */
    @JsonProperty("prompt_keyboard")
    private PromptKeyboard promptKeyboard;

    private Ark ark;

    private Media media;

    /**
     * 单聊输入状态通知
     */
    @JsonProperty("input_notify")
    private InputNotify inputNotify;

    @JsonProperty("message_reference")
    private MessageReference messageReference;

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
    private Embed embed;

    @JsonIgnore
    private String recordAttachments;

    @JsonProperty("card")
    private Card card;

    /**
     * 维护消息提示用字段
     */
    @JsonIgnore
    private boolean maintenanceReply;

    @JsonProperty("action_button")
    private ActionButton actionButton;

    /**
     * 使用指定来源回复消息，保留内容及引用，不修改原对象
     *
     * @param source 消息或事件来源
     * @return 已替换回复来源的消息副本，发送时重新分配序号
     */
    public MessageBody withReply(RT source) {
        Objects.requireNonNull(source, "回复来源不能为空");
        var copy = toBuilder().msgId(null).eventId(null).msgSeq(null).isWakeup(null);
        switch (source) {
            case RT.Message message -> copy.msgId(message.id());
            case RT.Event event -> copy.eventId(event.id());
        }
        return copy.build();
    }

    /**
     * 校验消息组合并生成协议副本，不分配回复序号
     *
     * @return 已推导消息类型并整理键盘的独立消息体
     * @throws IllegalArgumentException 内容类型或发送参数冲突
     */
    public MessageBody normalized() {
        boolean hasMsg = msgId != null && !msgId.isBlank();
        boolean hasEvent = eventId != null && !eventId.isBlank();
        if (hasMsg && hasEvent) throw new IllegalArgumentException("msg_id 与 event_id 不能同时设置");
        if (Boolean.TRUE.equals(isWakeup) && (hasMsg || hasEvent)) {
            throw new IllegalArgumentException("召回消息不能同时设置被动回复来源");
        }
        if (msgSeq != null && msgSeq < 1) throw new IllegalArgumentException("消息回复序号必须大于 0");
        int components = (markdown == null ? 0 : 1) + (ark == null ? 0 : 1) + (card == null ? 0 : 1)
                + (media == null ? 0 : 1) + (embed == null ? 0 : 1) + (inputNotify == null ? 0 : 1);
        if (components > 1) throw new IllegalArgumentException("一条消息只能设置一种正文类型");
        if (image != null && components > 0) throw new IllegalArgumentException("频道图片不能与其他正文类型混用");
        if (components == 0 && content == null && image == null) {
            throw new IllegalArgumentException("消息正文不能为空");
        }
        Integer type = null;
        if (markdown != null) type = 2;
        else if (ark != null) type = 3;
        else if (embed != null) type = 4;
        else if (inputNotify != null) type = 6;
        else if (media != null) type = 7;
        else if (card != null) type = 8;
        else if (image == null) type = 0;
        if (msgType != null && type != null && !msgType.equals(type)) {
            throw new IllegalArgumentException("msg_type 与消息正文类型不一致");
        }
        var copy = toBuilder().msgType(msgType == null ? type : msgType)
                .msgId(hasMsg ? msgId : null).eventId(hasEvent ? eventId : null);
        if (keyboard == null && promptKeyboard == null && markdown != null && markdown.getKeyboard() != null) {
            var layout = markdown.getKeyboard();
            var payload = KeyboardPayload.from(layout);
            if (layout.isPromptStyle()) copy.promptKeyboard(new PromptKeyboard(payload));
            else copy.keyboard(payload);
        }
        if (markdown != null) copy.markdown(new Markdown(markdown.getText(), markdown.getForceVerifyImageResource()));
        return copy.build();
    }

    /**
     * 发送前分配缺省回复序号，显式序号保持原值
     */
    MessageBody prepareForSend(ToIntFunction<String> sequenceProvider) {
        MessageBody copy = normalized();
        if (copy.msgId != null && !copy.msgId.isBlank() && copy.msgSeq == null
                && copy.image == null && copy.embed == null) {
            copy.msgSeq = sequenceProvider.applyAsInt(copy.msgId);
        }
        return copy;
    }
}
