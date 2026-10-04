package top.yzljc.atribot.function.command;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.command.impl.QQSenderImpl;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.EventPriority;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.OfficialC2CMessageCreateEvent;
import top.yzljc.atribot.function.reminder.ReminderService;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.service.runtime.ThreadManager;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * @Author YZ_Ljc_
 * @ClassName ReminderCommand
 * @Created_at 2026/09/30
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.command
 */
@Slf4j
public final class ReminderCommand implements CommandExecutor, Listener {
    private static final Set<OfficialC2CMessageCreateEvent> CONTENT_EVENTS = Collections.newSetFromMap(
            Collections.synchronizedMap(new WeakHashMap<>()));
    private final ReminderService service = ReminderService.getInstance();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof QQCommandSender qq) || qq.getPlatform() != Platform.OFFICIAL_C2C) {
            sender.sendMessage("提醒功能仅支持QQ官方机器人私聊。");
            return true;
        }
        if (qq.isBot() || OfficialUsers.isBlocked(qq.getUserId()) || OfficialUsers.isIgnored(qq.getUserId())) return true;
        if (!qq.hasPermission(ReminderService.USE_PERMISSION)) {
            qq.sendMessage(ReminderService.BETA_ONLY, false);
            return true;
        }
        execute(qq, label, args);
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPrivateChat(OfficialC2CMessageCreateEvent event) {
        if (event.shouldIgnore() || event.getUser().isBot() || event.getMessage().isCommand()
                || OfficialUsers.isIgnored(event.getUser().getUserId())) return;
        String input = event.getMessage().getContent();
        String userId = event.getUser().getUserId();
        String messageId = event.getMessage().getMessageId();
        if (service.isConsumedCustomInput(userId, messageId)) {
            CONTENT_EVENTS.add(event);
            return;
        }
        if (service.hasPendingCustom(userId)) {
            CONTENT_EVENTS.add(event);
            QQCommandSender sender = new QQSenderImpl(event.getUser(), null, event.getMessage());
            if (input != null && input.trim().equals("取消")) {
                submit(sender, () -> service.cancelCustom(userId));
                return;
            }
            if (!service.claimCustomInput(userId, messageId)) {
                sender.sendMessage(ReminderService.BUSY, false);
                return;
            }
            String content = event.getMessage().getAttachments() != null && !event.getMessage().getAttachments().isEmpty()
                    ? null : input;
            try {
                ThreadManager.execute(() -> sendResult(sender, () -> service.completeCustom(userId, messageId,
                        event.getMessage().getRefIdx(), content), false));
            } catch (java.util.concurrent.RejectedExecutionException e) {
                service.releaseCustomInput(userId, messageId);
                sender.sendMessage(ReminderService.BUSY, false);
            }
            return;
        }
    }

    public static boolean isReminderRequest(String input) {
        return input != null && input.trim().toLowerCase(Locale.ROOT)
                .matches("^/?(提醒|remind|reminder|提醒列表|reminder-list)(\\s.*)?$");
    }

    /**
     * 判断本次私聊事件是否已交由提醒内容接收流程处理
     *
     * @param event 当前私聊事件
     * @return 已接收、重复投递或正在等待处理的提醒内容事件返回 true
     */
    public static boolean isCustomReminderInput(OfficialC2CMessageCreateEvent event) {
        return CONTENT_EVENTS.contains(event);
    }

    private void execute(QQCommandSender sender, String label, String[] args) {
        if (label.equalsIgnoreCase("reminder-list") || label.equals("提醒列表")) {
            if (args.length == 2 && (args[0].equals("开启") || args[0].equals("关闭") || args[0].equals("删除"))) {
                Long id = parseId(args[1]);
                if (id == null) sender.sendMessage(ReminderService.NOT_FOUND, false);
                else if (args[0].equals("删除")) submit(sender, () -> service.delete(sender.getUserId(), id));
                else submit(sender, () -> service.setEnabled(sender.getUserId(), id, args[0].equals("开启")));
                return;
            }
            int page = 1;
            if (args.length == 1) {
                try { page = Integer.parseInt(args[0]); }
                catch (NumberFormatException ignored) { page = 0; }
            } else if (args.length > 1) page = 0;
            if (page < 1 || page > 1_000_000) {
                sender.sendMessage("用法：提醒列表 [页码]", false);
                return;
            }
            int requestedPage = page;
            try {
                ThreadManager.execute(() -> {
                    try {
                        sender.sendMessage(new Markdown(service.list(sender.getUserId(), requestedPage)), false);
                    } catch (Exception e) {
                        log.warn("提醒列表发送失败: errorType={}", e.getClass().getSimpleName());
                    }
                });
            } catch (java.util.concurrent.RejectedExecutionException e) {
                sender.sendMessage(ReminderService.BUSY, false);
            }
            return;
        }
        if (args.length == 0) {
            sender.sendMessage(ReminderService.USAGE, false);
        } else if (args.length == 1 && args[0].equals("取消")) {
            submit(sender, () -> service.cancelCustom(sender.getUserId()));
        } else {
            submit(sender, () -> service.prepare(sender.getUserId(), sender.getMessage().getMessageId(),
                    String.join(" ", args)), true);
        }
    }

    private void submit(QQCommandSender sender, java.util.function.Supplier<String> operation) {
        submit(sender, operation, false);
    }

    private void submit(QQCommandSender sender, java.util.function.Supplier<String> operation, boolean custom) {
        try {
            ThreadManager.execute(() -> sendResult(sender, operation, custom));
        } catch (java.util.concurrent.RejectedExecutionException e) {
            sender.sendMessage(ReminderService.BUSY, false);
        }
    }

    private void sendResult(QQCommandSender sender, java.util.function.Supplier<String> operation, boolean custom) {
        try {
            String result = operation.get();
            if (result != null && sender.sendMessage(result, false) == null && custom) {
                service.discardCustom(sender.getUserId(), sender.getMessage().getMessageId());
            }
        } catch (Exception e) {
            if (custom) service.discardCustom(sender.getUserId(), sender.getMessage().getMessageId());
            log.warn("提醒固定回复发送失败: errorType={}", e.getClass().getSimpleName());
        }
    }

    private static Long parseId(String value) {
        String digits = value.startsWith("#") ? value.substring(1) : value;
        if (!digits.matches("[1-9][0-9]{0,18}")) return null;
        try { return Long.parseLong(digits); }
        catch (NumberFormatException ignored) { return null; }
    }
}
