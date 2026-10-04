package top.yzljc.atribot.service.request;

/**
 * @Author YZ_Ljc_
 * @ClassName BizResponse
 * @Created_at 2026/10/03
 * @Project AtriMeow
 * @Package top.yzljc.atribot.service.request
 */
public record BizResponse<T>(
        int httpCode,
        long bizCode,
        String message,
        String requestId,
        String timestamp,
        T data
) {
    public boolean isSuccess() {
        return httpCode >= 200 && httpCode < 300 && bizCode == 0;
    }
}
