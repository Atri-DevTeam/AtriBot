package top.yzljc.atribot.chat.official.moderation;

import lombok.Data;

/** 用户为单个群保存的 AI 审查提示词方案。 */
@Data
public class AiPromptPreset {
    private String id = "";
    private String name = "";
    private String description = "";
    private String prompt = "";

    public AiPromptPreset() {
    }

    public AiPromptPreset(String id, String name, String description, String prompt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.prompt = prompt;
    }
}
