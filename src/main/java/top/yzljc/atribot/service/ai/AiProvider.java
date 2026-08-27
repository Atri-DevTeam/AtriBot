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
    PLAN_1("plan_1"),
    PLAN_2("plan_2");

    private final String configKey;

    AiProvider(String configKey) {
        this.configKey = configKey;
    }
}