package top.yzljc.atribot.function.general;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.chat.napcat.impl.MessageUtils;
import top.yzljc.atribot.chat.official.media.ImageType;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.function.general.impl.PreImageGenerate;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.service.runtime.ThreadManager;

/**
 * @Author YZ_Ljc_
 * @ClassName SponsorCommand
 * @Created_at 2026/06/03
 * @Project AtriBot
 * @Package top.yzljc.atribot.functions.official
 */
@Slf4j
public class SponsorCommand implements CommandExecutor {

    private static final String secret = Config.getInstance().getAtribotKeySecret();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String url = "https://www.yzljc.top/data/api/v2/atribot/function/sponsors?key=" + secret + "&" + System.currentTimeMillis();

        if (sender.getPlatform() == Platform.NAPCAT_GROUP) {
            sender.sendMessage(url, MessageUtils.ImageType.URL);
            return true;
        }

        ThreadManager.execute(() -> {

            int code = PreImageGenerate.create(url);
            if (code != 200) {
                sender.sendMessage("数据获取失败，请尝试重新执行指令，或稍后再试");
                log.warn("预生成赞助信息图片失败，状态码: {}", code);
            }
            sender.sendMessage(url, ImageType.URL);
        });

        return true;
    }
}