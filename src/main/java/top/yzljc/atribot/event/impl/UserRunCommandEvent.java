package top.yzljc.atribot.event.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.event.Cancellable;
import top.yzljc.atribot.event.Event;

/**
 * @Author YZ_Ljc_
 * @ClassName UserRunCommandEvent
 * @Created_at 2026/05/31
 * @Project AtriBot
 * @Package top.yzljc.atribot.event.impl
 */
@Getter
@AllArgsConstructor
public class UserRunCommandEvent extends Event implements Cancellable {
    private final CommandSender sender;
    private final Command command;
    private final String label;
    private final String commandHeader;
    private final String[] args;
    @Setter
    private boolean cancelled;
}
