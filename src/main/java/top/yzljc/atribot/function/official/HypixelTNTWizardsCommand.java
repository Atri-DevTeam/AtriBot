package top.yzljc.atribot.function.official;

import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.QQCommandSender;

/**
 * @Author YZ_Ljc_
 * @ClassName HypixelTNTWizardsStats
 * @Created_at 2026/08/15
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.official
 */
@Deprecated(since = "3.2.2")
public class HypixelTNTWizardsCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender instanceof QQCommandSender user) {
            user.sendMessage(TC.md("> 该指令已弃用，请使用二级指令 " + Markdown.enterCommand("/hyp wz", "/hyp wz") + "查询！"));
            return true;

//            String player;
//            if (args.length < 1) {
//                if (MinecraftBind.getDataByOpenId(user.getUserId()).uuid() != null) {
//                    player = MinecraftBind.getDataByOpenId(user.getUserId()).uuid();
//                } else {
//                    user.sendMessage("笨蛋喵，你没有绑定用户信息，请阅读帮助文档查看绑定事项。");
//                    return true;
//                }
//            } else {
//                player = args[0];
//            }
//            String msgId = user.sendMessage("正在查询相关数据，请稍等片刻...");
//
//            var d = PreImageGenerate.dump(ResourcesProperties.HYPIXEL_TNT_WIZARDS_API, Map.of("player", player));
//            user.recall(msgId);
//
//            if (!d.isError()) {
//                if (d.url() != null) {
//                    user.sendMessage(ImageComponent.imageOf(d.url()).setText("根据开放平台要求，自定义内容须审核后才能显示，请使用 /反馈 <用户名> 提交审核。"));
//                    return true;
//                }
//            }
//            user.sendMessage("在执行操作时出现错误: 请尝试重新查询！");
        }

        return true;
    }
}