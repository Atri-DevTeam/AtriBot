package top.yzljc.atribot.webui;

import lombok.Data;

/**
 * @Author YZ_Ljc_
 * @ClassName ResultData
 * @Created_at 2026/05/02
 * @Project AtriData
 * @Package top.yzljc.atri.common
 */
@Data
public class Result<T> {
    public static final int ERROR_STATUS = 432;
    private static final String DEFAULT_ERROR_MESSAGE = "请求处理失败，请稍后重试。";

    private int status;
    private String message;
    private T data;
    private long timestamp ;


    public Result(){
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> Result<T> success(T data) {
        Result<T> resultData = new Result<>();
        resultData.setStatus(200);
        resultData.setMessage("ok");
        resultData.setData(data);
        return resultData;
    }

    /** 保留原错误码参数以兼容现有调用；响应体中的错误状态统一为 432。 */
    public static <T> Result<T> fail(int ignoredCode, String message) {
        Result<T> resultData = new Result<>();
        resultData.setStatus(ERROR_STATUS);
        resultData.setMessage(message == null || message.isBlank() ? DEFAULT_ERROR_MESSAGE : message.trim());
        return resultData;
    }

    public static <T> Result<T> custom(int code, String message, T data) {
        Result<T> resultData = new Result<>();
        resultData.setStatus(code);
        resultData.setMessage(message);
        resultData.setData(data);
        return resultData;
    }
}
