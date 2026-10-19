package top.yzljc.atribot.function.command;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.function.tasks.pushtask.NewYearCountdownTask;
import top.yzljc.atricore.chat.ImageComponent;
import top.yzljc.atricore.chat.official.Markdown;
import top.yzljc.atricore.chat.official.TC;
import top.yzljc.atricore.command.*;
import top.yzljc.atribot.function.impl.PreImageGenerate;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigManager;
import top.yzljc.atribot.service.request.OpenApi;
import top.yzljc.atricore.platform.Platform;

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
            case QQCommandSender qq -> {

                boolean isSub = false;

                if (qq.getPlatform() == Platform.OFFICIAL_GROUP) {
                    if (OfficialGroups.isFunctionEnabled(qq.getGroupId(), NewYearCountdownTask.INSTANCE.getFunctionId())) {
                        isSub = true;
                    }
                } else {
                    if (OfficialUsers.isFunctionEnabled(qq.getUserId(), NewYearCountdownTask.INSTANCE.getFunctionId())) {
                        isSub = true;
                    }
                }

                var md = TC.md(Markdown.img(data.url(), data.width(), data.height()) + "\n\n> 当前解冻进度：" + String.format("%.2f", NewYearCountdownTask.getThawProgress()) + "%");
                qq.sendMessage(md.setKeyboard(NewYearCountdownTask.getButtons(isSub)));
            }
            case NapcatCommandSender nc -> {
                if (!GroupConfigManager.isFeatureEnabled(nc.getGroupId(), "new_year")) return true;
                nc.sendMessage(ImageComponent.imageOf(data.url()));
            }
            case QQGuildCommandSender guild -> guild.sendMessage(ImageComponent.imageOf(data.url()).setText("当前解冻进度：" + String.format("%.2f", NewYearCountdownTask.getThawProgress()) + "%"));
            default -> {
            }
        }

        return true;
    }
}
