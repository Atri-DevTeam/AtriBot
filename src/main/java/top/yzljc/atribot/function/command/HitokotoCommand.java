package top.yzljc.atribot.function.command;

import java.util.Arrays;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import top.yzljc.atricore.chat.ImageComponent;
import top.yzljc.atricore.command.*;
import top.yzljc.atricore.event.EventHandler;
import top.yzljc.atricore.event.Listener;
import top.yzljc.atricore.event.events.NapcatGroupMessageEvent;
import top.yzljc.atribot.function.impl.FetchHitokoto;
import top.yzljc.atribot.function.impl.PreImageGenerate;
import top.yzljc.atribot.service.request.OpenApi;

/**
 * @Author YZ_Ljc_
 * @ClassName Hitokoto
 * @Created_at 2026/06/02
 * @Project AtriBot
 * @Package top.yzljc.atribot.functions.overall
 * @Description NapCat 一言指令
 */
@Slf4j
public class HitokotoCommand implements CommandExecutor, Listener {

    private static final String[] ALIASES = {"hitokoto", "一言", "yiyan"};

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var d = PreImageGenerate.dump(OpenApi.get("bot.hitokoto"), Map.of(), sender.getPlatform());
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
}
