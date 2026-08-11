package top.yzljc.atribot.function.official;

import lombok.Getter;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.chat.official.button.Button;
import top.yzljc.atribot.chat.official.button.ButtonStyle;
import top.yzljc.atribot.chat.official.button.ButtonType;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.function.official.pushtask.*;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.PlatformRole;
import top.yzljc.atribot.platform.UnsupportedPlatform;

import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName PushTaskCommand
 * @Created_at 2026/06/14
 * @Project AtriBot
 * @Package top.yzljc.atribot.functions.official
 */
public class PushTaskCommand implements CommandExecutor {

    @Getter
    private static final List<PushTask> tasks = List.of(
            new MinecraftNewsCheckTask(),
            new CalendarTask(),
            new HypixelNewsTask(),
            new MemerAddWelcomeTask(),
            new SkyblockResourcePackTask(),
            new HypixelAlphaTask()
    );

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof QQCommandSender qq)) return true;

        String groupOpenId = qq.getGroupId();
        var platform = qq.getPlatform();

        if (platform != Platform.OFFICIAL_GROUP && platform != Platform.OFFICIAL_C2C) {
            qq.sendMessage("当前平台暂不支持推送任务调度！");
            return true;
        }

        if (args.length < 1) {
            StringBuilder markdown = new StringBuilder("**推送任务列表**\n\n");
            for (PushTask task : tasks) {
                if (platform.equals(Platform.OFFICIAL_GROUP)) {
                    if (task.isGroupEnable()) {
                        markdown.append(String.format("- %s - %s\n\n", getFunctionStatusIcon(platform, groupOpenId, task), getFunctionDescriptionText(task)));
                    }
                }
                if (platform.equals(Platform.OFFICIAL_C2C)) {
                    if (task.isC2cEnable()) {
                        markdown.append(String.format("- %s - %s\n\n", getFunctionStatusIcon(platform, qq.getUserId(), task), getFunctionDescriptionText(task)));
                    }
                }
            }
            markdown.append("> 点击名称可以查看详细描述\n> 点击按钮可以快速开关功能");

            Object feedbackButton = TC.keyboard(
                    List.of(
                            List.of(new Button("c1", "向开发者反馈", "/feedback ", false, ButtonStyle.BLUE, ButtonType.COMMAND))
                    )
            );

            qq.sendMessage(TC.md(markdown.toString()), feedbackButton);
            return true;
        }

        if (args.length > 1) {
            String cmd = args[0];
            String functionId = args[1];
            var task = get(functionId);

            if (task == null) {
                qq.sendMessage("未找到对应的推送任务！");
                return true;
            }

            switch (cmd) {
                case "详情" -> {
                    qq.sendMessage(task.getDescription(platform, platform.equals(Platform.OFFICIAL_GROUP) ? groupOpenId : qq.getUserId()), buttons(functionId));
                    return true;
                }
                case "开启" -> {
                    if (qq.getPlatform().equals(Platform.OFFICIAL_GROUP) && !(qq.getRole() == PlatformRole.ADMIN || qq.getRole() == PlatformRole.OWNER)) {
                        qq.sendMessage("只有群组管理员及以上用户才能调整有关设置！");
                        return true;
                    }
                    if (platform.equals(Platform.OFFICIAL_GROUP)) {
                        task.enable(platform, groupOpenId, qq.getUserId(), qq.getMessage().getMessageId());
                    } else {
                        task.enable(platform, qq.getUserId(), qq.getUserId(), qq.getMessage().getMessageId());
                    }
                    return true;
                }
                case "关闭" -> {
                    if (qq.getPlatform().equals(Platform.OFFICIAL_GROUP) && !(qq.getRole() == PlatformRole.ADMIN || qq.getRole() == PlatformRole.OWNER)) {
                        qq.sendMessage("只有群组管理员及以上用户才能调整有关设置！");
                        return true;
                    }
                    if (platform.equals(Platform.OFFICIAL_GROUP)) {
                        task.disable(platform, groupOpenId, qq.getUserId(), qq.getMessage().getMessageId());
                    } else {
                        task.disable(platform, qq.getUserId(), qq.getUserId(), qq.getMessage().getMessageId());
                    }
                    return true;
                }
            }
        }
        qq.sendMessage("未知的操作指令！");
        return true;
    }

    private static String getFunctionStatusIcon(Platform platform, String platformIdentifyId, PushTask task) {
        var on = Markdown.enterCommand("/推送任务 关闭 " + task.getFunctionId(), "\uD83D\uDFE2已启用");
        var off = Markdown.enterCommand("/推送任务 开启 " + task.getFunctionId(), "⚪未开启");
        if (platform.equals(Platform.OFFICIAL_GROUP)) {
            if (task.isGroupEnabled(platformIdentifyId)) {
                return on;
            } else {
                return off;
            }
        } else if (platform.equals(Platform.OFFICIAL_C2C)) {
            if (task.isUserEnabled(platformIdentifyId)) {
                return on;
            } else {
                return off;
            }
        } else {
            throw new UnsupportedPlatform(platform, "不支持的获取功能状态的平台");
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
        return new Button("c3", "返回列表", "/推送任务", true, ButtonStyle.BLUE, ButtonType.COMMAND);
    }
}