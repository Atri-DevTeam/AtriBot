package top.yzljc.atribot.functions.onebot;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.service.request.PostRequest;
import top.yzljc.atribot.service.request.RequestType;
import top.yzljc.atribot.service.ThreadManager;
import top.yzljc.atribot.config.Config;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.impl.GroupMemberChangeEvent;
import top.yzljc.atribot.event.impl.GroupMemberChangeType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @Author YZ_Ljc_
 * @ClassName UnknownInvitation
 * @Created_at 2026/04/03
 * @Project Yzljc-QQ-Bot
 * @Package top.yzljc.qqbot.functions
 */
@Slf4j
public class UnknownInvitation implements Listener {
    private static final List<Long> allowed_users = Config.getInstance().getNapcatAdminUins();

    @EventHandler
    public void onUserInviteBot(GroupMemberChangeEvent event) {
        if (event.getOperateType() == GroupMemberChangeType.ME_PASSIVE_INVITE && !(allowed_users.contains(event.getOperatorId()) || event.getOperatorId() == event.getSelfId())) {
            long groupId = event.getGroupId();
            Map<String, Object> data = new HashMap<>();
            data.put("group_id", String.valueOf(groupId));
            data.put("is_dismiss", "false");
            event.getGroup().sendSingleText("本次进群信息未经同意，为避免一些潜在风险，已主动退出，如有需求，请联系开发者主动加群！");
            ThreadManager.schedule(() -> PostRequest.sendPost(RequestType.QUIT_GROUP, data), 5, TimeUnit.SECONDS);
            log.info("已自动退出未知邀请的群聊，群ID: {}", groupId);
        }
    }
}