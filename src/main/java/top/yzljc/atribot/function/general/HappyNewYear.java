package top.yzljc.atribot.function.general;

import top.yzljc.atribot.configuration.ResourcesProperties;

import top.yzljc.atribot.chat.napcat.impl.MessageUtils;
import top.yzljc.atribot.chat.ImageType;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.NapcatCommandSender;
import top.yzljc.atribot.command.QQCommandSender;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.function.impl.PreImageGenerate;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigManager;

import java.util.Map;

public class HappyNewYear implements CommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(HappyNewYear.class);

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
            qq.sendMessage(data.url(), ImageType.URL);
        } else if (sender instanceof NapcatCommandSender nc) {
            if (!GroupConfigManager.isFeatureEnabled(nc.getGroupId(), "new_year")) return true;
            nc.sendMessage(data.url(), MessageUtils.ImageType.URL);
        }

        return true;
    }
}
