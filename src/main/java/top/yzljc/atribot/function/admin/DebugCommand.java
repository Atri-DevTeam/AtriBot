package top.yzljc.atribot.function.admin;

import top.yzljc.atribot.command.*;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.utils.debug.NapcatPacket;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @Author YZ_Ljc_
 * @ClassName OfficialBotDebug
 * @Created_at 2026/05/28
 * @Project AtriBot
 * @Package top.yzljc.atribot.debug
 */
public class DebugCommand implements CommandExecutor {

    public static final AtomicBoolean isQQDebugEnabled = new AtomicBoolean(false);
    public static DebugDisplayType type = DebugDisplayType.DEBUG_GROUP;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender instanceof ConsoleCommandSender console) {
            if (args.length < 1) {
                console.sendMessage("无效的平台参数，可选参数: -n, -o");
                return true;
            }
            if (args.length >1 && args[0].equals("-o")) {
                String text = "[!] 事件监听器%s，调用方: OFFICIAL_QQ_ALL_EVENT";
                if (isQQDebugEnabled.get()) {
                    console.sendMessage(String.format(text, "已禁用"));
                    isQQDebugEnabled.set(false);
                    type = DebugDisplayType.DEBUG_GROUP;
                } else {
                    console.sendMessage(String.format(text, "已启用"));
                    isQQDebugEnabled.set(true);
                    type = DebugDisplayType.CONSOLE;
                }
                return true;
            } else if (args.length >1 && args[0].equals("-n")) {
                String text = "[!] 事件监听器%s，调用方: NAPCAT_ALL_EVENT";
                boolean toggled = NapcatPacket.toggleDebug();
                if (toggled) {
                    console.sendMessage(String.format(text, "已启用"));
                } else {
                    console.sendMessage(String.format(text, "已禁用"));
                }
                return true;
            } else {
                console.sendMessage("无效的平台参数，可选参数: -n, -o");
                return true;
            }

        }

        if (sender instanceof QQCommandSender qq && qq.getPlatform() == Platform.OFFICIAL_GROUP) {
            if (args.length < 1 || !args[0].equals("-o")) return true;
            if (qq.hasPermission()) {
                if (isQQDebugEnabled.get()) {
                    isQQDebugEnabled.set(false);
                    qq.sendMessage("[事件监听器禁用] 平台：" + qq.getPlatform());
                } else {
                    isQQDebugEnabled.set(true);
                    qq.sendMessage("[事件监听器启用] 平台：" + qq.getPlatform());
                }
                return true;
            }
        }

        if (sender instanceof NapcatCommandSender nc && nc.getPlatform() == Platform.NAPCAT_GROUP) {
            if (!nc.hasPermission()) {
                nc.sendMessage("你没有权限执行此操作！");
                return true;
            }

            if (args.length == 1 && args[0].equals("-o")) return true;

            String debugGroupId = Config.getInstance().getNapcatDebugGroupUin();
            String currentGroupId = nc.getGroupId();
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

            nc.sendMessage(statusMsg);
            return true;
        }

        return true;
    }

    public enum DebugDisplayType {
        CONSOLE,
        DEBUG_GROUP
    }
}