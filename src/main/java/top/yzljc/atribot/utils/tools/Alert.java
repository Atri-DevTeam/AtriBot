package top.yzljc.atribot.utils.tools;

import top.yzljc.atricore.core.CoreConfig;

import top.yzljc.atricore.chat.napcat.GroupMessage;

/**
 * @Author YZ_Ljc_
 * @ClassName Alert
 * @Created_at 2026/05/21
 * @Project AtriBot
 * @Package top.yzljc.qqbot.utils
 */
public class Alert {

    public static void notify(String message) {
        if (!CoreConfig.getInstance().getSettings().isNapcatEnabled()) return;
        GroupMessage.chatMessage("3199590352", CoreConfig.getInstance().getSettings().getNapcatDebugGroupUin(), message, true);
    }

    public static void notify(String message, boolean at) {
        if (!CoreConfig.getInstance().getSettings().isNapcatEnabled()) return;
        if (!at) {
            GroupMessage.chatMessage(CoreConfig.getInstance().getSettings().getNapcatDebugGroupUin(), message);
        } else {
            notify(message);
        }
    }
}