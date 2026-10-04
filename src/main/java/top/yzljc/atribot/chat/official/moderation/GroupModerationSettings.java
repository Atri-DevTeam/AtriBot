package top.yzljc.atribot.chat.official.moderation;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;

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

    @JsonIgnore
    public boolean isAnyEnabled() {
        if (keywordRecall != null && keywordRecall.isEnabled()
                || aiRecall != null && aiRecall.isEnabled()) {
            return true;
        }
        if (joinReview == null) return false;
        if (joinReview.isEnabled()) return true;
        return (joinReview.getRules() == null || joinReview.getRules().isEmpty())
                && joinReview.getMode() != null && joinReview.getMode() != JoinReviewMode.DISABLED;
    }
}
