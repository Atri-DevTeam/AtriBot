package top.yzljc.qqbot.config.webui.exception;

import lombok.Getter;

/**
 * @Author YZ_Ljc_
 * @ClassName FeatureNotFoundException
 * @Created_at 2026/05/02
 * @Project AtriBot
 * @Package top.yzljc.qqbot.config.webui.exception
 */
@Getter
public class FeatureNotFoundException extends RuntimeException {
    private final String featureName;

    public FeatureNotFoundException(String featureName) {
        super("未知的功能: " + featureName);
        this.featureName = featureName;
    }
}