package top.yzljc.atricore.chat.official;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 单聊输入状态通知
 *
 * @param inputType   1 显示正在输入，2 取消显示
 * @param inputSecond 显示时长，单位为秒；取消时可为 null
 * @Author YZ_Ljc_
 * @ClassName InputNotify
 * @Created_at 2026/11/04
 * @Project AtriMeow
 * @Package top.yzljc.atricore.chat.official
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record InputNotify(@JsonProperty("input_type") int inputType,
                          @JsonProperty("input_second") Integer inputSecond) {
    public InputNotify {
        if (inputType != 1 && inputType != 2) throw new IllegalArgumentException("输入状态类型必须为 1 或 2");
        if (inputType == 1 && (inputSecond == null || inputSecond <= 0)) {
            throw new IllegalArgumentException("输入状态持续时间必须大于 0");
        }
    }
}
