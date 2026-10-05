package top.yzljc.atribot.function.command;

import java.util.Arrays;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.chat.discord.DiscordEmbed;
import top.yzljc.atribot.command.*;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.NapcatGroupMessageEvent;
import top.yzljc.atribot.function.impl.FetchHitokoto;
import top.yzljc.atribot.function.impl.PreImageGenerate;
import top.yzljc.atribot.service.request.OpenApi;

/**
 * @Author YZ_Ljc_
 * @ClassName Hitokoto
 * @Created_at 2026/06/02
 * @Project AtriBot
 * @Package top.yzljc.atribot.functions.overall
 * @Description 支持 Napcat、Discord 和 KOOK
 */
@Slf4j
public class HitokotoCommand implements CommandExecutor, Listener, SlashCommandExecutor {

    private static final String[] ALIASES = {"hitokoto", "一言", "yiyan"};

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var d = PreImageGenerate.dump(OpenApi.get("bot.hitokoto"), Map.of(), sender.getPlatform());
        if (sender instanceof KookCommandSender kook) {
            if (d.isError()) kook.sendMessage(d.errorMessage());
            else kook.sendMessage(ImageComponent.imageOf(d.url()));
            return true;
        }
        if (sender instanceof NapcatCommandSender nc) {
            if (!d.isError()) {
                nc.sendMessage(ImageComponent.imageOf(d.url()));
            } else {
                nc.sendMessage(d.errorMessage());
            }
            return true;
        }

        return true;
    }

    @EventHandler
    public void onGroupChat(NapcatGroupMessageEvent event) {
        if (Arrays.stream(ALIASES).anyMatch(alias -> alias.equals(event.getMessage().getContent().trim()))) {
            event.sendMessage(FetchHitokoto.get().replace(">", ""));
        }
    }

    @Override
    public boolean onCommand(SlashCommandSender slashSender, Command command, String label, SlashCommandArguments args) {
        if (!(slashSender instanceof DiscordCommandSender sender)) return true;

        var d = PreImageGenerate.dump(OpenApi.get("bot.hitokoto"), Map.of(), sender.getPlatform());
        if (d.isError()) {
            sender.sendMessage(d.errorMessage());
            return true;
        }
        sender.sendEmbed(new DiscordEmbed().image(d.url()));
        return true;
    }
}
