package top.yzljc.atribot.feature.schedule;

import top.yzljc.atribot.chat.onebot.GroupMessage;
import top.yzljc.atribot.chat.onebot.impl.MessageUtils;
import top.yzljc.atribot.chat.onebot.GroupInformation;
import top.yzljc.atribot.service.ThreadManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.service.timer.Schedule;
import top.yzljc.atribot.service.timer.ScheduleType;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.config.Config;
import top.yzljc.atribot.config.groups.GroupConfigManager;
import top.yzljc.atribot.utils.Alert;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class Calendar implements CommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(Calendar.class);
    private static final AtomicBoolean calendarPushInProgress = new AtomicBoolean(false);
    private static final String secret = Config.getInstance().getAtribotKeySecret();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!label.equals("0")) return true;
        if (sender.isDebug() && sender.isAdmin()) {
            sendToAllGroups();
        } else {
            sendToSingleGroup(sender.groupId());
        }
        return true;
    }

    public static void sendToSingleGroup(long targetGroupId) {
        try {
            GroupMessage.chatMessage(targetGroupId, "https://www.yzljc.top/data/api/v2/atribot/function/calendar?key=" + secret + "&system=false&" + System.currentTimeMillis(), MessageUtils.ImageType.URL);
        } catch (Exception e) {
            log.error("发送失败", e);
        }
    }

    @Schedule(time = "00:00:10", type = ScheduleType.DAILY)
    public static void sendToAllGroups() {
        top.yzljc.atribot.functions.official.Calendar.sendCalendar();
        ThreadManager.execute(() -> {
            if (!calendarPushInProgress.compareAndSet(false, true)) {
                log.warn("日历推送已在进行中，触发重复推送保护");
                return;
            }

            try {
                long messageId = GroupMessage.chatMessage(Config.getInstance().getNapcatDebugGroupUin(), "https://www.yzljc.top/data/api/v2/atribot/function/calendar?key=" + secret + "&system=true&" + System.currentTimeMillis(), MessageUtils.ImageType.URL);
                if (messageId != 0L) {
                    log.info("日历已发送至Debug群 ({})，MessageID: {}，开始执行广播转发...", Config.getInstance().getNapcatDebugGroupUin(), messageId);
                } else {
                    log.warn("无法获取MessageID，取消本次推送任务");
                    Alert.notify("日历推送失败，无法获取消息ID");
                    calendarPushInProgress.set(false);
                    return;
                }

                Set<Long> allGroups = GroupInformation.fetchAllGroupIds();
                for (long gid : allGroups) {
                    if (gid == Config.getInstance().getNapcatDebugGroupUin()) continue;
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