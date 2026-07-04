package top.yzljc.atribot.service.ai;

import lombok.Getter;

/**
 * @Author YZ_Ljc_
 * @ClassName AiProvider
 * @Created_at 2026/07/01
 * @Project AtriMeow
 * @Package top.yzljc.atribot.service.ai
 */
@Getter
public enum AiProvider {

    DEFAULT("default"),
    OTHER("other"),
    OPENCODE("opencode"),;

    private final String configKey;

    AiProvider(String configKey) {
        this.configKey = configKey;
    }
}
