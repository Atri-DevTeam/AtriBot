package top.yzljc.atricore.event.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import top.yzljc.atricore.command.Command;
import top.yzljc.atricore.command.CommandSender;
import top.yzljc.atricore.event.Cancellable;
import top.yzljc.atricore.event.Event;

/**
 * 文本指令匹配成功、即将交给执行器时触发
 *
 * @Author YZ_Ljc_
 * @ClassName UserRunCommandEvent
 * @Created_at 2026/05/31
 * @Project AtriBot
 * @Package top.yzljc.atricore.event.events
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
