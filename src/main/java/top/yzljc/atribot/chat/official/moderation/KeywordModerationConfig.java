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
    private ModerationAction action = new ModerationAction();
}
