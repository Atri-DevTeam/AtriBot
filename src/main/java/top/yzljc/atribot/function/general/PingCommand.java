package top.yzljc.atribot.function.general;

import com.sun.management.OperatingSystemMXBean;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;

import java.io.File;
import java.lang.management.ManagementFactory;


/**
 * @Author YZ_Ljc_
 * @ClassName PingCommand
 * @Created_at 2026/07/10
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.general
 */
public class PingCommand implements CommandExecutor {

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

        String status = String.format(
                "Pong!\n当前服务运行状态:\nCPU使用率: %.2f%%\n内存使用率: %.2f%%\n磁盘使用率: %.2f%%\n机器人运行时间: %s",
                getCpuUsage(),
                getMemoryUsage(),
                getDiskUsage(),
                getSystemOperatingTime()
        );
        sender.sendMessage(status);
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
}