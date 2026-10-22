package top.yzljc.atribot.function.command;

import top.yzljc.atribot.function.tasks.pushtask.GroupDailyReportTask;
import top.yzljc.atricore.chat.official.RT;
import top.yzljc.atricore.command.Command;
import top.yzljc.atricore.command.CommandExecutor;
import top.yzljc.atricore.command.CommandSender;
import top.yzljc.atricore.command.QQCommandSender;
import top.yzljc.atricore.platform.Platform;

/**
 * @Author YZ_Ljc_
 * @ClassName GroupDailyReportCommand
 * @Created_at 2026/10/20
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.command
 */
public class GroupDailyReportCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof QQCommandSender qq) || qq.getPlatform() != Platform.OFFICIAL_GROUP) {
            sender.sendMessage("请在官机群聊中使用 /昨日发言排行");
            return true;
        }
        if (qq.isBot()) return true;
        GroupDailyReportTask.replyYesterday(qq.getGroupId(), RT.message(qq.getMessage().getMessageId()))
                .whenComplete((messageId, error) -> {
                    if (error != null) qq.sendMessage("群发言排行查询失败，请稍后重试");
                    else if (messageId == null) qq.sendMessage("昨日没有可统计的群消息");
                });
        return true;
    }
}
