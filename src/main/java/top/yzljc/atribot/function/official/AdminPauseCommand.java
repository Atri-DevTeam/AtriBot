package top.yzljc.atribot.function.official;

import top.yzljc.atribot.chat.official.ChatService;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.platform.Identifier;
import top.yzljc.atribot.platform.official.OfficialBot;

/**
 * @Author YZ_Ljc_
 * @ClassName AdminPauseCommand
 * @Created_at 2026/07/27
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.official
 */
public class AdminPauseCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!sender.hasPermission()) {
            sender.sendMessage(Identifier.NO_PERMISSION);
            return true;
        }

        if (ChatService.isEmergencyPaused()) {
            ChatService.setEmergencyPaused(false);
            sender.sendMessage(OfficialBot.BOT_NAME + "已解除暂停状态，机器人恢复正常运行！");
        } else {
            ChatService.setEmergencyPaused(true);
            sender.sendMessage(OfficialBot.BOT_NAME + "已被暂停使用，维护状态启用！");
        }
        return true;
    }
}