package top.yzljc.atribot.chat.official.moderation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
* @Author AndyOctopus
* @ClassName AiModerationConfig
* @Created_at 2026/08/20
* @Project AtriMeow
* @Package top.yzljc.atribot.chat.official.moderation
*/
@Data
@JsonIgnoreProperties({"promptPresets", "customPresets"})
public class AiModerationConfig {
    private boolean enabled = false;
    /** 内容审核接口模式：0 纯词库，1 词库与 AI 都查，2 词库优先，3 纯 AI。 */
    private int type = 2;
    private String systemPrompt = "";
    private String customOutput = "";
    private boolean useCustomOutputAsReminder = true;
    private List<String> allowedDomains = new ArrayList<>();
    private ModerationAction action = new ModerationAction();
    private AiModerationSchedule schedule = new AiModerationSchedule();
}
