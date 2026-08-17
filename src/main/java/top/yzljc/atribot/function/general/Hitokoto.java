package top.yzljc.atribot.function.general;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.command.*;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.NapcatGroupMessageEvent;
import top.yzljc.atribot.event.events.OfficialGroupMessageCreateEvent;
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
 */
@Slf4j
public class Hitokoto implements CommandExecutor, Listener {

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

        if (sender instanceof QQCommandSender qq) {
            if (!d.isError()) {
                qq.sendMessage(ImageComponent.imageOf(d.url()));
            } else {
                qq.sendMessage(Identifier.HANDLER_ERROR);
            }
        }

        if (sender instanceof QQGuildCommandSender guildUser) {
            if (!d.isError()) {
                guildUser.sendMessage(ImageComponent.imageOf(d.url()));
            } else {
                guildUser.sendMessage(Identifier.HANDLER_ERROR);
            }
        }

        return true;
    }

    @EventHandler
    public void onGroupChat(NapcatGroupMessageEvent event) {
        if (Arrays.stream(ALIASES).anyMatch(alias -> alias.equals(event.getMessage().getContent().trim()))) {
            event.sendMessage(FetchHitokoto.get().replace(">", ""));
        }
    }

    @EventHandler
    public void onOfficialGroupChat(OfficialGroupMessageCreateEvent event) {
        if (Arrays.stream(ALIASES).anyMatch(alias -> alias.equals(event.getMessage().getContent().trim()))) {
            event.sendMessage(TC.md(FetchHitokoto.get()));
        }
    }
}
