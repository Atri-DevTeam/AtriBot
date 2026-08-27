package top.yzljc.atribot.function.command;

import top.yzljc.atribot.command.*;
import top.yzljc.atribot.configuration.ResourcesProperties;

import top.yzljc.atribot.chat.ImageComponent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.function.impl.PreImageGenerate;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigManager;

import java.util.Map;

public class HappyNewYearCommand implements CommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(HappyNewYearCommand.class);

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var data = PreImageGenerate.dump(ResourcesProperties.HAPPY_NEW_YEAR_API, Map.of());

        if (data.isError()) {
            String errMsg = data.errorMessage();
            sender.sendMessage("数据获取失败: " + errMsg);
            log.warn("新年倒计时图片获取失败: {}", errMsg);
            return true;
        }

        if (sender instanceof QQCommandSender qq) {
            qq.sendMessage(ImageComponent.imageOf(data.url()));
        } else if (sender instanceof NapcatCommandSender nc) {
            if (!GroupConfigManager.isFeatureEnabled(nc.getGroupId(), "new_year")) return true;
            nc.sendMessage(ImageComponent.imageOf(data.url()));
        } else if (sender instanceof QQGuildCommandSender guild) {
            guild.sendMessage(ImageComponent.imageOf(data.url()));
        }

        return true;
    }
}
