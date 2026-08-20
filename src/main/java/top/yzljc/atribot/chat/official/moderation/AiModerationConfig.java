package top.yzljc.atribot.chat.official.moderation;

import lombok.Data;

/**
* @Author AndyOctopus
* @ClassName AiModerationConfig
* @Created_at 2026/08/20
* @Project AtriMeow
* @Package top.yzljc.atribot.chat.official.moderation
*/
@Data
public class AiModerationConfig {
    private boolean enabled = false;
    private String systemPrompt = "";
    private ModerationAction action = new ModerationAction();
}
