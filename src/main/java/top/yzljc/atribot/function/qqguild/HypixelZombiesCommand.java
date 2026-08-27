package top.yzljc.atribot.function.qqguild;

import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.chat.discord.DiscordEmbed;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.command.*;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.function.impl.PreImageGenerate;
import top.yzljc.atribot.function.utils.official.minecraft.MinecraftBind;

import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName HypixelZombies
 * @Created_at 2026/08/19
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.official
 */
public class HypixelZombiesCommand implements CommandExecutor, SlashCommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender instanceof QQCommandSender user) {
            user.sendMessage(TC.md("> 该指令在当前场景下已弃用，请使用二级指令 " + Markdown.enterCommand("/hyp zs", "/hyp zs") + "查询！"));
            return true;
        }

        if (sender instanceof QQGuildCommandSender user) {
            String player;
            if (args.length < 1) {
                if (MinecraftBind.getDataByOpenId(user.getUserOpenId()).uuid() != null) {
                    player = MinecraftBind.getDataByOpenId(user.getUserOpenId()).uuid();
                } else {
                    user.sendMessage("笨蛋喵，你没有绑定用户信息，请阅读帮助文档查看绑定事项，或指定一个查询玩家。");
                    return true;
                }
            } else {
                player = args[0];
            }
            user.sendMessage("正在查询相关数据，请稍等片刻...");

            var d = PreImageGenerate.dump(ResourcesProperties.HYPIXEL_ZOMBIES_API, Map.of("player", player));

            if (!d.isError()) {
                if (d.url() != null) {
                    user.sendMessage(ImageComponent.imageOf(d.url()).setText("根据开放平台要求，自定义内容须审核后才能显示，请使用 /反馈 <用户名> 提交审核。"));
                    return true;
                }
            }
            user.sendMessage("在执行操作时出现错误: 请尝试重新查询！");
        }

        return true;
    }

    @Override
    public boolean onSlashCommand(DiscordCommandSender sender, Command command, String label,
                                  SlashCommandArguments args) {
        String player = args.getString("player", "").trim();
        if (player.isBlank()) {
            sender.sendEphemeralMessage("请指定要查询的 Minecraft 玩家名或 UUID。");
            return true;
        }

        sender.sendMessage("正在查询相关数据，请稍等片刻...");
        var data = PreImageGenerate.dump(ResourcesProperties.HYPIXEL_ZOMBIES_API, Map.of("player", player));
        if (data.isError() || data.url() == null) {
            sender.sendMessage(data.isError() ? data.errorMessage() : "在执行操作时出现错误，请稍后重试。");
            return true;
        }
        sender.sendEmbed(new DiscordEmbed().title("Hypixel 僵尸末日数据").image(data.url()));
        return true;
    }
}
