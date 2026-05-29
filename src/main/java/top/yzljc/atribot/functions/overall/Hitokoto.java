package top.yzljc.atribot.functions.overall;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.impl.GroupMessageEvent;
import top.yzljc.atribot.event.impl.OfficialGroupMessageCreateEvent;
import top.yzljc.atribot.utils.FetchHitokoto;

import java.util.Arrays;

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
        if (label.equals("0")) {
            sender.reply(FetchHitokoto.get().replace(">", ""));
            return true;
        }
        sender.sendMessage(TC.md(FetchHitokoto.get()));
        return true;
    }

    @EventHandler
    public void onGroupChat(GroupMessageEvent event) {
        if (Arrays.stream(ALIASES).anyMatch(alias -> alias.equals(event.getRawMessage().trim()))) {
            event.getSender().reply(FetchHitokoto.get().replace(">", ""));
        }
    }

    @EventHandler
    public void onOfficialGroupChat(OfficialGroupMessageCreateEvent event) {
        if (Arrays.stream(ALIASES).anyMatch(alias -> alias.equals(event.getContent().trim()))) {
            event.sendMessage(TC.md(FetchHitokoto.get()));
        }
    }
}