package top.yzljc.atribot.function.general;

import top.yzljc.atribot.configuration.ResourcesProperties;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.chat.napcat.impl.MessageUtils;
import top.yzljc.atribot.chat.official.media.ImageType;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.function.general.impl.PreImageGenerate;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.service.runtime.ThreadManager;

import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName SponsorCommand
 * @Created_at 2026/06/03
 * @Project AtriBot
 * @Package top.yzljc.atribot.functions.official
 */
@Slf4j
public class SponsorCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String apiUrl = ResourcesProperties.SPONSORS_API;

        if (sender.getPlatform() == Platform.NAPCAT_GROUP) {
            var data = PreImageGenerate.dump(apiUrl, Map.of());
            if (data.isError()) {
                String errMsg = data.errorMessage();
                sender.sendMessage("数据获取失败: " + errMsg);
                return true;
            }
            sender.sendMessage(data.url(), MessageUtils.ImageType.URL);
            return true;
        }

        ThreadManager.execute(() -> {
            var data = PreImageGenerate.dump(apiUrl, Map.of());
            if (data.isError()) {
                String errMsg = data.errorMessage();
                sender.sendMessage("数据获取失败: " + errMsg);
                log.warn("赞助信息图片生成失败: {}", errMsg);
                return;
            }
            sender.sendMessage(data.url(), ImageType.URL);
        });

        return true;
    }
}