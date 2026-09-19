package top.yzljc.atribot.function.tasks.pushtask;

import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.service.request.HttpService;
import top.yzljc.atribot.service.taskscheduler.ScheduleMode;
import top.yzljc.atribot.service.taskscheduler.ScheduledTask;
import top.yzljc.atribot.service.taskscheduler.TaskPlan;
import top.yzljc.atribot.service.taskscheduler.TaskSchedule;

import java.time.Duration;

/**
 * @Author YZ_Ljc_
 * @ClassName HypixelBanWaveAlertTask
 * @Created_at 2026/09/18
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.tasks.pushtask
 */
public final class HypixelBanWaveAlertTask extends PushTask implements ScheduledTask {

    private static final long ALERT_COOLDOWN_NANOS = Duration.ofMinutes(5).toNanos();
    private static Long lastAlertNanos;

    public static HypixelBanWaveAlertTask TASK_INSTANCE = new HypixelBanWaveAlertTask();

    public HypixelBanWaveAlertTask() {
        super("hyp_banwave_alert", "Hypixel BanWave告警", true);
    }

    @Override
    public Markdown getDescription(Platform platform, String platformIdentifyId) {
        return TC.md("**Hypixel BanWave告警**\n\n在Hypixel Punishment数据发生波动时予以提醒，例如：\n\n---\n\n" + "Hypixel BanWave告警\n\n> 近5分钟 225，前5分钟 100，环比 +125.0%\n\n---\n\n"
                + getStatus(platform, platformIdentifyId));
    }

    @Override
    public TaskSchedule schedule() {
        return new TaskPlan(ScheduleMode.minutely);
    }

    @Override
    public void run() {
        var response = HttpService.sendGetRequest(ResourcesProperties.HYPIXEL_BANWAVE_API);
        if (response == null || response.path("status").asInt() != 200) return;

        var data = response.path("data");
        if (data.path("is_banwave").asBoolean(false) && tryAcquireAlert()) {
            String alert = data.path("alert").asText("");
            PushTask.push("hyp_banwave_alert", TC.md("Hypixel BanWave告警\n\n" + "> " + alert));
        }
    }

    private static synchronized boolean tryAcquireAlert() {
        long now = System.nanoTime();
        if (lastAlertNanos != null && now - lastAlertNanos < ALERT_COOLDOWN_NANOS) {
            return false;
        }
        // 在推送前记录，避免并发检测重复告警；被抑制的检测不延长冷却。
        lastAlertNanos = now;
        return true;
    }
}
