package top.yzljc.atribot.platform;

import lombok.Getter;

/**
 * @Author YZ_Ljc_
 * @ClassName UnsupportedPlatform
 * @Created_at 2026/07/07
 * @Project AtriMeow
 * @Package top.yzljc.atribot.platform
 */
public class UnsupportedPlatform extends RuntimeException {
    @Getter
    private final Platform platform;

    public UnsupportedPlatform(Platform platform, String message) {
        super(String.format("平台 [%s] 不支持: %s", platform, message));
        this.platform = platform;
    }
}