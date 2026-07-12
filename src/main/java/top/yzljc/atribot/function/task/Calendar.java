package top.yzljc.atribot.function.task;

import top.yzljc.atribot.configuration.ResourcesProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.chat.napcat.GroupInformation;
import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.chat.napcat.impl.MessageUtils;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.function.general.impl.PreImageGenerate;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigManager;
import top.yzljc.atribot.service.runtime.ThreadManager;
import top.yzljc.atribot.service.timer.Schedule;
import top.yzljc.atribot.service.timer.ScheduleType;
import top.yzljc.atribot.utils.tools.Alert;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class Calendar implements CommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(Calendar.class);
    private static final AtomicBoolean calendarPushInProgress = new AtomicBoolean(false);
    private static final String secret = Config.getInstance().getAtribotKeySecret();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender.getPlatform() != Platform.NAPCAT_GROUP) return true;
        if (!GroupConfigManager.isFeatureEnabled(sender.getGroupId(), "calendar")) return true;
        if (Config.getInstance().isDebugMode() && sender.hasPermission()) {
            sendToAllGroups();
        } else {
            sendToSingleGroup(sender.getGroupId());
        }
        return true;
    }

    public static void sendToSingleGroup(String targetGroupId) {
        try {
            String apiUrl = ResourcesProperties.CALENDAR_API + "?key=" + secret;
            var data = PreImageGenerate.dump(apiUrl, Map.of("system", false));
            if (!data.isError() && data.url() != null) {
                GroupMessage.chatMessage(targetGroupId, data.url(), MessageUtils.ImageType.URL);
            } else {
                String errMsg = data.errorMessage();
                log.error("日历推送失败: {}", errMsg);
            }
        } catch (Exception e) {
            log.error("日历推送失败", e);
        }
    }

    @Schedule(time = "00:00:10", type = ScheduleType.DAILY)
    public static void sendToAllGroups() {
        top.yzljc.atribot.function.general.Calendar.sendCalendar();
        ThreadManager.execute(() -> {
            if (!calendarPushInProgress.compareAndSet(false, true)) {
                log.warn("日历推送已在进行中，触发重复推送保护");
                return;
            }

            try {
                String apiUrl = ResourcesProperties.CALENDAR_API + "?key=" + secret;
                var data = PreImageGenerate.dump(apiUrl, Map.of("system", true));
                if (data.isError() || data.url() == null) {
                    String errMsg = data.errorMessage();
                    log.warn("日历推送失败: {}", errMsg);
                    Alert.notify("日历推送失败: " + errMsg);
                    calendarPushInProgress.set(false);
                    return;
                }

                String debugGroupUin = Config.getInstance().getNapcatDebugGroupUin();
                String messageId = GroupMessage.chatMessage(debugGroupUin, data.url(), MessageUtils.ImageType.URL);
                if (messageId != null) {
                    log.info("日历已发送至Debug群 ({})，MessageID: {}，开始执行广播转发...", debugGroupUin, messageId);
                } else {
                    log.warn("无法获取MessageID，取消本次推送任务");
                    Alert.notify("日历推送失败，无法获取消息ID");
                    calendarPushInProgress.set(false);
                    return;
                }

                Set<String> allGroups = GroupInformation.fetchAllGroupIds();
                for (String gid : allGroups) {
                    if (gid.equals(debugGroupUin)) continue;
                    if (!GroupConfigManager.isFeatureEnabled(gid, "calendar")) continue;

                    GroupMessage.forwardTo(gid, messageId);
                    log.info("已推送日历到群 {}，使用转发", gid);

                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException ignored) {
                    }
                }
                log.info("日历推送完成");
            } catch (Exception ex) {
                log.error("日历推送异常：", ex);
            } finally {
                calendarPushInProgress.set(false);
            }
        });
    }
}
