package top.yzljc.atribot.functions.onebot;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.config.groups.GroupConfigManager;
import top.yzljc.atribot.chat.onebot.GroupInformation;
import top.yzljc.atribot.chat.onebot.UserInformation;
import top.yzljc.atribot.config.Config;
import top.yzljc.atribot.config.LoadIllegalWords;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.impl.GroupMessageEvent;

@Slf4j
public class GroupMessageCheck implements Listener {
    @EventHandler
    public void onGroupMessage(GroupMessageEvent event) {
        if (!GroupConfigManager.isFeatureEnabled(event.getGroupId(), "illegal_words_check") || event.getUserId() == event.getSelfId()) return;

        if (LoadIllegalWords.containsSensitiveWord(event.getRawMessage())) {
            if (event.getGroupId() == Config.getInstance().getDebugGroupId()) return;
            event.recall();

            String detectedWord = LoadIllegalWords.findSensitiveWord(event.getRawMessage());
            log.info("检测到违规词：{}, 已尝试撤回, 来自 QQ: {}, 消息 ID: {}, 群组: {}", detectedWord, UserInformation.getUserName(event.getUserId()), event.getMessageId(), GroupInformation.getGroupName(event.getGroupId()));
        }
    }
}
