package top.yzljc.atribot.chat.official.moderation;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
* @Author AndyOctopus
* @ClassName JoinReviewKeywordRule
* @Created_at 2026/08/20
* @Project AtriMeow
* @Package top.yzljc.atribot.chat.official.moderation
*/
@Data
public class JoinReviewKeywordRule {
    private MatchMode matchMode = MatchMode.CONTAINS;
    private List<String> keywords = new ArrayList<>();
    private JoinReviewDecision onHit = JoinReviewDecision.REJECT;
}
