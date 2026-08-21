package top.yzljc.atribot.function.official;

import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.function.impl.PreImageGenerate;
import top.yzljc.atribot.function.official.minecraft.MinecraftBind;
import top.yzljc.atribot.function.official.minecraft.MinecraftWhitelist;
import top.yzljc.atribot.service.request.HttpService;
import top.yzljc.atribot.utils.tools.FetchMinecraftProfile;

import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName HypixelTNTWizardsStats
 * @Created_at 2026/08/15
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.official
 */
public class HypixelTNTWizardsStats implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender instanceof QQCommandSender user) {
            String player;
            boolean isAllowedName = false;
            if (args.length < 1) {
                if (MinecraftBind.getDataByOpenId(user.getUserId()).uuid() != null) {
                    player = MinecraftBind.getDataByOpenId(user.getUserId()).uuid();
                } else {
                    user.sendMessage("笨蛋喵，你没有绑定用户信息，请阅读帮助文档查看绑定事项。");
                    return true;
                }
            } else {
                player = args[0];
            }
            isAllowedName = MinecraftWhitelist.isNameWhitelisted(FetchMinecraftProfile.getUsernameByUuid(player));

            String msgId = user.sendMessage("正在查询相关数据，请稍等片刻...");

            var d = PreImageGenerate.dump(ResourcesProperties.HYPIXEL_TNT_WIZARDS_API, Map.of("player", player, "is_allowed_name", isAllowedName));
            user.recall(msgId);

            if (!d.isError()) {
                if (d.url() != null) {
                    ImageComponent image = ImageComponent.imageOf(d.url());
                    if (!isAllowedName) image.setText("为保障内容合规，用户名和皮肤须审查后才能放出，使用 /加白 用户名 提交审查。");
                    user.sendMessage(image);
                    return true;
                }
            }
            user.sendMessage("在执行操作时出现错误: 请尝试重新查询！");
        }

        return true;
    }
}