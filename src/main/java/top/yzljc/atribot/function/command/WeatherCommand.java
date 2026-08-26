package top.yzljc.atribot.function.command;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.function.command.weather.WeatherDataService;
import top.yzljc.atribot.function.impl.PreImageGenerate;
import top.yzljc.atribot.platform.Platform;

@Slf4j
public final class WeatherCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof QQCommandSender qq)) {
            return true;
        }
        if (qq.getPlatform() == Platform.OFFICIAL_C2C) {
            qq.sendMessage("请前往群聊使用 /weather 查看群聊天气。");
            return true;
        }
        if (qq.getPlatform() != Platform.OFFICIAL_GROUP) {
            return true;
        }
        if (args.length > 0) {
            return false;
        }

        String loadingMessageId = qq.sendMessage("正在观测最近一天的群聊天气...");
        try {
            var report = WeatherDataService.load(qq.getGroupId(), qq.getMessage().getMessageId());
            var generated = PreImageGenerate.dump(ResourcesProperties.WEATHER_API, report.toPayload());
            if (loadingMessageId != null) qq.recall(loadingMessageId);

            if (generated != null && !generated.isError() && generated.url() != null) {
                qq.sendMessage(ImageComponent.imageOf(generated.url()));
                return true;
            }
            qq.sendMessage(generated != null && generated.isError()
                    ? generated.errorMessage()
                    : "群聊天气图片生成失败，请稍后重试。");
        } catch (Exception e) {
            if (loadingMessageId != null) qq.recall(loadingMessageId);
            log.error("群聊天气指令执行失败: groupOpenId={}", qq.getGroupId(), e);
            qq.sendMessage("群聊天气观测失败，请稍后重试。");
        }
        return true;
    }
}
