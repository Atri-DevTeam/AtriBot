package top.yzljc.qqbot.functions;

import top.yzljc.qqbot.botservice.request.PostRequest;
import top.yzljc.qqbot.botservice.request.RequestType;
import top.yzljc.qqbot.botservice.thread.ThreadManager;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.event.EventHandler;
import top.yzljc.qqbot.event.Listener;
import top.yzljc.qqbot.event.impl.GroupMemberChangeEvent;
import top.yzljc.qqbot.event.impl.GroupMemberChangeType;
import top.yzljc.qqbot.utils.Logger;

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
public class UnknownInvitation implements Listener {
    private static final List<Long> allowed_users = Config.getInstance().getAdminUids();

    @EventHandler
    public void onUserInviteBot(GroupMemberChangeEvent event) {
        if (event.getOperateType() == GroupMemberChangeType.ME_PASSIVE_INVITE && !allowed_users.contains(event.getOperatorId())) {
            long groupId = event.getGroupId();
            Map<String, Object> data = new HashMap<>();
            data.put("group_id", String.valueOf(groupId));
            data.put("is_dismiss", "false");
            event.getGroup().sendSingleText("本次进群信息未经同意，为避免一些潜在风险，已主动退出，如有需求，请联系开发者申请！");
            ThreadManager.schedule(() -> PostRequest.sendPost(RequestType.QUIT_GROUP, data), 5, TimeUnit.SECONDS);
            Logger.info("已自动退出未知邀请的群聊，群ID: " + groupId);
        }
    }
}