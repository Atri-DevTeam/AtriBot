package top.yzljc.atribot.chat.official.moderation;

import lombok.Data;
import lombok.NoArgsConstructor;

/** 用户为单个群保存的 AI 审查提示词方案。 */
@Data
@NoArgsConstructor
public class AiPromptPreset {
    private String id = "";
    private String name = "";
    private String description = "";
    private String prompt = "";

    public AiPromptPreset(String id, String name, String description, String prompt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.prompt = prompt;
    }
}
