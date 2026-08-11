package top.yzljc.atribot.utils.update;

import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.NapcatCommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.UserRunCommandEvent;
import top.yzljc.atribot.platform.Identifier;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.qq.QQBot;
import top.yzljc.atribot.utils.FormatTools;

import java.util.Arrays;

/**
 * @Author YZ_Ljc_
 * @ClassName UpdatePushCommand
 * @Created_at 2026/06/24
 * @Project AtriMeow
 * @Package top.yzljc.atribot.utils.update
 */
public class UpdatePushCommand implements Listener, CommandExecutor {

    private static final String layout = QQBot.BOT_NAME + "近期更新日志:\n";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission()) {
            sender.sendMessage(Identifier.NO_PERMISSION);
            return true;
        }

        if (args.length == 0) {
            sendStatus(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "set", "设置" -> {
                if (args.length < 2) {
                    return false;
                }
                return setNotice(sender, Arrays.copyOfRange(args, 1, args.length));
            }
            case "clear", "clean", "remove", "删除", "清空", "关闭" -> {
                UpdateNoticeRecord.clear();
                sender.sendMessage("更新推送已清空，后续不会再自动通知。");
                return true;
            }
            case "status", "info", "查看", "状态" -> {
                sendStatus(sender);
                return true;
            }
            default -> {
                sender.sendMessage("""
                        请选择正确的子命令：
                        1. set/设置 <内容> - 设置更新推送内容
                        2. clear/clean/remove/删除/清空/关闭 - 清空更新推送
                        3. status/info/查看/状态 - 查看当前更新推送状态""".trim());
                return true;
            }
        }
    }

    private boolean setNotice(CommandSender sender, String[] args) {
        String text = parseLineBreaks(String.join(" ", args)).trim();
        if (text.isBlank()) {
            sender.sendMessage("更新推送内容不能为空。");
            return true;
        }

        UpdateNoticeRecord.setText(text);
        sender.sendMessage("更新推送已设置，将在群内下一次触发命令时自动通知；旧通知记录已覆盖。");
        return true;
    }

    private String parseLineBreaks(String text) {
        return text.replace("\\r\\n", "\n").replace("\\n", "\n");
    }

    private void sendStatus(CommandSender sender) {
        if (!UpdateNoticeRecord.hasNotice()) {
            sender.sendMessage("当前没有待推送的更新通知。");
            return;
        }

        sender.sendMessage("当前更新推送：\n"
                + "发布时间: " + FormatTools.formatTimestampMilli(UpdateNoticeRecord.getCreatedAt()) + "\n"
                + "已通知群数: " + UpdateNoticeRecord.getNotifiedGroups().size() + "\n\n"
                + UpdateNoticeRecord.getText());
    }

    @EventHandler
    public void onCommandExecuted(UserRunCommandEvent event) {
        if (event.getCommand() != null && "update".equalsIgnoreCase(event.getCommand().getName())) return;

        String groupId;
        if (event.getSender() instanceof QQCommandSender qq) {
            if (qq.getPlatform() != Platform.OFFICIAL_GROUP) return;
            groupId = qq.getGroupId();
        } else if (event.getSender() instanceof NapcatCommandSender nc) {
            if (nc.getPlatform() != Platform.NAPCAT_GROUP) return;
            groupId = nc.getGroupId();
        } else {
            return;
        }

        if (groupId != null && UpdateNoticeRecord.shouldNotify(groupId)) {
            String pushContent = layout + "> " + FormatTools.formatTimestampMilli(UpdateNoticeRecord.getCreatedAt()) + "\n\n"
                    + UpdateNoticeRecord.getText() + "\n\n遇到了一些问题？使用/feedback向开发者反馈！";
            event.getSender().sendMessage(pushContent);
            UpdateNoticeRecord.markNotified(groupId);
        }
    }
}
