package top.yzljc.qqbot.functions;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.qqbot.service.userinfo.GetGroupInfo;
import top.yzljc.qqbot.service.userinfo.GetUserInfo;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.LoadIllegalWords;
import top.yzljc.qqbot.event.EventHandler;
import top.yzljc.qqbot.event.Listener;
import top.yzljc.qqbot.event.impl.GroupMessageEvent;

@Slf4j
public class GroupMessageCheck implements Listener {
    @EventHandler
    public void onGroupMessage(GroupMessageEvent event) {
        if (!Config.getInstance().getMessageSpyGroups().contains(event.getGroupId()) || event.getUserId() == event.getSelfId()) return;

        if (LoadIllegalWords.containsSensitiveWord(event.getRawMessage())) {
            if (event.getGroupId() == Config.getInstance().getDebugGroupId()) return;
            event.recall();

            String detectedWord = LoadIllegalWords.findSensitiveWord(event.getRawMessage());
            log.info("检测到违规词：{}, 已尝试撤回, 来自 QQ: {}, 消息 ID: {}, 群组: {}", detectedWord, GetUserInfo.getUserName(event.getUserId()), event.getMessageId(), GetGroupInfo.getGroupName(event.getGroupId()));
        }
    }
}
