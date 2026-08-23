package top.yzljc.atribot.chat.official.moderation;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
* @Author AndyOctopus
* @ClassName JoinReviewConfig
* @Created_at 2026/08/20
* @Project AtriMeow
* @Package top.yzljc.atribot.chat.official.moderation
*/
@Data
public class JoinReviewConfig {
    private boolean enabled = false;
    private List<JoinReviewRule> rules = new ArrayList<>();
    private JoinReviewMode mode = JoinReviewMode.DISABLED;
    private JoinReviewKeywordRule keywordRule = new JoinReviewKeywordRule();
    private String aiSystemPrompt = "";
    private String rejectReason = "";
    private boolean notifyDebugGroup = false;
}
