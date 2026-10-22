package top.yzljc.atribot.function.qqguild;

import java.util.Map;

import top.yzljc.atribot.auth.UnifiedAuthentication;
import top.yzljc.atricore.chat.ImageComponent;
import top.yzljc.atricore.chat.official.Markdown;
import top.yzljc.atricore.chat.official.TC;
import top.yzljc.atricore.command.*;
import top.yzljc.atribot.function.impl.PreImageGenerate;
import top.yzljc.atribot.service.request.OpenApi;

/**
 * @Author YZ_Ljc_
 * @ClassName HypixelZombies
 * @Created_at 2026/08/19
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.official
 */
public class HypixelZombiesCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {


        if (sender instanceof QQCommandSender user) {
            user.sendMessage(TC.md("> 该指令在当前场景下已弃用，请使用二级指令 " + Markdown.enterCommand("/hyp zs", "/hyp zs") + "查询！"));
            return true;
        }

        if (sender instanceof QQGuildCommandSender user) {
            String player;
            if (args.length < 1) {
                var account = UnifiedAuthentication.findByQqUserOpenId(user.getUnionOpenId());
                if (account != null && account.minecraftUuid() != null) {
                    player = account.minecraftUuid();
                } else {
                    user.sendMessage("笨蛋喵，你没有绑定用户信息，请阅读帮助文档查看绑定事项，或指定一个查询玩家。");
                    return true;
                }
            } else {
                player = args[0];
            }
            user.sendMessage("正在查询相关数据，请稍等片刻...");

            var d = PreImageGenerate.dump(OpenApi.get("bot.hypixel.zombies"), Map.of("player", player));

            if (!d.isError()) {
                if (d.url() != null) {
                    user.sendMessage(ImageComponent.imageOf(d.url()).setText("根据开放平台要求，自定义内容须审核后才能显示，请使用 /反馈 <用户名> 提交审核。"));
                    return true;
                }
            }
            user.sendMessage(d.isError() ? d.errorMessage() : "图片地址无效，请稍后重试");
        }

        return true;
    }

}
