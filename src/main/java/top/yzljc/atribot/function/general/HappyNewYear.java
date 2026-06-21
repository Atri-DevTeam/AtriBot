package top.yzljc.atribot.function.general;

import top.yzljc.atribot.configuration.ResourcesProperties;

import top.yzljc.atribot.chat.napcat.impl.MessageUtils;
import top.yzljc.atribot.chat.official.media.ImageType;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.function.general.impl.PreImageGenerate;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigManager;

public class HappyNewYear implements CommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(HappyNewYear.class);

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String url = ResourcesProperties.HAPPY_NEW_YEAR_API + "?key=" + Config.getInstance().getAtribotKeySecret() + "&timestamp=" + System.currentTimeMillis();

        int code = PreImageGenerate.create(url);
        if (code != 200) {
            sender.sendMessage("数据获取失败，请稍后重试，若反复发生请反馈给开发者，谢谢喵！");
        }

        if (sender.getPlatform() == Platform.OFFICIAL_GROUP || sender.getPlatform() == Platform.OFFICIAL_C2C) {
            sender.sendMessage(url, ImageType.URL);
        } else if (sender.getPlatform() == Platform.NAPCAT_GROUP) {
            if (!GroupConfigManager.isFeatureEnabled(sender.getGroupId(), "new_year")) return true;
            sender.sendMessage(url, MessageUtils.ImageType.URL);
        }

        return true;
    }
}
