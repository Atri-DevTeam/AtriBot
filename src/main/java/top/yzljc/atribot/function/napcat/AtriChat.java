package top.yzljc.atribot.function.napcat;

import top.yzljc.atribot.chat.napcat.AiChat;
import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.NapcatGroupMessageEvent;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigManager;
import top.yzljc.atribot.service.ai.AiProvider;

/**
 * @Author YZ_Ljc_
 * @ClassName AtriChat
 * @Created_at 2026/07/08
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.napcat
 */
public class AtriChat implements Listener {

    @EventHandler
    public void onGroupChat(NapcatGroupMessageEvent event) {
        var gid = event.getGroupId();
        if (!GroupConfigManager.isFeatureEnabled(gid, "atri_chat") && !event.getUser().hasPermission()) return;
        var userId = event.getUser().getUserId();
        var username = event.getUser().getUsername();
        var content = event.getMessage().getContent();
        var messageId = event.getMessage().getMessageId();
        if (content.equals("-c")) {
            GroupMessage.replyMessage(gid, messageId, "已清除对话上下文");
            AiChat.clearContext(gid, userId);
            return;
        }
        if (content.equals("-h") || content.equals("-help") || content.equals("-帮助")) {
            var help = """
                    AtriChat 聊天指令帮助:
                    -h: 显示此帮助信息
                    -c: 清除对话上下文
                    -<内容>: 聊天
                    """.trim();
            GroupMessage.replyMessage(gid, messageId, help);
            return;
        }
        if (content.startsWith("-")) {
            var ai = AiChat.chat(AiProvider.OPENCODE, gid, userId, username, content);
            if (ai != null) {
                GroupMessage.replyMessage(gid, messageId, ai);
            }
        }
    }
}