package top.yzljc.atribot.functions.official;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.Atri;
import top.yzljc.atribot.chat.official.GroupChat;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.config.Config;
import top.yzljc.atribot.chat.official.ChatService;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.functions.official.permission.GroupList;
import top.yzljc.atribot.service.request.HttpService;
import top.yzljc.atribot.service.ThreadManager;
import top.yzljc.atribot.utils.FormatTools;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * @Author YZ_Ljc_
 * @ClassName Calendar
 * @Created_at 2026/05/23
 * @Project AtriBot
 * @Package top.yzljc.qqbot.official.function
 */
@Slf4j
public class Calendar implements CommandExecutor {

    private static final ChatService service = Atri.getInstance().getChatService();

    private static final String secret = Config.getInstance().getAtribotKeySecret();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length > 0 && args[0].equals("test-2026") && sender.isAdmin()) {
            sendCalendar();
            return true;
        }

        String url = "https://www.yzljc.top/data/api/v2/atribot/function/calendar?key=" + secret + "&system=false&" + System.currentTimeMillis();

        try {
            HttpRequest preWarmRequest = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<Void> response = HttpService.httpClient.send(preWarmRequest, HttpResponse.BodyHandlers.discarding());
            HttpService.httpClient.send(preWarmRequest, HttpResponse.BodyHandlers.discarding());

            if (response.statusCode() != 200) {
                log.warn("获取日历 API 失败，状态码: {}", response.statusCode());
            }

            String today = "![today #1642px #958px](" + url + ")\n\n" + "> 现在是北京时间" + FormatTools.formatTimestampMilli(System.currentTimeMillis());
            sender.replyMarkdown(label, TC.md(today));

        } catch (Exception e) {
            log.error("获取日历 API 失败: ", e);
        }
        return true;
    }

    public static void sendCalendar() {
        String url = "https://www.yzljc.top/data/api/v2/atribot/function/calendar?key=" + secret + "&system=true&" + System.currentTimeMillis();

        ThreadManager.execute(() -> {
            try {
                HttpRequest preWarmRequest = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
                HttpResponse<Void> response = HttpService.httpClient.send(preWarmRequest, HttpResponse.BodyHandlers.discarding());
                HttpService.httpClient.send(preWarmRequest, HttpResponse.BodyHandlers.discarding());

                if (response.statusCode() != 200) {
                    log.warn("获取日历 API 失败，状态码: {}", response.statusCode());
                }

                String today = "![today #1642px #958px](" + url + ")\n\n" + "> 现在是北京时间" + FormatTools.formatTimestampMilli(System.currentTimeMillis()) + "\n\n> 夜已深，世界安静了。早点休息，好梦。";

                for (String gid : GroupList.enabledGroups("daily_calendar")) {
                    GroupChat.sendMessage(gid, TC.md(today));
                }

            } catch (Exception e) {
                log.error("获取日历 API 失败: ", e);
            }
        });
    }
}