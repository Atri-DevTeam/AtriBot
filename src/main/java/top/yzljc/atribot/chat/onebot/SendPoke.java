package top.yzljc.atribot.chat.onebot;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.service.request.PostRequest;
import top.yzljc.atribot.service.request.RequestType;
import top.yzljc.atribot.config.Config;

import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName SendPoke
 * @Created_at 2026/04/04
 * @Project AtriBot
 * @Package top.yzljc.qqbot.chat
 */
@Slf4j
public class SendPoke {
    public static void poke(long targetId, long groupId) {
        if (targetId == Config.getInstance().getBotUid()) return;
        Map<String, Object> params = Map.of(
                "group_id", groupId,
                "user_id", targetId
        );
        PostRequest.sendPost(RequestType.GROUP_POKE, params);
        log.info("已向用户 {} 反戳！", targetId);
    }
}