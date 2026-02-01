package top.yzljc.qqbot.feature.schedule;

import top.yzljc.qqbot.botkits.request.RequestType;
import top.yzljc.qqbot.botkits.request.PostRequest;
import top.yzljc.qqbot.config.groups.GroupConfigManager;

import java.util.Set;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.config.groups.GroupList;

public class AutoSign {

    private static final Logger log = LoggerFactory.getLogger(AutoSign.class);

    private static void signAllGroups() {
        try {
            Set<Long> groupIds = GroupList.fetchAllGroupIds();

            if (groupIds.isEmpty()) {
                log.warn("未获取到任何群号，自动打卡跳过");
                return;
            }

            for (Long groupId : groupIds) {

                if (!GroupConfigManager.isFeatureEnabled(groupId, "auto_sign")) {
                    continue;
                }

                sendGroupSign(groupId);
                log.info("群 {} 打卡成功", groupId);
            }
        } catch (Exception e) {
            log.warn("自动打卡异常", e);
        }
    }

    private static void sendGroupSign(long groupId) {
        PostRequest.sendSimplePost(RequestType.SEND_SIGN, "group_id", groupId);
    }

    public static void processAutoSign() {
        Executors.newSingleThreadExecutor().submit(AutoSign::signAllGroups);
    }
}
