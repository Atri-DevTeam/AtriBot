package top.yzljc.atribot.chat.official.moderation;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
* @Author AndyOctopus
* @ClassName KeywordModerationConfig
* @Created_at 2026/08/20
* @Project AtriMeow
* @Package top.yzljc.atribot.chat.official.moderation
*/
@Data
public class KeywordModerationConfig {
    private boolean enabled = false;
    private List<ViolationRule> rules = new ArrayList<>();
    /**
     * 兜底动作：仅当命中的规则未配置规则级 action（旧数据）时使用
     */
    private ModerationAction action = new ModerationAction();
}
