package top.yzljc.atribot.utils.debug;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import top.yzljc.atricore.chat.official.C2CChat;
import top.yzljc.atricore.chat.official.GroupChat;
import top.yzljc.atricore.chat.official.TC;
import top.yzljc.atricore.command.CommandSender;
import top.yzljc.atricore.command.QQCommandSender;
import top.yzljc.atricore.platform.Platform;
import top.yzljc.atricore.service.runtime.ThreadManager;
import top.yzljc.atricore.utils.debug.MessageTrace;

/**
 * 按官方 QQ 会话管理消息链路调试开关，并向原会话主动发送报告
 *
 * @Author YZ_Ljc_
 * @ClassName MessageChainDebug
 * @Created_at 2026/10/22
 * @Project AtriMeow
 * @Package top.yzljc.atribot.utils.debug
 */
public final class MessageChainDebug {
    private static final Logger log = LoggerFactory.getLogger(MessageChainDebug.class);
    private static final Set<Conversation> ENABLED = ConcurrentHashMap.newKeySet();

    private MessageChainDebug() {}

    /**
     * 切换当前会话的调试状态，鉴权由 DebugCommand 完成
     *
     * @param sender 已通过权限检查的官方 QQ 群聊或私聊发送者
     * @return 切换后是否启用
     */
    public static boolean toggle(QQCommandSender sender) {
        Conversation key = conversation(sender);
        if (key == null) throw new IllegalArgumentException("不支持的调试会话");
        synchronized (ENABLED) {
            if (ENABLED.remove(key)) return false;
            ENABLED.add(key);
            return true;
        }
    }

    /**
     * @param sender 指令发送者
     * @param commandName 已注册的主指令名
     * @return 当前会话已开启且不是 debug 指令时为 true
     */
    public static boolean isEnabled(CommandSender sender, String commandName) {
        if (ENABLED.isEmpty()) return false;
        if ("debug".equalsIgnoreCase(commandName)) return false;
        Conversation key = conversation(sender);
        return key != null && ENABLED.contains(key);
    }

    /**
     * 提交独立发送任务，不阻塞原指令，也不将报告自身计入链路
     *
     * @param sender 原指令发送者
     * @param report 已完成的计时报告
     */
    public static void publish(CommandSender sender, MessageTrace.Report report) {
        Conversation key = conversation(sender);
        if (key == null || !ENABLED.contains(key)) return;
        MessageTrace.detached(() -> {
            try {
                ThreadManager.execute(() -> {
                    if (!ENABLED.contains(key)) return;
                    try {
                        String id = key.platform() == Platform.OFFICIAL_GROUP
                                ? GroupChat.sendMessage(key.id(), TC.md(render(report)))
                                : C2CChat.sendMessage(key.id(), TC.md(render(report)));
                        if (id == null) log.warn("消息链路报告发送失败: id={}", report.id());
                    } catch (RuntimeException failure) {
                        log.warn("消息链路报告发送失败: id={}", report.id(), failure);
                    }
                });
            } catch (RuntimeException failure) {
                log.warn("消息链路报告提交失败: id={}", report.id(), failure);
            }
        });
    }

    static String render(MessageTrace.Report report) {
        StringBuilder text = new StringBuilder("**消息链路** · ").append(report.id())
                .append("\n\n").append(report.failed() ? "执行结束，存在失败环节" : "执行完成")
                .append(" · 总耗时 **").append(millis(report.durationNanos())).append(" ms**\n");
        int shown = 0;
        for (var step : report.steps()) {
            if (shown++ == 18) break;
            text.append("\n> ").append(step.name()).append("：")
                    .append(millis(step.durationNanos())).append(" ms · ")
                    .append(step.success() ? "完成" : "失败");
            if (!step.detail().isBlank()) text.append(" · ").append(step.detail());
            text.append('\n');
        }
        int omitted = report.omitted() + Math.max(0, report.steps().size() - 18);
        if (omitted > 0) text.append("\n另有 ").append(omitted).append(" 个环节未展开\n");
        report.information().forEach((name, value) -> text.append("\n").append(name).append("：").append(value).append('\n'));
        if (report.overlapping()) text.append("\n存在并行或嵌套环节，耗时不直接相加\n");
        return text.append("\n计时范围：指令分发至关联任务结束").toString();
    }

    private static String millis(long nanos) {
        return String.format(java.util.Locale.ROOT, "%,d", TimeUnit.NANOSECONDS.toMillis(nanos));
    }

    private static Conversation conversation(CommandSender sender) {
        if (!(sender instanceof QQCommandSender qq)) return null;
        String id = switch (qq.getPlatform()) {
            case OFFICIAL_GROUP -> qq.getGroupId();
            case OFFICIAL_C2C -> qq.getUserId();
            default -> null;
        };
        return id == null || id.isBlank() ? null : new Conversation(qq.getPlatform(), id);
    }

    private record Conversation(Platform platform, String id) {}
}
