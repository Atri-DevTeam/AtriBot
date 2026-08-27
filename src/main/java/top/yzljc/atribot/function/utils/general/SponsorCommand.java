package top.yzljc.atribot.function.utils.general;

import top.yzljc.atribot.configuration.ResourcesProperties;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.NapcatCommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.command.QQGuildCommandSender;
import top.yzljc.atribot.function.impl.PreImageGenerate;
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
@Deprecated(since = "3.2.2")
public class SponsorCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String apiUrl = ResourcesProperties.SPONSORS_API;

        if (sender instanceof NapcatCommandSender nc) {
            var data = PreImageGenerate.dump(apiUrl, Map.of());
            if (data.isError()) {
                String errMsg = data.errorMessage();
                nc.sendMessage("数据获取失败: " + errMsg);
                return true;
            }
            nc.sendMessage(ImageComponent.imageOf(data.url()));
            return true;
        }

        if (sender instanceof QQCommandSender qq) {
            ThreadManager.execute(() -> {
                var data = PreImageGenerate.dump(apiUrl, Map.of());
                if (data.isError()) {
                    String errMsg = data.errorMessage();
                    qq.sendMessage("数据获取失败: " + errMsg);
                    log.warn("赞助信息图片生成失败: {}", errMsg);
                    return;
                }
                qq.sendMessage(ImageComponent.imageOf(data.url()));
            });
        }

        if (sender instanceof QQGuildCommandSender guildSender) {
            ThreadManager.execute(() -> {
                var data = PreImageGenerate.dump(apiUrl, Map.of());
                if (data.isError()) {
                    String errMsg = data.errorMessage();
                    guildSender.sendMessage("数据获取失败: " + errMsg);
                    log.warn("赞助信息图片生成失败: {}", errMsg);
                    return;
                }
                guildSender.sendMessage(ImageComponent.imageOf(data.url()));
            });
        }

        return true;
    }
}