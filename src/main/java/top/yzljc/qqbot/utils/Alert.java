package top.yzljc.qqbot.utils;

import top.yzljc.qqbot.chat.GroupMessage;
import top.yzljc.qqbot.config.Config;

/**
 * @Author YZ_Ljc_
 * @ClassName Alert
 * @Created_at 2026/05/21
 * @Project AtriBot
 * @Package top.yzljc.qqbot.utils
 */
public class Alert {

    public static void notify(String message) {
        GroupMessage.chatMessage(3199590352L, Config.getInstance().getDebugGroupId(), message, true);
    }
}