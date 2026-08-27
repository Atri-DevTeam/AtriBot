package top.yzljc.atribot.function.command;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.chat.discord.DiscordEmbed;
import top.yzljc.atribot.command.*;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.NapcatGroupMessageEvent;
import top.yzljc.atribot.function.impl.FetchHitokoto;
import top.yzljc.atribot.function.impl.PreImageGenerate;
import top.yzljc.atribot.platform.Identifier;

import java.util.Arrays;
import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName Hitokoto
 * @Created_at 2026/06/02
 * @Project AtriBot
 * @Package top.yzljc.atribot.functions.overall
 * @Description 仅支持 Napcat 和 Discord 端
 */
@Slf4j
public class HitokotoCommand implements CommandExecutor, Listener, SlashCommandExecutor {

    private static final String[] ALIASES = {"hitokoto", "一言", "yiyan"};

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var d = PreImageGenerate.dump(ResourcesProperties.HITOKOTO_API, Map.of());
        if (sender instanceof NapcatCommandSender nc) {
            if (!d.isError()) {
                nc.sendMessage(ImageComponent.imageOf(d.url()));
            } else {
                nc.sendMessage(Identifier.HANDLER_ERROR);
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
    public boolean onSlashCommand(DiscordCommandSender sender, Command command, String label, SlashCommandArguments args) {

        var d = PreImageGenerate.dump(ResourcesProperties.HITOKOTO_API, Map.of());
        if (d.isError()) {
            sender.sendMessage(d.errorMessage());
            return true;
        }
        sender.sendEmbed(new DiscordEmbed().image(d.url()));
        return true;
    }
}
