package top.yzljc.atricore.chat.official.button;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * QQ 私聊消息操作栏按钮
 *
 * @Author YZ_Ljc_
 * @ClassName ActionButton
 * @Created_at 2026/11/04
 * @Project AtriMeow
 * @Package top.yzljc.atricore.chat.official.button
 */
@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ActionButton {
    /**
     * 赞踩按钮
     */
    private Boolean feedback;

    /**
     * 语音播放按钮
     */
    private Boolean tts;

    /**
     * 重新生成按钮
     */
    @JsonProperty("re_generate")
    private Boolean reGenerate;

    /**
     * 停止生成按钮
     */
    @JsonProperty("stop_generate")
    private Boolean stopGenerate;

    /**
     * 操作回调数据，最多 128 个字符
     */
    @JsonProperty("callback_data")
    private String callbackData;

    @Builder
    private ActionButton(Boolean feedback, Boolean tts, Boolean reGenerate,
                         Boolean stopGenerate, String callbackData) {
        this.feedback = feedback;
        this.tts = tts;
        this.reGenerate = reGenerate;
        this.stopGenerate = stopGenerate;
        setCallbackData(callbackData);
    }

    /**
     * 设置操作回调数据
     *
     * @param callbackData 回调数据，不得超过 128 个 Unicode 码点
     * @return 当前操作栏对象
     * @throws IllegalArgumentException 回调数据超过 128 个 Unicode 码点
     */
    public ActionButton setCallbackData(String callbackData) {
        if (callbackData != null && callbackData.codePointCount(0, callbackData.length()) > 128) {
            throw new IllegalArgumentException("操作栏回调数据不能超过 128 个字符");
        }
        this.callbackData = callbackData;
        return this;
    }
}
