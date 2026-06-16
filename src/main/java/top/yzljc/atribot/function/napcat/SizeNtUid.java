package top.yzljc.atribot.function.napcat;

import top.yzljc.atribot.Atri;
import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.platform.Platform;

/**
 * @Author YZ_Ljc_
 * @ClassName SizeNtUid
 * @Created_at 2026/06/08
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.napcat
 */
public class SizeNtUid implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender.getPlatform() != Platform.NAPCAT_GROUP) return true;
        if (sender.getMentions().isEmpty()) {
            sender.sendMessage("请@目标账号！");
            return true;
        }
        String info = "Target Account Info\n" +
                "Uin: " + sender.getMentions().getFirst().getUserId() + "\n" +
                "GroupId: " + sender.getGroupId() + "\n" +
                "Uid: " + sender.getMentions().getFirst().getData().path("ntUid").asText();
        var msgId = sender.sendMessage(info);
        Atri.getInstance().getScheduler().runTaskLater(() -> GroupMessage.recallMessage(msgId), 30 * 1000);
        return true;
    }
}
