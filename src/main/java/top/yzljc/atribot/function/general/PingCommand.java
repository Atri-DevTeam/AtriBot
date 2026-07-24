package top.yzljc.atribot.function.general;

import com.sun.management.OperatingSystemMXBean;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.SlashCommandArguments;
import top.yzljc.atribot.command.SlashCommandExecutor;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.command.DiscordSlashCommandSender;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.service.request.HttpService;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.Arrays;


/**
 * @Author YZ_Ljc_
 * @ClassName PingCommand
 * @Created_at 2026/07/10
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.general
 */
public class PingCommand implements CommandExecutor, SlashCommandExecutor {

    private static final OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

    static {
        osBean.getCpuLoad();
        try {
            Thread.sleep(50);
        } catch (InterruptedException ignored) {
        }
        osBean.getCpuLoad();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender.getPlatform().equals(Platform.OFFICIAL_C2C)) {
            var t = Arrays.stream(buildStatus().split("\n", -1))
                    .map(line -> TC.md(line + "\n\n"))
                    .toList();
            sender.sendStreamMarkdownMessage(t);
            return true;
        }
        sender.sendMessage(buildStatus());
        return true;
    }

    @Override
    public boolean onSlashCommand(DiscordSlashCommandSender sender, Command command, String label, SlashCommandArguments args) {
        sender.sendMessage(buildStatus());
        return true;
    }

    private static double getCpuUsage() {
        return osBean.getCpuLoad() * 100;
    }

    private static double getMemoryUsage() {
        long totalMemory = osBean.getTotalMemorySize();
        long freeMemory = osBean.getFreeMemorySize();
        return ((double) (totalMemory - freeMemory) / totalMemory) * 100;
    }

    public static double getDiskUsage() {
        File file = new File("/");
        long total = file.getTotalSpace();
        long free = file.getFreeSpace();
        return (double) (total - free) / total * 100;
    }

    private static String getSystemOperatingTime() {
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
        long seconds = uptime / 1000 % 60;
        long minutes = uptime / (1000 * 60) % 60;
        long hours = uptime / (1000 * 60 * 60) % 24;
        long days = uptime / (1000 * 60 * 60 * 24);
        return String.format("%d天%02d小时%02d分钟%02d秒", days, hours, minutes, seconds);
    }

    private static String buildStatus() {
        var ugc_status = HttpService.sendGetRequest(ResourcesProperties.UGC_STATUS_API);
        String ugc_status_str = "CPU使用率: $cpu\n内存使用率: $meo\n磁盘使用率: $disk\n运行时间: $upt";
        if (ugc_status == null || ugc_status.path("status").asInt() != 200) {
            ugc_status_str = "获取状态失败";
        } else {
            var d = ugc_status.path("data");
            String cpu = d.path("cpuUsage").asText(null);
            String memory = d.path("memoryUsage").asText(null);
            String disk = d.path("diskUsage").asText(null);
            String uptime = d.path("uptime").asText(null);
            ugc_status_str = ugc_status_str.replace("$cpu", cpu).replace("$meo", memory).replace("$disk", disk).replace("$upt", uptime);
        }

        return String.format(
                "Pong! \n● 机器人运行状态:\nCPU使用率: %.2f%%\n内存使用率: %.2f%%\n磁盘使用率: %.2f%%\n运行时间: %s\n" +
                        "● 图源服务运行状态:\n%s",
                getCpuUsage(),
                getMemoryUsage(),
                getDiskUsage(),
                getSystemOperatingTime(),
                ugc_status_str
        );
    }
}