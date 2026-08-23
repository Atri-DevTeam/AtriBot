package top.yzljc.atribot.chat.official.moderation;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.database.repo.ModerationLogRepository;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.OfficialGroupJoinRequestEvent;
import top.yzljc.atribot.service.runtime.ThreadManager;
import top.yzljc.atribot.utils.tools.Alert;

/**
* @Author AndyOctopus
* @ClassName GroupJoinReviewListener
* @Created_at 2026/08/20
* @Project AtriMeow
* @Package top.yzljc.atribot.chat.official.moderation
*/
@Slf4j
public final class GroupJoinReviewListener implements Listener {

    @EventHandler
    public void onJoinRequest(OfficialGroupJoinRequestEvent event) {
        if (event.getStrategyId() != null) {
            return;
        }
        GroupModerationSettings settings = GroupModerationStore.get(event.getGroupOpenId());
        JoinReviewConfig config = settings.getJoinReview();
        if (config.getRules() != null && !config.getRules().isEmpty()) {
            if (config.isEnabled()) {
                ThreadManager.execute(() -> processRules(event, config));
            }
            return;
        }
        JoinReviewMode mode = config.getMode();
        if (mode == JoinReviewMode.DISABLED) {
            return;
        }

        if (mode == JoinReviewMode.KEYWORD || mode == JoinReviewMode.ALL) {
            JoinReviewDecision decision = matchKeyword(config.getKeywordRule(), event.getAnswer());
            if (decision != null) {
                apply(event, decision, config, "命中入群审核关键词规则");
                return;
            }
            if (mode == JoinReviewMode.KEYWORD) {
                return;
            }
        }

        ThreadManager.execute(() -> {
            AiModerationVerdict verdict = AiModerationService.reviewJoinRequest(
                    config.getAiSystemPrompt(), event.getQuestion(), event.getAnswer());
            JoinReviewDecision decision = verdict.violation() ? JoinReviewDecision.REJECT : JoinReviewDecision.APPROVE;
            apply(event, decision, config, verdict.reason());
        });
    }

    private void processRules(OfficialGroupJoinRequestEvent event, JoinReviewConfig config) {
        for (JoinReviewRule rule : config.getRules()) {
            if (rule == null || !rule.isEnabled()) {
                continue;
            }

            JoinReviewRuleOutcome outcome;
            String detail;
            if (rule.getType() == JoinReviewRuleType.KEYWORD) {
                if (!matchesKeywords(rule, event.getAnswer())) {
                    continue;
                }
                outcome = rule.getOnMatch();
                detail = "命中规则「" + rule.getName() + "」";
            } else {
                AiModerationVerdict verdict = AiModerationService.reviewJoinRequest(
                        rule.getAiSystemPrompt(), event.getQuestion(), event.getAnswer());
                outcome = verdict.violation() ? rule.getOnViolation() : rule.getOnPass();
                detail = "AI 规则「" + rule.getName() + "」：" + verdict.reason();
            }

            if (outcome == null || outcome == JoinReviewRuleOutcome.CONTINUE) {
                continue;
            }
            JoinReviewDecision decision = outcome == JoinReviewRuleOutcome.APPROVE
                    ? JoinReviewDecision.APPROVE
                    : JoinReviewDecision.REJECT;
            apply(event, decision, config, detail, rule.getRejectReason());
            return;
        }
    }

    private boolean matchesKeywords(JoinReviewRule rule, String answer) {
        if (rule.getKeywords() == null || answer == null) {
            return false;
        }
        for (String keyword : rule.getKeywords()) {
            if (keyword == null || keyword.isBlank()) {
                continue;
            }
            boolean hit = rule.getMatchMode() == MatchMode.EQUALS
                    ? answer.trim().equals(keyword.trim())
                    : answer.contains(keyword);
            if (hit) {
                return true;
            }
        }
        return false;
    }

    private JoinReviewDecision matchKeyword(JoinReviewKeywordRule rule, String answer) {
        if (rule == null || rule.getKeywords() == null || answer == null) {
            return null;
        }
        for (String keyword : rule.getKeywords()) {
            if (keyword == null || keyword.isBlank()) {
                continue;
            }
            boolean hit = rule.getMatchMode() == MatchMode.EQUALS
                    ? answer.trim().equals(keyword.trim())
                    : answer.contains(keyword);
            if (hit) {
                return rule.getOnHit();
            }
        }
        return null;
    }

    private void apply(OfficialGroupJoinRequestEvent event, JoinReviewDecision decision,
                        JoinReviewConfig config, String detail) {
        apply(event, decision, config, detail, config.getRejectReason());
    }

    private void apply(OfficialGroupJoinRequestEvent event, JoinReviewDecision decision,
                       JoinReviewConfig config, String detail, String ruleRejectReason) {
        String rejectReason = ruleRejectReason == null || ruleRejectReason.isBlank()
                ? config.getRejectReason()
                : ruleRejectReason;
        boolean success = decision == JoinReviewDecision.APPROVE
                ? event.approve()
                : event.deny(rejectReason);
        ModerationLogRepository.log(event.getGroupOpenId(), "JOIN_REVIEW", decision.name().toLowerCase(),
                event.getMemberOpenId(), detail + (success ? "" : "（接口调用失败）"));

        if (config.isNotifyDebugGroup()) {
            // TODO: 通知到Debug群逻辑后续需改动
            Alert.notify("[群管系统] 群 " + event.getGroupOpenId() + " 入群审核：" + decision + "，" + detail);
        }
    }
}
