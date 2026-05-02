package top.yzljc.qqbot.config;

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

    public static <T> Result<T> fail(int code, String message) {
        Result<T> resultData = new Result<>();
        resultData.setStatus(code);
        resultData.setMessage(message);
        return resultData;
    }

}