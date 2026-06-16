package top.yzljc.atribot.function.general;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.chat.official.GroupChat;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.function.general.impl.PreImageGenerate;
import top.yzljc.atribot.service.runtime.ThreadManager;
import top.yzljc.atribot.utils.FormatTools;
import top.yzljc.atribot.utils.tools.Alert;

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

        String url = "https://www.yzljc.top/data/api/v2/atribot/function/calendar?key=" + secret + "&system=false&" + System.currentTimeMillis();

        int code = PreImageGenerate.create(url);

        if (code != 200) {
            sender.sendMessage("获取日历图片失败，请稍后再试，或将此情况提交给开发者：" + "API returned status code " + code);
            Alert.notify("日历图片获取失败，请检查！");
            log.warn("获取日历 API 失败，状态码: {}", code);
            return true;
        }

        String today = "![today #1642px #958px](" + url + ")\n\n" + "> 现在是北京时间" + FormatTools.formatTimestampMilli(System.currentTimeMillis());
        sender.sendMessage(TC.md(today));
        return true;
    }

    public static void sendCalendar() {
        String url = "https://www.yzljc.top/data/api/v2/atribot/function/calendar?key=" + secret + "&system=true&" + System.currentTimeMillis();

        ThreadManager.execute(() -> {
            int code = PreImageGenerate.create(url);

            if (code != 200) {
                Alert.notify("日历图片获取失败，请检查！");
                log.warn("获取日历 API 失败，状态码: {}", code);
                return;
            }

            String today = "![today #1642px #958px](" + url + ")\n\n" + "> 现在是北京时间" + FormatTools.formatTimestampMilli(System.currentTimeMillis()) + "\n\n> 夜已深，世界安静了。早点休息，好梦。";

            for (String gid : OfficialGroups.enabledGroups("daily_calendar")) {
                GroupChat.sendMessage(gid, TC.md(today));
            }
        });
    }
}