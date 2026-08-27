package top.yzljc.atribot.function.utils.official.minecraft;

import top.yzljc.atribot.configuration.ResourcesProperties;

import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.chat.official.button.Button;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.service.runtime.ThreadManager;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @Author YZ_Ljc_
 * @ClassName MinecraftNetwork
 * @Created_at 2026/05/25
 * @Project AtriBot
 * @Package top.yzljc.qqbot.functions.official.minecraft
 */
public abstract class MinecraftNetwork {

    private final boolean isSelf;
    private final String host;
    private final int port;
    private final String key;

    public MinecraftNetwork(boolean isSelf) {
        this.isSelf = isSelf;
        this.host = null;
        this.port = -1;
        this.key = null;
    }

    public MinecraftNetwork(boolean isSelf, String host, int port, String key) {
        this.isSelf = isSelf;
        this.host = host;
        this.port = port;
        this.key = key;
    }

    public boolean handleCommand(QQCommandSender sender, Command command, String label, String[] args) {

        ThreadManager.execute(() -> {
            if (args.length < 2) {
                sender.sendMessage("未指定指令内容！");
                return;
            }

            String server = args[0];
            String commandContent = commandsJoin(args);

            if (!hasPermission(sender, commandContent)) {
                if (this.isSelf) {
                    sender.sendMessage("你没有权限或未绑定社区服务器用户！");
                } else {
                    sender.sendMessage("你没有权限执行这个指令！");
                }
                return;
            }

            commandContent = modifyCommand(sender, args);
            String logs;
            if (this.isSelf) {
                logs = MinecraftRemote.sendCommand(commandContent);
            } else {
                logs = MinecraftRemote.sendCommandTo(this.host, this.port, this.key, commandContent);
            }

            List<String> extraCommands = extraCommand(sender, args);

            if (!extraCommands.isEmpty()) {
                for (String cmd : extraCommands) {
                    MinecraftRemote.sendCommand(cmd);
                }
            }

            String markdownLogs;
            if (logs == null) {
                markdownLogs = "![console #24px #24px](" + ResourcesProperties.CONSOLE_LOGO_IMG + ") **指令执行结果**\n\n" +
                        "> 无法获取指令执行结果，可能是服务器未响应或发生错误\n";
            } else if (sender.hasPermission()) {
                markdownLogs = "![console #24px #24px](" + ResourcesProperties.CONSOLE_LOGO_IMG + ") **指令执行结果**\n\n" +
                        "```\n" + logs + "\n```\n";
            } else {
                markdownLogs = "指令已送达，但对你隐藏";
            }

            sender.sendMessage(TC.md(markdownLogs), TC.keyboard(getButtons(sender, server, args)));
        });

        return true;
    }

    protected String commandsJoin(String[] args) {
        return Arrays.stream(args, 1, args.length).collect(Collectors.joining(" ")).trim();
    }

    protected List<String> extraCommand(QQCommandSender sender, String[] args) {
        return List.of();
    }

    protected String modifyCommand(QQCommandSender sender, String[] args) {
        return commandsJoin(args);
    }

    protected abstract List<List<Button>> getButtons(QQCommandSender sender, String server, String[] args);

    protected abstract boolean hasPermission(QQCommandSender sender, String commandContent);
}
