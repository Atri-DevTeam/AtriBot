package top.yzljc.qqbot.functions;

import top.yzljc.qqbot.botservice.request.PostRequest;
import top.yzljc.qqbot.botservice.request.RequestType;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import top.yzljc.qqbot.botservice.thread.ThreadManager;
import top.yzljc.qqbot.chat.impl.MessageUtils;
import top.yzljc.qqbot.event.EventHandler;
import top.yzljc.qqbot.event.Listener;
import top.yzljc.qqbot.event.impl.FriendRequestEvent;
import top.yzljc.qqbot.utils.AtriHelp;
import top.yzljc.qqbot.utils.Logger;

public class AutoAccept implements Listener {
    private static long lastUser = 0;

    @EventHandler
    public void onFriendAdd(FriendRequestEvent event) {
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
            ThreadManager.schedule(() -> MessageUtils.sendPrivateForwardMessage(event.getUserId(), AtriHelp.getAtriHelp(), "ATRI - YZ_Ljc_ Bot 帮助文档", "查看项目帮助信息",
                    "项目开发说明", "指令帮助", "功能介绍"), 10, TimeUnit.SECONDS);
            Logger.info("已自动接受好友请求，用户ID: " + event.getUserId());
        }
        lastUser = event.getUserId();
    }
}
