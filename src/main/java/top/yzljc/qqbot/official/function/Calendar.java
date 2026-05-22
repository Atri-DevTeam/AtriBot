package top.yzljc.qqbot.official.function;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.qqbot.AtriBot;
import top.yzljc.qqbot.command.Command;
import top.yzljc.qqbot.command.CommandExecutor;
import top.yzljc.qqbot.command.CommandSender;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.official.service.QQBotMessageService;
import top.yzljc.qqbot.service.request.HttpService;
import top.yzljc.qqbot.service.thread.ThreadManager;
import top.yzljc.qqbot.utils.FormatTools;

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

    private static final QQBotMessageService service = AtriBot.getInstance().getMessageService();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length > 0 && args[0].equals("test-2026") && sender.isAdmin()) {
            sendCalendar();
            return true;
        }

        String url = "https://www.yzljc.top/data/api/v2/atribot/function/calendar?key=atri-atribot-calendar-2026@yzljc.top&system=false&" + System.currentTimeMillis();

        try {
            HttpRequest preWarmRequest = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<Void> response = HttpService.httpClient.send(preWarmRequest, HttpResponse.BodyHandlers.discarding());
            HttpService.httpClient.send(preWarmRequest, HttpResponse.BodyHandlers.discarding());

            if (response.statusCode() != 200) {
                log.warn("获取日历 API 失败，状态码: {}", response.statusCode());
            }

            String today = "![today #1200px #700px](" + url + ")\n\n" + "> 现在是北京时间" + FormatTools.formatTimestampMilli(System.currentTimeMillis());

            sender.replyMarkdown(label, today);

        } catch (Exception e) {
            log.error("获取日历 API 失败: ", e);
        }
        return true;
    }

    public static void sendCalendar() {
        String url = "https://www.yzljc.top/data/api/v2/atribot/function/calendar?key=atri-atribot-calendar-2026@yzljc.top&system=true&" + System.currentTimeMillis();

        ThreadManager.execute(() -> {
            try {
                HttpRequest preWarmRequest = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
                HttpResponse<Void> response = HttpService.httpClient.send(preWarmRequest, HttpResponse.BodyHandlers.discarding());
                HttpService.httpClient.send(preWarmRequest, HttpResponse.BodyHandlers.discarding());

                if (response.statusCode() != 200) {
                    log.warn("获取日历 API 失败，状态码: {}", response.statusCode());
                }

                String today = "![today #1200px #700px](" + url + ")\n\n" + "> 夜已深，世界安静了。早点休息，好梦。";

                for (String gid : Config.getInstance().getActiveMessageGroups()) {
                    service.sendActiveGroupMarkdownMessage(gid, today);
                }

            } catch (Exception e) {
                log.error("获取日历 API 失败: ", e);
            }
        });
    }
}