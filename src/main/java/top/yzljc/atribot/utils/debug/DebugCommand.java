package top.yzljc.atribot.utils.debug;

import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.platform.Platform;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @Author YZ_Ljc_
 * @ClassName OfficialBotDebug
 * @Created_at 2026/05/28
 * @Project AtriBot
 * @Package top.yzljc.atribot.debug
 */
public class DebugCommand implements CommandExecutor {

    public static final AtomicBoolean isOfficialDebugEnabled = new AtomicBoolean(false);

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender.getPlatform() == Platform.OFFICIAL_GROUP) {
            if (args.length < 1 || !args[0].equals("-o")) return true;
            if (sender.hasPermission()) {
                if (isOfficialDebugEnabled.get()) {
                    isOfficialDebugEnabled.set(false);
                    sender.sendMessage("[事件监听器禁用] 平台：" + sender.getPlatform());
                } else {
                    isOfficialDebugEnabled.set(true);
                    sender.sendMessage("[事件监听器启用] 平台：" + sender.getPlatform());
                }
                return true;
            }
        }

        if (sender.getPlatform() == Platform.NAPCAT_GROUP) {
            if (!sender.hasPermission()) {
                sender.sendMessage("你没有权限执行此操作！");
                return true;
            }

            if (args.length == 1 && args[0].equals("-o")) return true;

            String debugGroupId = Config.getInstance().getNapcatDebugGroupUin();
            String currentGroupId = sender.getGroupId();
            String statusMsg;

            if (args.length == 1) {
                if ("this".equalsIgnoreCase(args[0])) {
                    NapcatPacket.enableDebug(currentGroupId);
                    statusMsg = "Debug 模式已开启 (过滤模式)！\n只监听来自群 " + currentGroupId + " 的数据包\n数据将转发至群 " + debugGroupId;
                } else {
                    NapcatPacket.enableDebug(args[0]);
                    statusMsg = "Debug 模式已开启 (过滤模式)！\n只监听来自群 " + args[0] + " 的数据包\n数据将转发至群 " + debugGroupId;
                }
            } else {
                boolean toggled = NapcatPacket.toggleDebug();
                statusMsg = toggled ? "Debug 模式已开启（全局模式）\n所有收到的原始数据包将转发至群 " + debugGroupId : "Debug 模式已关闭";
            }

            sender.sendMessage(statusMsg);
            return true;
        }

        return true;
    }
}