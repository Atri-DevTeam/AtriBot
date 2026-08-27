package top.yzljc.atribot.function.utils.napcat;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.NapcatGroupMemberChangeEvent;
import top.yzljc.atribot.event.impl.GroupMemberChangeType;
import top.yzljc.atribot.platform.napcat.PostRequest;
import top.yzljc.atribot.platform.napcat.RequestType;
import top.yzljc.atribot.service.runtime.ThreadManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    private static final List<String> allowed_users = Config.getInstance().getNapcatAdminUins();

    @EventHandler
    public void onUserInviteBot(NapcatGroupMemberChangeEvent event) {
        if (event.getOperateType() == GroupMemberChangeType.ME_PASSIVE_INVITE && !(allowed_users.contains(event.getOperatorId()) || Objects.equals(event.getOperatorId(), event.getSelfId()))) {
            String groupId = event.getGroupId();
            Map<String, Object> data = new HashMap<>();
            data.put("group_id", String.valueOf(groupId));
            data.put("is_dismiss", "false");
            event.sendMessage("你好，亚托莉喵3rd端已转向私人使用，如有相关功能需求请查看官方机器人端(3889798968)，或通过/feedback与开发者取得联系，感谢您的理解！");
            ThreadManager.schedule(() -> PostRequest.sendPost(RequestType.QUIT_GROUP, data), 5, TimeUnit.SECONDS);
            log.info("已自动退出未知邀请的群聊，群ID: {}", groupId);
        }
    }
}