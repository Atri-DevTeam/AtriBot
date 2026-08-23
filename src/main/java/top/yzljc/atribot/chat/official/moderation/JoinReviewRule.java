package top.yzljc.atribot.chat.official.moderation;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 按列表顺序执行的单条入群审核规则。 */
@Data
public class JoinReviewRule {
    private String id = "";
    private String name = "";
    private boolean enabled = true;
    private JoinReviewRuleType type = JoinReviewRuleType.KEYWORD;
    private MatchMode matchMode = MatchMode.CONTAINS;
    private List<String> keywords = new ArrayList<>();
    private JoinReviewRuleOutcome onMatch = JoinReviewRuleOutcome.REJECT;
    private String aiSystemPrompt = "";
    private JoinReviewRuleOutcome onViolation = JoinReviewRuleOutcome.REJECT;
    private JoinReviewRuleOutcome onPass = JoinReviewRuleOutcome.CONTINUE;
    private String rejectReason = "";
}
