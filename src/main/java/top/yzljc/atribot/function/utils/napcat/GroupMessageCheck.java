package top.yzljc.atribot.function.utils.napcat;

import top.yzljc.atricore.core.CoreConfig;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atricore.chat.napcat.GroupInformation;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.configuration.LoadIllegalWords;
import top.yzljc.atricore.event.EventHandler;
import top.yzljc.atricore.event.Listener;
import top.yzljc.atricore.event.events.NapcatGroupMessageEvent;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigManager;

import java.util.Objects;

@Slf4j
@Deprecated(since = "3.2.2")
public class GroupMessageCheck implements Listener {
    @EventHandler
    public void onGroupMessage(NapcatGroupMessageEvent event) {
        if (!GroupConfigManager.isFeatureEnabled(event.getGroupId(), "illegal_words_check") || event.getUser().isBot()) return;

        if (LoadIllegalWords.containsSensitiveWord(event.getMessage().getContent())) {
            if (Objects.equals(event.getGroupId(), CoreConfig.getInstance().getSettings().getNapcatDebugGroupUin())) return;
            event.recall();

            String detectedWord = LoadIllegalWords.findSensitiveWord(event.getMessage().getContent());
            log.info("检测到违规词：{}, 已尝试撤回, 来自 QQ: {}, 消息 ID: {}, 群组: {}", detectedWord, event.getUser().getUsername(), event.getMessage().getMessageId(), GroupInformation.getGroupName(event.getGroupId()));
        }
    }
}
