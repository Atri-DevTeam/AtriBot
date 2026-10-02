package top.yzljc.atribot.function.utils;

import top.yzljc.atribot.chat.napcat.AiChat;
import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.NapcatGroupMessageEvent;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigManager;
import top.yzljc.atribot.service.ai.AiProvider;

import java.io.UncheckedIOException;
import java.util.regex.Pattern;

/**
 * @Author YZ_Ljc_
 * @ClassName AtriChat
 * @Created_at 2026/07/08
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.napcat
 */
public class AtriChat implements Listener {
    private static final Pattern REPLY = Pattern.compile("\\[CQ:reply,id=([^,\\]]+)[^\\]]*]");
    private static final Pattern LEADING_MENTIONS = Pattern.compile("^(?:\\s*\\[CQ:at,[^\\]]*]\\s*)+");

    @EventHandler
    public void onGroupChat(NapcatGroupMessageEvent event) {
        var gid = event.getGroupId();
        if (event.getUser() == null || event.getUser().isBot()) return;
        boolean enabled = GroupConfigManager.isFeatureEnabled(gid, "atri_chat");
        if (!enabled && !event.getUser().hasPermission()) return;
        var userId = event.getUser().getUserId();
        var username = event.getUser().getUsername();
        var rawContent = event.getMessage().getContent();
        if (rawContent == null || rawContent.isBlank()) return;
        var quoted = REPLY.matcher(rawContent);
        var replyToMessageId = quoted.find() ? quoted.group(1) : "";
        var content = REPLY.matcher(rawContent).replaceAll("").trim();
        var command = LEADING_MENTIONS.matcher(content).replaceFirst("").trim();
        var messageId = event.getMessage().getMessageId();
        if (command.equals("-c")) {
            if (!event.getUser().hasPermission() && !event.getUser().isPlatformAdmin()) {
                GroupMessage.replyMessage(gid, messageId, "群聊记忆由大家共享，清除需要管理员权限。");
                return;
            }
            try {
                AiChat.clearContext(gid);
                GroupMessage.replyMessage(gid, messageId, "已清除本群的对话记录和经历记忆。");
            } catch (UncheckedIOException e) {
                GroupMessage.replyMessage(gid, messageId, "群聊记忆清除失败，请检查数据文件。");
            }
            return;
        }
        if (command.equals("-h") || command.equals("-help") || command.equals("-帮助")) {
            var help = """
                    AtriChat 群聊指令帮助:
                    -h: 显示此帮助信息
                    -c: 清除本群对话记录和经历记忆（需要管理员权限）
                    -<内容>: 和亚托莉聊天
                    本群共用聊天记忆，普通群消息也会进入上下文。
                    """.trim();
            GroupMessage.replyMessage(gid, messageId, help);
            return;
        }
        if (command.startsWith("-")) {
            var ai = AiChat.chat(AiProvider.PLAN_2, gid, userId, username,
                    messageId, replyToMessageId, command.substring(1));
            if (ai != null && !ai.isBlank()) {
                var sentMessageId = GroupMessage.replyMessage(gid, messageId, ai);
                AiChat.recordSentReply(gid, userId, messageId, sentMessageId);
            }
        } else if (enabled) {
            AiChat.observe(gid, userId, username, messageId, replyToMessageId, content);
        }
    }
}