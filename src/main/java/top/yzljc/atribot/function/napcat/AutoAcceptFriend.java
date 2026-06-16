package top.yzljc.atribot.function.napcat;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.chat.napcat.PrivateMessage;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.NapcatFriendRequestEvent;
import top.yzljc.atribot.function.general.HelpCommand;
import top.yzljc.atribot.platform.napcat.PostRequest;
import top.yzljc.atribot.platform.napcat.RequestType;
import top.yzljc.atribot.service.runtime.ThreadManager;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
public class AutoAcceptFriend implements Listener {
    private static String lastUser = "";

    @EventHandler
    public void onFriendAdd(NapcatFriendRequestEvent event) {
        Map<String, Object> params = new HashMap<>();
        params.put("flag", event.getFlag());
        params.put("approve", true);
        params.put("remark", ""); // 备注留空

        try {
            PostRequest.sendPost(RequestType.ACCEPT_FRIEND_REQUEST, params);
        } catch (Exception e) {
            // 1
        }
        if (lastUser != event.getUserId()) {
            ThreadManager.schedule(() -> PrivateMessage.forwardMessage(event.getUserId(), HelpCommand.getAtriHelp(), "ATRI - YZ_Ljc_ Bot 帮助文档", "查看项目帮助信息",
                    "项目开发说明", "指令帮助", "功能介绍"), 10, TimeUnit.SECONDS);
            log.info("已自动接受好友请求，用户ID: " + event.getUserId());
        }
        lastUser = event.getUserId();
    }
}
