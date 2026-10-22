package top.yzljc.atribot.webui;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * @Author YZ_Ljc_
 * @ClassName Result
 * @Created_at 2026/05/02
 * @Project AtriMeow
 * @Package top.yzljc.atribot.webui
 */
@Getter
@JsonInclude(JsonInclude.Include.ALWAYS)
public final class Result<T> {
    private final int code;
    private final String message;
    private final T data;
    private final long timestamp;
    private final String requestId;

    private Result(ApiCode code, String message, T data, String requestId) {
        this.code = code.getCode();
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
        this.requestId = requestId;
    }

    public static <T> Result<T> success(T data) {
        return success(data, null);
    }

    /**
     * @param data 业务数据，允许为 null
     * @param requestId 请求标识，未建立请求追踪时允许为 null
     * @param <T> 业务数据类型
     * @return 业务码为 0 的成功响应
     */
    public static <T> Result<T> success(T data, String requestId) {
        return new Result<>(ApiCode.SUCCESS, ApiCode.SUCCESS.getMessage(), data, requestId);
    }

    /**
     * @param code 固定业务错误码，不允许使用 SUCCESS 或 CUSTOM_ERROR
     * @param requestId 请求标识，未建立请求追踪时允许为 null
     * @param <T> 业务数据类型
     * @return data 为 null 的失败响应
     * @throws NullPointerException code 为 null
     * @throws IllegalArgumentException code 为 SUCCESS 或 CUSTOM_ERROR
     */
    public static <T> Result<T> failure(ApiCode code, String requestId) {
        Objects.requireNonNull(code, "code must not be null");
        if (code == ApiCode.SUCCESS || code == ApiCode.CUSTOM_ERROR)
            throw new IllegalArgumentException("code must be a fixed error code");
        return new Result<>(code, code.getMessage(), null, requestId);
    }

    public static <T> Result<T> failure(ApiCode code) {
        return failure(code, null);
    }

    /**
     * @param message 可公开的非空白业务提示，不得直接传入底层异常消息
     * @param requestId 请求标识，未建立请求追踪时允许为 null
     * @param <T> 业务数据类型
     * @return 业务码为 19999、data 为 null 的失败响应，消息保留原文
     * @throws IllegalArgumentException message 为 null 或空白
     */
    public static <T> Result<T> customFailure(String message, String requestId) {
        if (message == null || message.isBlank()) throw new IllegalArgumentException("message must not be blank");
        return new Result<>(ApiCode.CUSTOM_ERROR, message, null, requestId);
    }

    public static <T> Result<T> customFailure(String message) {
        return customFailure(message, null);
    }

    @JsonProperty("request_id")
    public String getRequestId() {
        return requestId;
    }
}
