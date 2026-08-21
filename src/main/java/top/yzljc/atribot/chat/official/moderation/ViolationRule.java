package top.yzljc.atribot.chat.official.moderation;

import lombok.Data;

/**
* @Author AndyOctopus
* @ClassName ViolationRule
* @Created_at 2026/08/20
* @Project AtriMeow
* @Package top.yzljc.atribot.chat.official.moderation
*/
@Data
public class ViolationRule {
    private String ruleId = "";
    private ViolationRuleType type = ViolationRuleType.KEYWORD;
    private MatchMode matchMode = MatchMode.CONTAINS;
    private String keyword = "";
    private String remark = "";
}
