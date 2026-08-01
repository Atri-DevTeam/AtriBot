package top.yzljc.atribot.function.general.impl;

/**
 * @Author YZ_Ljc_
 * @ClassName ImageDTO
 * @Created_at 2026/06/19
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.general.impl
 */
public record ImageDTO(String url, int width, int height, String errorMessage, String traceId) {

    public ImageDTO(String url, int width, int height) {
        this(url, width, height, null, null);
    }

    public boolean isError() {
        return errorMessage != null;
    }
}
