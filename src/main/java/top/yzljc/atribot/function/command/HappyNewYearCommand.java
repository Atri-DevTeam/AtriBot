package top.yzljc.atribot.function.command;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import top.yzljc.atricore.chat.ImageComponent;
import top.yzljc.atricore.command.*;
import top.yzljc.atribot.function.impl.PreImageGenerate;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigManager;
import top.yzljc.atribot.service.request.OpenApi;

public class HappyNewYearCommand implements CommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(HappyNewYearCommand.class);

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var data = PreImageGenerate.dump(OpenApi.get("bot.happy-new-year"), Map.of(), sender.getPlatform());

        if (data.isError()) {
            String errMsg = data.errorMessage();
            sender.sendMessage("数据获取失败: " + errMsg);
            log.warn("新年倒计时图片获取失败: {}", errMsg);
            return true;
        }

        switch (sender) {
            case QQCommandSender qq -> qq.sendMessage(ImageComponent.imageOf(data.url()));
            case NapcatCommandSender nc -> {
                if (!GroupConfigManager.isFeatureEnabled(nc.getGroupId(), "new_year")) return true;
                nc.sendMessage(ImageComponent.imageOf(data.url()));
            }
            case QQGuildCommandSender guild -> guild.sendMessage(ImageComponent.imageOf(data.url()));
            default -> {
            }
        }

        return true;
    }
}
