package top.yzljc.qqbot.official.function;

import top.yzljc.qqbot.AtriBot;
import top.yzljc.qqbot.command.Command;
import top.yzljc.qqbot.command.CommandExecutor;
import top.yzljc.qqbot.command.CommandSender;
import top.yzljc.qqbot.official.impl.MinecraftNetwork;
import top.yzljc.qqbot.official.service.CommandButton;
import top.yzljc.qqbot.official.service.QQBotMessageService;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @Author YZ_Ljc_
 * @ClassName NetworkHandler
 * @Created_at 2026/05/23
 * @Project AtriBot
 * @Package top.yzljc.qqbot.official.function
 */
public class NetworkHandler implements CommandExecutor {

    private static final QQBotMessageService service = AtriBot.getInstance().getMessageService();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sender.replyText(label, "未指定目标服务器！");
            return true;
        }
        String server = args[0];
        if (server.equals("atri")) {
            if (args.length < 2) {
                sender.replyText(label, "未指定指令内容！");
                return true;
            }
            if (!sender.hasPermission("rcon.atri")) {
                boolean isAllowedCommand = (args.length == 2 && (args[1].equals("tps") || args[1].equals("list") || args[1].equals("unban")));
                if (!isAllowedCommand) {
                    sender.replyText(label, "你没有权限执行这个指令！");
                    return true;
                }
            }
            String commandContent = Arrays.stream(args, 1, args.length).collect(Collectors.joining(" "));
            String logs = MinecraftNetwork.sendCommand(commandContent);
            if (args.length > 2 && args[1].equals("unban")) {
                MinecraftNetwork.sendCommand("pardon " + args[2]);
            }
            String markdownLogs = "![console #24px #24px](https://www.yzljc.top/img/console-logo.png) **指令执行结果**\n\n";
            if (logs != null) {
                markdownLogs += "```\n" + logs + "\n```\n";
            } else {
                markdownLogs += "> 无法获取指令执行结果，可能是服务器未响应或发生错误\n";
            }

            List<CommandButton> custom = List.of(
                    new CommandButton("c1", "喊话", "/rc " + server + " bc ", false, 0, 2),
                    new CommandButton("c2", "在线人数", "/rc " + server + " list", true, 0, 2),
                    new CommandButton("c3", "TPS", "/rc " + server + " tps", true, 0, 2),
                    new CommandButton("c4", "解封", "/rc " + server + " unban ", false, 0, 2)
            );

            sender.replyMarkdown(label, markdownLogs, getButtons(server, commandContent, custom));

            return true;
        }
        sender.replyText(label, "未知服务器: " + server);
        return true;
    }

    private static Object getButtons(String serverId, String commandContent, List<CommandButton> customButtons) {
        if (customButtons == null) {
            return service.buildCmdKeyboard(List.of(
                    List.of(new CommandButton("s1", "重新执行", "/rc " + serverId + " " + commandContent, true, 1, 2)),
                    List.of(new CommandButton("s2", "发送另一个指令", "/rc " + serverId + " ", false, 1, 2))
            ));
        }
        return service.buildCmdKeyboard(List.of(
                customButtons,
                List.of(new CommandButton("s1", "重新执行", "/rc " + serverId + " " + commandContent, true, 1, 2)),
                List.of(new CommandButton("s2", "发送另一个指令", "/rc " + serverId + " ", false, 1, 2))
        ));
    }
}