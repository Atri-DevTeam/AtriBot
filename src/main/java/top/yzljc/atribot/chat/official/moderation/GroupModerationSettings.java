package top.yzljc.atribot.chat.official.moderation;

import lombok.Data;

/**
* @Author AndyOctopus
* @ClassName GroupModerationSettings
* @Created_at 2026/08/20
* @Project AtriMeow
* @Package top.yzljc.atribot.chat.official.moderation
*/
@Data
public class GroupModerationSettings {
    private KeywordModerationConfig keywordRecall = new KeywordModerationConfig();
    private AiModerationConfig aiRecall = new AiModerationConfig();
    private JoinReviewConfig joinReview = new JoinReviewConfig();
}
