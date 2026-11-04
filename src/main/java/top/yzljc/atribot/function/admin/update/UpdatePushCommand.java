package top.yzljc.atribot.function.admin.update;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.Atri;
import top.yzljc.atricore.chat.official.TC;
import top.yzljc.atricore.command.Command;
import top.yzljc.atricore.command.CommandExecutor;
import top.yzljc.atricore.command.CommandSender;
import top.yzljc.atricore.command.QQCommandSender;
import top.yzljc.atricore.event.EventHandler;
import top.yzljc.atricore.event.Listener;
import top.yzljc.atricore.event.events.UserRunCommandEvent;
import top.yzljc.atricore.event.events.OfficialGroupMemberAddEvent;
import top.yzljc.atricore.event.events.OfficialGroupMessageCreateEvent;
import top.yzljc.atricore.event.events.OfficialGroupAtMessageCreateEvent;
import top.yzljc.atricore.platform.qq.QQUser;
import top.yzljc.atricore.platform.Identifier;
import top.yzljc.atricore.platform.qq.QQBot;
import top.yzljc.atribot.utils.FormatTools;
import top.yzljc.sakuraba_ema.guild.ChannelPosts;
import top.yzljc.sakuraba_ema.utils.ForumCode;

import java.util.Arrays;

/**
 * 更新通知管理指令、群消息推送及成员入群延迟推送
 *
 * @Author YZ_Ljc_
 * @ClassName UpdatePushCommand
 * @Created_at 2026/06/24
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.admin.update
 */
public class UpdatePushCommand implements Listener, CommandExecutor {
    private static final Logger log = LoggerFactory.getLogger(UpdatePushCommand.class);
    private final java.util.concurrent.ConcurrentMap<String, UpdateNoticeRecord.JoinTrigger> pendingJoins = new java.util.concurrent.ConcurrentHashMap<>();

    @EventHandler
    public void onGroupMessage(OfficialGroupMessageCreateEvent event) {
        pushPassiveMessage(event.getGroupId(), event.getMessage().getMessageId(), event.getUser());
    }

    @EventHandler
    public void onGroupAtMessage(OfficialGroupAtMessageCreateEvent event) {
        pushPassiveMessage(event.getGroupId(), event.getMessage().getMessageId(), event.getUser());
    }

    private void pushPassiveMessage(String groupId, String messageId, QQUser user) {
        if (!UpdateNoticeRecord.isPassiveMessagePushEnabled() || !UpdateNoticeRecord.shouldNotify(groupId)) return;
        if (groupId == null || groupId.isBlank() || messageId == null || messageId.isBlank()) return;
        Atri.getInstance().getScheduler().runTaskAsynchronously(() -> {
            try {
                UpdateNoticeRecord.deliverPassiveOnce(groupId, (content, createdAt) -> {
                    String message = content.render(QQBot.BOT_NAME, FormatTools.formatTimestampMilli(createdAt));
                    return "MARKDOWN".equals(content.format())
                            ? user.sendMessage(groupId, messageId, content.markdown(message), false)
                            : user.sendMessage(groupId, messageId, message);
                });
            } catch (Exception e) {
                log.warn("群消息更新通知发送失败，群ID {}", groupId, e);
            }
        });
    }

    @EventHandler
    public void onMemberJoin(OfficialGroupMemberAddEvent event) {
        String groupId = event.getGroupOpenId();
        var trigger = UpdateNoticeRecord.getJoinTrigger(groupId);
        if (trigger == null) return;
        pendingJoins.compute(groupId, (key, existing) -> {
            if (trigger.equals(existing)) return existing;
            Atri.getInstance().getScheduler().runTaskLaterAsynchronously(() -> {
                try {
                    UpdateNoticeRecord.deliverOnce(groupId, trigger, (content, createdAt) -> {
                        String message = content.render(QQBot.BOT_NAME, FormatTools.formatTimestampMilli(createdAt));
                        return "MARKDOWN".equals(content.format())
                                ? event.sendMessage(content.markdown(message), false) : event.sendMessage(message);
                    });
                } catch (Exception e) {
                    log.warn("入群事件更新通知发送失败，群ID {}", groupId, e);
                } finally {
                    pendingJoins.remove(groupId, trigger);
                }
            }, 3 * 60 * 1000L);
            return trigger;
        });
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof QQCommandSender)) return true;

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

        ChannelPosts.sendMessage(ForumCode.GUILD_ID, ForumCode.BOT_UPDATE.getChannelId(), "[更新日志] 亚托莉喵机器人更新", TC.md(text.replace("\n", "\n\n")));

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
            groupId = qq.getGroupId();
        } else {
            return;
        }

        UpdateNoticeRecord.deliverOnce(groupId, null, (content, createdAt) -> {
            String pushContent = content.render(QQBot.BOT_NAME, FormatTools.formatTimestampMilli(createdAt));
            if ("MARKDOWN".equals(content.format())) {
                return ((QQCommandSender) event.getSender()).sendMessage(content.markdown(pushContent), false);
            } else {
                return event.getSender().sendMessage(pushContent);
            }
        });
    }
}
