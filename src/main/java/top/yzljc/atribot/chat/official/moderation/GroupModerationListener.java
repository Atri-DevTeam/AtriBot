package top.yzljc.atribot.chat.official.moderation;

import lombok.extern.slf4j.Slf4j;
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
        String content = message.getContent();
        String memberOpenId = event.getUser().getUserId();

        if (settings.getKeywordRecall().isEnabled()) {
            ThreadManager.execute(() -> handleKeywordCheck(event, settings, content, memberOpenId));
        }
        if (settings.getAiRecall().isEnabled()) {
            ThreadManager.execute(() -> handleAiCheck(event, settings, content, memberOpenId));
        }
    }

    private void handleKeywordCheck(OfficialGroupMessageCreateEvent event, GroupModerationSettings settings,
                                     String content, String memberOpenId) {
        ViolationRule rule = KeywordViolationMatcher.match(content, event.getMessage().getArk(),
                settings.getKeywordRecall().getRules());
        if (rule == null) {
            return;
        }
        applyAction(event, memberOpenId, settings.getKeywordRecall().getAction(), "KEYWORD_RECALL",
                "命中规则「" + rule.getRemark() + "」(" + rule.getType() + ": " + rule.getKeyword() + ")");
    }

    private void handleAiCheck(OfficialGroupMessageCreateEvent event, GroupModerationSettings settings,
                                String content, String memberOpenId) {
        AiModerationVerdict verdict = AiModerationService.reviewMessage(settings.getAiRecall().getSystemPrompt(), content);
        if (!verdict.violation()) {
            return;
        }
        applyAction(event, memberOpenId, settings.getAiRecall().getAction(), "AI_RECALL", verdict.reason());
    }

    private void applyAction(OfficialGroupMessageCreateEvent event, String memberOpenId,
                              ModerationAction action, String category, String detail) {
        String groupOpenId = event.getGroupId();

        if (action.isRecall()) {
            // TODO: 撤回逻辑后续需改动
            GroupChat.recallMessage(groupOpenId, event.getMessage().getMessageId());
            ModerationLogRepository.log(groupOpenId, category, "recall", memberOpenId, detail);
        }

        if (action.isRemind() && action.getRemindMessage() != null && !action.getRemindMessage().isBlank()) {
            event.sendMessage(action.getRemindMessage());
        }

        if (action.isMute() && action.getMuteSeconds() > 0) {
            // TODO: 禁言逻辑后续需改动
            Mute.muteMember(groupOpenId, memberOpenId, Duration.ofSeconds(action.getMuteSeconds()));
            ModerationLogRepository.log(groupOpenId, category, "mute", memberOpenId, detail);
        }

        if (action.isNotifyDebugGroup()) {
            // TODO: 通知到Debug群逻辑后续需改动
            Alert.notify("[群管系统] 群 " + groupOpenId + " 触发 " + category + "：" + detail);
        }
    }
}
