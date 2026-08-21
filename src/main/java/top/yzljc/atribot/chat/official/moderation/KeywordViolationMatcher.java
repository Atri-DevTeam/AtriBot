package top.yzljc.atribot.chat.official.moderation;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.regex.Pattern;

/**
* @Author AndyOctopus
* @ClassName KeywordViolationMatcher
* @Created_at 2026/08/20
* @Project AtriMeow
* @Package top.yzljc.atribot.chat.official.moderation
*/
public final class KeywordViolationMatcher {

    private static final Pattern LINK_PATTERN = Pattern.compile(
            "(https?://|www\\.)[\\w.-]+(:\\d+)?(/[\\w\\-./?%&=#~+]*)?", Pattern.CASE_INSENSITIVE);

    public static ViolationRule match(String content, JsonNode ark, List<ViolationRule> rules) {
        if (rules == null || rules.isEmpty()) {
            return null;
        }
        for (ViolationRule rule : rules) {
            if (matches(content, ark, rule)) {
                return rule;
            }
        }
        return null;
    }

    private static boolean matches(String content, JsonNode ark, ViolationRule rule) {
        return switch (rule.getType()) {
            case LINK -> content != null && LINK_PATTERN.matcher(content).find();
            case MINI_PROGRAM -> ark != null && !ark.isNull() && !ark.isMissingNode();
            case KEYWORD -> matchKeyword(content, rule);
        };
    }

    private static boolean matchKeyword(String content, ViolationRule rule) {
        String keyword = rule.getKeyword();
        if (content == null || keyword == null || keyword.isBlank()) {
            return false;
        }
        return rule.getMatchMode() == MatchMode.EQUALS
                ? content.trim().equals(keyword.trim())
                : content.contains(keyword);
    }
}
