package top.yzljc.qqbot.chat;

import top.yzljc.qqbot.botservice.request.PostRequest;
import top.yzljc.qqbot.botservice.request.RequestType;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.utils.Logger;

import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName SendPoke
 * @Created_at 2026/04/04
 * @Project AtriBot
 * @Package top.yzljc.qqbot.chat
 */
public class SendPoke {
    public static void poke(long targetId, long groupId) {
        if (targetId == Config.getInstance().getBotUid()) return;
        Map<String, Object> params = Map.of(
                "group_id", groupId,
                "user_id", targetId
        );
        PostRequest.sendPost(RequestType.GROUP_POKE, params);
        Logger.info("已向用户 {} 反戳！", targetId);
    }
}