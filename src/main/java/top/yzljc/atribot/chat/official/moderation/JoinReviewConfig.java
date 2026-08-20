package top.yzljc.atribot.chat.official.moderation;

import lombok.Data;

/**
* @Author AndyOctopus
* @ClassName JoinReviewConfig
* @Created_at 2026/08/20
* @Project AtriMeow
* @Package top.yzljc.atribot.chat.official.moderation
*/
@Data
public class JoinReviewConfig {
    private JoinReviewMode mode = JoinReviewMode.DISABLED;
    private JoinReviewKeywordRule keywordRule = new JoinReviewKeywordRule();
    private String aiSystemPrompt = "";
    private boolean notifyDebugGroup = false;
}
