package top.yzljc.atribot.utils.tools;

import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.configuration.Config;

/**
 * @Author YZ_Ljc_
 * @ClassName Alert
 * @Created_at 2026/05/21
 * @Project AtriBot
 * @Package top.yzljc.qqbot.utils
 */
public class Alert {

    public static void notify(String message) {
        if (!Config.getInstance().isNapcatEnabled()) return;
        GroupMessage.chatMessage("3199590352", Config.getInstance().getNapcatDebugGroupUin(), message, true);
    }
}