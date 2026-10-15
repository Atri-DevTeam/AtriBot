package top.yzljc.atribot.function.tasks;

import top.yzljc.atribot.chat.napcat.NapcatDebugGroup;
import top.yzljc.atribot.command.KookCommandSender;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.UserRunCommandEvent;
import top.yzljc.atribot.platform.Platform;

/**
 * @Author YZ_Ljc_
 * @ClassName KookEventRecord
 * @Created_at 2026/10/05
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.tasks
 */
public final class KookEventRecord implements Listener {

    @EventHandler
    public void onRunCommand(UserRunCommandEvent event) {
        if (!(event.getSender() instanceof KookCommandSender kook)) return;
        if (!Config.getInstance().isNapcatEnabled()) return;

        String scene = kook.getPlatform() == Platform.KOOK_CHANNEL
                ? "服务器: %s, 频道: %s".formatted(kook.getGuildId(), kook.getChannelId())
                : "私信";
        String info = "[KOOK] 用户 %s (%s) 使用指令: %s%s %s (%s)".formatted(
                kook.getUsername().replaceAll("[\\r\\n\\t]", " "),
                kook.getUserId(),
                Config.getInstance().getCommandPrefix(),
                event.getCommandHeader(),
                String.join(" ", event.getArgs()),
                scene
        );
        NapcatDebugGroup.sendAsync(info);
    }
}
