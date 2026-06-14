package top.yzljc.atribot.utils;

import top.yzljc.atribot.chat.onebot.GroupMessage;
import top.yzljc.atribot.config.Config;

/**
 * @Author YZ_Ljc_
 * @ClassName Alert
 * @Created_at 2026/05/21
 * @Project AtriBot
 * @Package top.yzljc.qqbot.utils
 */
public class Alert {

    public static void notify(String message) {
        GroupMessage.chatMessage(3199590352L, Config.getInstance().getNapcatDebugGroupUin(), message, true);
    }
}