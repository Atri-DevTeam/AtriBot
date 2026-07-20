package top.yzljc.atribot.platform.discord;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.command.CommandFeature;
import top.yzljc.atribot.command.CommandManager;
import top.yzljc.atribot.command.DiscordSlashCommandSender;
import top.yzljc.atribot.command.SlashCommandExecutor;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.DiscordMessageCreateEvent;
import top.yzljc.atribot.event.events.DiscordSlashCommandEvent;

@Slf4j
public class DiscordEvents implements Listener {

    @EventHandler
    public void onSlashCommand(DiscordSlashCommandEvent event) {
        log.info("[Discord] slash command: guild={}, channel={}, user={}, /{}",
                event.getGuildId(),
                event.getChannelId(),
                event.getUser().getUsername(),
                event.getCommandName());

        CommandFeature command = CommandManager.getCommand(event.getCommandName());
        if (command == null || command.getExecutor() == null) {
            log.warn("Discord slash command /{} is not registered in CommandManager", event.getCommandName());
            return;
        }

        if (!(command.getExecutor() instanceof SlashCommandExecutor slashExecutor)) {
            log.warn("Discord slash command /{} has no SlashCommandExecutor", event.getCommandName());
            return;
        }

        DiscordSlashCommandSender sender = new DiscordSlashCommandSender(
                event.getUser(),
                event.getApplicationId(),
                event.getInteractionId(),
                event.getToken()
        );
        slashExecutor.onSlashCommand(sender, command, event.getCommandName(), event.getArgs());
    }
}
