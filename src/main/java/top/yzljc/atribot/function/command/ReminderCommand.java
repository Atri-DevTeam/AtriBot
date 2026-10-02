package top.yzljc.atribot.function.command;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.command.impl.QQSenderImpl;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.OfficialC2CMessageCreateEvent;
import top.yzljc.atribot.function.reminder.ReminderService;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.service.runtime.ThreadManager;

import java.util.Arrays;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * @Author YZ_Ljc_
 * @ClassName ReminderCommand
 * @Created_at 2026/09/30
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.command
 */
@Slf4j
public final class ReminderCommand implements CommandExecutor, Listener {
    private static final Pattern NATURAL = Pattern.compile(
            "^(?:请|帮我|请帮我)?(?:设置|创建|添加)(?:一个|一项|个)?(?:定时)?提醒|提醒我");
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
        execute(qq, args);
        return true;
    }

    @EventHandler
    public void onPrivateChat(OfficialC2CMessageCreateEvent event) {
        if (event.shouldIgnore() || event.getUser().isBot() || event.getMessage().isCommand()
                || OfficialUsers.isIgnored(event.getUser().getUserId())) return;
        String input = event.getMessage().getContent();
        if (!isNaturalRequest(input)) return;
        if (!event.getUser().hasPermission(ReminderService.USE_PERMISSION)) {
            event.sendMessage(ReminderService.BETA_ONLY);
            return;
        }
        QQCommandSender sender = new QQSenderImpl(event.getUser(), null, event.getMessage());
        submit(sender, () -> service.set(sender.getUserId(), sender.getMessage().getMessageId(),
                sender.getMessage().getRefIdx(), null, input));
    }

    public static boolean isNaturalRequest(String input) {
        return input != null && !input.stripLeading().startsWith("/") && NATURAL.matcher(input.trim()).find();
    }

    public static boolean isReminderRequest(String input) {
        if (isNaturalRequest(input)) return true;
        if (input == null) return false;
        String stripped = input.trim().toLowerCase(Locale.ROOT);
        return stripped.matches("^/(提醒|remind|reminder)(\\s.*)?$");
    }

    private void execute(QQCommandSender sender, String[] args) {
        if (args.length == 0) {
            submit(sender, () -> service.list(sender.getUserId()));
            return;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        if (action.equals("设置") || action.equals("列表") || action.equals("list")) {
            if (args.length == 1) {
                submit(sender, () -> service.list(sender.getUserId()));
                return;
            }
            if (args.length == 2 && args[1].matches("[0-9]+")) {
                int page;
                try { page = Integer.parseInt(args[1]); }
                catch (NumberFormatException ignored) { page = 0; }
                int requestedPage = page;
                submit(sender, () -> service.list(sender.getUserId(), requestedPage));
                return;
            }
            if (!action.equals("设置")) {
                sender.sendMessage(ReminderService.USAGE, false);
                return;
            }
        }
        if (action.equals("关闭") || action.equals("删除") || action.equals("修改")
                || action.equals("disable") || action.equals("delete") || action.equals("edit")) {
            boolean edit = action.equals("修改") || action.equals("edit");
            if (args.length < (edit ? 3 : 2) || (!edit && args.length != 2)) {
                sender.sendMessage(ReminderService.USAGE, false);
                return;
            }
            Long id = parseId(args[1]);
            if (id == null) {
                sender.sendMessage(ReminderService.NOT_FOUND, false);
                return;
            }
            if (edit) {
                String input = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                submit(sender, () -> service.set(sender.getUserId(), sender.getMessage().getMessageId(),
                        sender.getMessage().getRefIdx(), id, input));
            } else {
                boolean delete = action.equals("删除") || action.equals("delete");
                submit(sender, () -> service.stop(sender.getUserId(), id, delete));
            }
            return;
        }
        int start = action.equals("设置") || action.equals("添加") || action.equals("创建") || action.equals("add") ? 1 : 0;
        String input = String.join(" ", Arrays.copyOfRange(args, start, args.length));
        submit(sender, () -> service.set(sender.getUserId(), sender.getMessage().getMessageId(),
                sender.getMessage().getRefIdx(), null, input));
    }

    private void submit(QQCommandSender sender, java.util.function.Supplier<String> operation) {
        try {
            ThreadManager.execute(() -> {
                try { sender.sendMessage(operation.get(), false); }
                catch (Exception e) { log.warn("提醒固定回复发送失败: errorType={}", e.getClass().getSimpleName()); }
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            sender.sendMessage(ReminderService.BUSY, false);
        }
    }

    private static Long parseId(String value) {
        String digits = value.startsWith("#") ? value.substring(1) : value;
        if (!digits.matches("[1-9][0-9]{0,18}")) return null;
        try { return Long.parseLong(digits); }
        catch (NumberFormatException ignored) { return null; }
    }
}
