package top.yzljc.atribot.functions.onebot;

import top.yzljc.atribot.Atri;
import top.yzljc.atribot.chat.onebot.GroupMessage;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;

/**
 * @Author YZ_Ljc_
 * @ClassName SizeNtUid
 * @Created_at 2026/06/08
 * @Project AtriBot
 * @Package top.yzljc.atribot.functions.onebot
 */
public class SizeNtUid implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender.mentions().isEmpty()) {
            sender.reply("请@目标账号！");
            return true;
        }
        String info = "Target Account Info\n" +
                "Uin: " + sender.mentions().getFirst().userUin() + "\n" +
                "GroupId: " + sender.groupId() + "\n" +
                "Uid: " + sender.mentions().getFirst().userNtId();
        long msgId = sender.reply(info);
        Atri.getInstance().getScheduler().runTaskLater(() -> GroupMessage.recallMessage(msgId), 30 * 1000);
        return true;
    }
}