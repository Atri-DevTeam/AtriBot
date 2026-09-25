package top.yzljc.atribot.chat.official.moderation;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.chat.napcat.NapcatDebugGroup;
import top.yzljc.atribot.chat.official.GroupChat;
import top.yzljc.atribot.chat.official.management.Mute;
import top.yzljc.atribot.database.repo.ModerationLogRepository;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.OfficialGroupMessageCreateEvent;
import top.yzljc.atribot.platform.qq.QQMessage;
import top.yzljc.atribot.service.runtime.ThreadManager;
import top.yzljc.atribot.utils.tools.Alert;

import java.time.Duration;
import java.time.LocalDateTime;

/**
* @Author AndyOctopus
* @ClassName GroupModerationListener
* @Created_at 2026/08/20
* @Project AtriMeow
* @Package top.yzljc.atribot.chat.official.moderation
*/
@Slf4j
public final class GroupModerationListener implements Listener {

    @EventHandler
    public void onGroupMessage(OfficialGroupMessageCreateEvent event) {
        if (event.shouldIgnore() || event.getUser().isPlatformAdmin()) {
            return;
        }
        String groupOpenId = event.getGroupId();
        GroupModerationSettings settings = GroupModerationStore.get(groupOpenId);
        QQMessage message = event.getMessage();

        if (message.getContent().isBlank()) return;

        String content = message.getContent();
        String memberOpenId = event.getUser().getUserId();

        if (settings.getKeywordRecall().isEnabled()
                || (settings.getAiRecall().isEnabled()
                && settings.getAiRecall().getSchedule() != null
                && settings.getAiRecall().getSchedule().isActive(LocalDateTime.now()))) {
            ThreadManager.execute(() -> handleModeration(event, settings, content, memberOpenId));
        }
    }

    private void handleModeration(OfficialGroupMessageCreateEvent event, GroupModerationSettings settings,
                                  String content, String memberOpenId) {
        if (settings.getKeywordRecall().isEnabled()) {
            ViolationRule rule = KeywordViolationMatcher.match(content, event.getMessage().getArk(),
                    settings.getKeywordRecall().getRules());
            if (rule != null) {
                ModerationAction action = rule.getAction() != null
                        ? rule.getAction()
                        : settings.getKeywordRecall().getAction();
                applyAction(event, memberOpenId, action, "KEYWORD_RECALL",
                        "命中规则「" + rule.getRemark() + "」(" + rule.getType() + ": " + rule.getKeyword() + ")", null);
                return;
            }
        }
        if (!settings.getAiRecall().isEnabled()
                || settings.getAiRecall().getSchedule() == null
                || !settings.getAiRecall().getSchedule().isActive(LocalDateTime.now())) {
            return;
        }

        AiModerationConfig ai = settings.getAiRecall();
        AiModerationVerdict verdict = AiModerationService.reviewMessage(
                ai.getSystemPrompt(), ai.getCustomOutput(), ai.getAllowedDomains(), ai.getType(), content);
        if (verdict.violation()) {
            applyAction(event, memberOpenId, ai.getAction(), "AI_RECALL", verdict.reason(), verdict.customMessage());
        }
    }

    private void applyAction(OfficialGroupMessageCreateEvent event, String memberOpenId,
                              ModerationAction action, String category, String detail, String customReminder) {
        String groupOpenId = event.getGroupId();

        if (action.isRecall()) {
            GroupChat.recallMessage(groupOpenId, event.getMessage().getMessageId());
            ModerationLogRepository.log(groupOpenId, category, "recall", memberOpenId, detail);
        }

        String remindMessage = customReminder != null && !customReminder.isBlank()
                ? customReminder : action.getRemindMessage();
        if (action.isRemind() && remindMessage != null && !remindMessage.isBlank()) {
            event.sendMessage(remindMessage);
        }

        if (action.isMute() && action.getMuteSeconds() > 0) {
            Mute.muteMember(groupOpenId, memberOpenId, Duration.ofSeconds(action.getMuteSeconds()));
            ModerationLogRepository.log(groupOpenId, category, "mute", memberOpenId, detail);
        }

        if (action.isNotifyDebugGroup()) {
            NapcatDebugGroup.sendAsync("[群管] 群 " + groupOpenId + " 触发 " + category + "：" + detail);
        }
    }
}
