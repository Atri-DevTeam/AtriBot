package top.yzljc.atribot.utils.socket;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @Author YZ_Ljc_
 * @ClassName BindResponse
 * @Created_at 2026/05/09
 * @Project AtriBot
 * @Package top.yzljc.qqbot.functions.impl
 */

public record BindResponse(int code, String uuid) {

    @JsonCreator
    public BindResponse(@JsonProperty("code") int code, @JsonProperty("uuid") String uuid) {
        this.code = code;
        this.uuid = uuid;
    }
}