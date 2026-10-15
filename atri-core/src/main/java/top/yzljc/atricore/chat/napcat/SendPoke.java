package top.yzljc.atricore.chat.napcat;

import top.yzljc.atricore.AtriProvider;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atricore.platform.napcat.PostRequest;
import top.yzljc.atricore.platform.napcat.RequestType;

import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName SendPoke
 * @Created_at 2026/04/04
 * @Project AtriBot
 * @Package top.yzljc.atricore.chat.napcat
 */
@Slf4j
public class SendPoke {
    public static void poke(String targetId, String groupId) {
        if (AtriProvider.getInstance().getSettings().getNapcatBotUin().equals(targetId)) return;
        Map<String, Object> params = Map.of(
                "group_id", groupId,
                "user_id", targetId
        );
        PostRequest.sendPost(RequestType.GROUP_POKE, params);
        log.info("已向用户 {} 反戳！", targetId);
    }
}
