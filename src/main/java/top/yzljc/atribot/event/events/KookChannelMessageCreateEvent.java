package top.yzljc.atribot.event.events;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import top.yzljc.atribot.command.KookCommandSender;
import top.yzljc.atribot.event.Event;
import top.yzljc.atribot.platform.kook.KookMessage;
import top.yzljc.atribot.platform.kook.KookUser;

/**
 * @Author YZ_Ljc_
 * @ClassName KookChannelMessageCreateEvent
 * @Created_at 2026/10/04
 * @Project AtriMeow
 * @Package top.yzljc.atribot.event.events
 */
@Getter
@RequiredArgsConstructor
public final class KookChannelMessageCreateEvent extends Event {
    private final KookUser user;
    private final KookMessage message;
    private final KookCommandSender sender;

    public String replyMessage(String text) {
        return sender.sendMessage(text);
    }
}

