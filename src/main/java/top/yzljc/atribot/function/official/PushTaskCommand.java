package top.yzljc.atribot.function.official;

import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.chat.official.button.Button;
import top.yzljc.atribot.chat.official.button.ButtonStyle;
import top.yzljc.atribot.chat.official.button.ButtonType;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.function.official.pushtask.CalendarTask;
import top.yzljc.atribot.function.official.pushtask.MinecraftNewsCheckTask;
import top.yzljc.atribot.function.official.pushtask.PushTask;

import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName PushTaskCommand
 * @Created_at 2026/06/14
 * @Project AtriBot
 * @Package top.yzljc.atribot.functions.official
 */
public class PushTaskCommand implements CommandExecutor {

    private static final List<PushTask> tasks = List.of(
            new MinecraftNewsCheckTask(),
            new CalendarTask()
    );

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        String groupOpenId = sender.getGroupId();

        if (label.equals("1")) {
            sender.sendMessage(TC.md("> 推送任务只在群聊中有效，等待官方开放私聊主动消息后更新!"));
            return true;
        }

        if (args.length < 1) {
            StringBuilder markdown = new StringBuilder("**📋 推送任务列表**\n\n");
            for (PushTask task : tasks) {
                markdown.append(String.format("- %s - %s\n\n", getFunctionStatusIcon(groupOpenId, task), getFunctionDescriptionText(task)));
            }
            markdown.append("> 点击名称可以查看详细描述\n> 点击按钮可以快速开关功能");

            Object feedbackButton = TC.keyboard(
                    List.of(
                            List.of(new Button("c1", "向开发者反馈", "/feedback ", false, ButtonStyle.BLUE, ButtonType.COMMAND))
                    )
            );

            sender.sendMessage(TC.md(markdown.toString()), feedbackButton);
            return true;
        }

        if (args.length > 1) {
            String cmd = args[0];
            String functionId = args[1];
            var task = get(functionId);

            if (task == null) {
                sender.sendMessage("未找到对应的推送任务！");
                return true;
            }

            switch (cmd) {
                case "详情" -> {
                    sender.sendMessage(task.getDescription(groupOpenId), buttons(functionId));
                    return true;
                }
                case "开启" -> {
                    sender.sendMessage(task.enable(groupOpenId, sender.getUserId()), TC.keyboard(List.of(List.of(back()))));
                    return true;
                }
                case "关闭" -> {
                    sender.sendMessage(task.disable(groupOpenId, sender.getUserId()), TC.keyboard(List.of(List.of(back()))));
                    return true;
                }
            }
        }
        sender.sendMessage("未知的操作指令！");
        return true;
    }

    private static String getFunctionStatusIcon(String groupOpenId, PushTask task) {
        if (task.isGroupEnabled(groupOpenId)) {
            return Markdown.enterCommand("/推送任务 关闭 " + task.getFunctionId(), "\uD83D\uDFE2已启用");
        } else {
            return Markdown.enterCommand("/推送任务 开启 " + task.getFunctionId(), "⚪未开启");
        }
    }

    private static String getFunctionDescriptionText(PushTask task) {
        return Markdown.enterCommand("/推送任务 详情 " + task.getFunctionId(), task.getDisplayName());
    }

    private static PushTask get(String functionId) {
        for (PushTask task : tasks) {
            if (task.getFunctionId().equals(functionId)) {
                return task;
            }
        }
        return null;
    }

    private static Object buttons(String functionId) {
        PushTask task = get(functionId);
        if (task == null) return null;

        Button toggleOffButton = new Button("c1", "关闭任务", "/推送任务 关闭 " + functionId, true, ButtonStyle.RED, ButtonType.COMMAND);
        Button toggleOnButton = new Button("c2", "开启任务", "/推送任务 开启 " + functionId, true, ButtonStyle.BLUE, ButtonType.COMMAND);

        return TC.keyboard(
                List.of(
                        List.of(toggleOnButton, toggleOffButton),
                        List.of(back())
                )
        );
    }

    private static Button back() {
        return new Button("c3", "返回列表", "/推送任务", true, ButtonStyle.BLUE_WITH_BACKGROUND, ButtonType.COMMAND);
    }
}