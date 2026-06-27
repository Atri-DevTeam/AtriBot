package top.yzljc.atribot.function.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.chat.napcat.impl.MessageUtils;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.service.timer.Schedule;
import top.yzljc.atribot.service.timer.ScheduleType;

import java.awt.*;

public class ManosabaDate implements CommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(ManosabaDate.class);
    private static final String GROUP_ID = String.valueOf(Config.getInstance().getManosabaGroupId());

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender.getPlatform() != Platform.NAPCAT_GROUP) return true;
        if (sender.getGroupId().equals(GROUP_ID)) {
            sender.sendMessage(ResourcesProperties.MANOSABA_DATE_IMG + "?key=" + Config.getInstance().getAtribotKeySecret() + "&" + System.currentTimeMillis(), MessageUtils.ImageType.URL);
        } else {
            sender.sendMessage("此指令无法在该群聊调用！");
        }
        return true;
    }

    @Schedule(time = "00:00:10", type = ScheduleType.DAILY)
    public static void sendAndNotifyToGroup() {
        GroupMessage.chatMessage(Config.getInstance().getManosabaGroupId(), ResourcesProperties.MANOSABA_DATE_IMG + "?key=" + Config.getInstance().getAtribotKeySecret() + "&" + System.currentTimeMillis(), MessageUtils.ImageType.URL);
    }
}
