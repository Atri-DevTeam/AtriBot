package top.yzljc.atribot.function.general;

import top.yzljc.atribot.configuration.ResourcesProperties;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.chat.official.GroupChat;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.function.general.impl.ImageDTO;
import top.yzljc.atribot.function.general.impl.PreImageGenerate;
import top.yzljc.atribot.service.runtime.ThreadManager;
import top.yzljc.atribot.utils.FormatTools;
import top.yzljc.atribot.utils.tools.Alert;

import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName Calendar
 * @Created_at 2026/05/23
 * @Project AtriBot
 * @Package top.yzljc.qqbot.official.function
 */
@Slf4j
public class Calendar implements CommandExecutor {

    private static final String secret = Config.getInstance().getAtribotKeySecret();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length > 0 && args[0].equals("test-2026") && sender.hasPermission()) {
            sendCalendar();
            return true;
        }

        String apiUrl = ResourcesProperties.CALENDAR_API + "?key=" + secret;
        ImageDTO data = PreImageGenerate.dump(apiUrl, Map.of("system", false));

        if (data.isError()) {
            String errMsg = data.errorMessage();
            sender.sendMessage("获取日历图片失败: " + errMsg);
            Alert.notify("日历图片获取失败: " + errMsg);
            log.warn("获取日历图片失败: {}", errMsg);
            return true;
        }

        String today = "![today #1642px #958px](" + data.url() + ")\n\n" + "> 现在是北京时间" + FormatTools.formatTimestampMilli(System.currentTimeMillis());
        sender.sendMessage(TC.md(today));
        return true;
    }

    public static void sendCalendar() {
        String apiUrl = ResourcesProperties.CALENDAR_API + "?key=" + secret;

        ThreadManager.execute(() -> {
            ImageDTO data = PreImageGenerate.dump(apiUrl, Map.of("system", true));

            if (data == null || data.isError()) {
                String errMsg = data != null ? data.errorMessage() : "API 无响应";
                Alert.notify("日历图片获取失败: " + errMsg);
                log.warn("获取日历 API 失败: {}", errMsg);
                return;
            }

            String today = "![today #1642px #958px](" + data.url() + ")\n\n" + "> 现在是北京时间" + FormatTools.formatTimestampMilli(System.currentTimeMillis()) + "\n> 夜已深，世界安静了。早点休息，好梦。";

            for (String gid : OfficialGroups.enabledGroups("daily_calendar")) {
                GroupChat.sendMessage(gid, TC.md(today));
            }
        });
    }
}