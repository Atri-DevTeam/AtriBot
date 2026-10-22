package top.yzljc.atribot.function.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atricore.chat.napcat.GroupInformation;
import top.yzljc.atricore.chat.napcat.GroupMessage;
import top.yzljc.atricore.chat.napcat.UserInformation;
import top.yzljc.atricore.command.Command;
import top.yzljc.atricore.command.CommandExecutor;
import top.yzljc.atricore.command.CommandSender;
import top.yzljc.atricore.command.NapcatCommandSender;
import top.yzljc.atricore.core.CoreConfig;
import top.yzljc.atricore.platform.Platform;
import top.yzljc.atricore.service.runtime.ThreadManager;
import top.yzljc.atricore.service.taskscheduler.ScheduleMode;
import top.yzljc.atricore.service.taskscheduler.ScheduledTask;
import top.yzljc.atricore.service.taskscheduler.TaskPlan;
import top.yzljc.atricore.service.taskscheduler.TaskSchedule;
import top.yzljc.atribot.Atri;
import top.yzljc.atribot.database.DatabaseManager;
import top.yzljc.atribot.database.repo.NapcatChatReportRepository;
import top.yzljc.atribot.database.repo.NapcatChatReportRepository.Window;
import top.yzljc.atribot.function.impl.NapcatChatReport;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigManager;
import top.yzljc.atribot.service.ai.AiService;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;

import static top.yzljc.atribot.database.repo.NapcatChatReportRepository.ZONE;

/**
 * 消息总结：本类纯AI编写，无任何人工成分
 *
 * @Author YZ_Ljc_
 * @ClassName NapcatChatDailyReport
 * @Created_at 2026/10/21
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.task
 */
public final class NapcatChatDailyReport implements CommandExecutor, ScheduledTask {
    public static final NapcatChatDailyReport INSTANCE = new NapcatChatDailyReport();
    public static final String FEATURE = "chat_daily_report";
    private static final Logger log = LoggerFactory.getLogger(NapcatChatDailyReport.class);
    private static final Set<String> RUNNING = ConcurrentHashMap.newKeySet();
    private static final Clock CLOCK = Clock.system(ZONE);

    private NapcatChatDailyReport() {
    }

    @Override
    public TaskSchedule schedule() {
        // 框架按进程本地时间调度，将北京时间 23 点转换为对应的本地时刻。
        return new TaskPlan(ScheduleMode.daily) {
            @Override
            public java.time.LocalTime getTime() {
                return LocalDate.now(ZONE).atTime(23, 0).atZone(ZONE)
                        .withZoneSameInstant(ZoneId.systemDefault()).toLocalTime();
            }
        };
    }

    @Override
    public void run() {
        if (!CoreConfig.getInstance().getSettings().isNapcatEnabled()) return;
        var enabled = GroupConfigManager.getStatusMapForFeature(FEATURE).entrySet().stream()
                .filter(entry -> Boolean.TRUE.equals(entry.getValue())).map(java.util.Map.Entry::getKey).toList();
        if (enabled.isEmpty()) return;
        Set<String> online = GroupInformation.fetchAllGroupIds();
        Window window = Window.automatic(CLOCK);
        for (String groupId : enabled) {
            if (!online.contains(groupId) || !RUNNING.add(groupId)) continue;
            execute(groupId, window, null);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1 || !"rv".equalsIgnoreCase(args[0])) return false;
        if (!(sender instanceof NapcatCommandSender nc) || nc.getPlatform() != Platform.NAPCAT_GROUP) {
//            sender.sendMessage("请在 Napcat 群聊中使用 /chat rv");
            return true;
        }
        if (nc.isBot()) return true;
        String groupId = nc.getGroupId();
        if (!GroupConfigManager.isFeatureEnabled(groupId, FEATURE)) {
            sender.sendMessage("当前群未开启群聊日报，请管理员启用 " + FEATURE);
            return true;
        }
        if (!RUNNING.add(groupId)) {
            sender.sendMessage("本群日报正在生成，请稍候");
            return true;
        }
        Window window = Window.yesterday(CLOCK);
        try {
            ThreadManager.execute(() -> execute(groupId, window, sender));
        } catch (RejectedExecutionException e) {
            RUNNING.remove(groupId);
            sender.sendMessage("当前任务繁忙，请稍后重试 /chat rv");
        }
        return true;
    }

    private static void execute(String groupId, Window window, CommandSender sender) {
        try {
            if (!GroupConfigManager.isFeatureEnabled(groupId, FEATURE)) return;
            if (sender != null) sender.sendMessage("正在生成 " + window.date() + " 的群聊日报，请稍候…");
            java.util.List<NapcatChatReportRepository.Message> messages;
            try (var connection = DatabaseManager.getConnection()) {
                messages = NapcatChatReportRepository.query(connection, groupId, window);
            }
            if (messages.isEmpty()) {
                if (sender != null) sender.sendMessage(window.date() + " 没有已记录的群消息，无法生成日报");
                return;
            }
            AiService ai = Atri.getInstance().getAiService();
            if (ai == null) throw new IllegalStateException("尚未配置 AI 服务");
            var prepared = NapcatChatReport.prepare(messages, userId -> UserInformation.getUserName(String.valueOf(userId)));
            var sections = NapcatChatReport.generate(window, prepared, (prompt, system) -> {
                String response = ai.askWithSystemPrompt(prompt, system);
                if (response == null || !AiService.isValidResponse(response))
                    throw new IllegalStateException("AI 请求失败");
                return response;
            });
            if (!GroupConfigManager.isFeatureEnabled(groupId, FEATURE)) return;
            String botId = UserInformation.getBotId();
            if (botId == null || botId.isBlank()) throw new IllegalStateException("无法获取 Napcat 机器人账号");
            String botName = UserInformation.getUserName(botId);
            String name = botName == null || botName.isBlank() ? "亚托莉喵" : botName;
            var nodes = sections.stream().map(text -> GroupMessage.createTextNode(text, botId, name)).toList();
            String messageId = GroupMessage.forwardMessage(groupId, nodes, "群聊日常分析报告", window.date() + " 群聊日报",
                    "消息 " + prepared.stats().messages() + " 条 · 参与 " + prepared.stats().participants() + " 人");
            if (messageId == null || messageId.isBlank()) throw new IllegalStateException("合并转发消息发送失败");
        } catch (Exception e) {
            log.warn("Napcat 群聊日报生成失败：group={}, date={}", groupId, window.date(), e);
            if (sender != null) sender.sendMessage("群聊日报生成失败，请检查 AI、数据库和 Napcat 配置后重试");
        } finally {
            RUNNING.remove(groupId);
        }
    }
}
