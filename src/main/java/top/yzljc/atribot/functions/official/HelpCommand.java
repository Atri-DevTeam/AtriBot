package top.yzljc.atribot.functions.official;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.chat.official.Keyboard;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.config.Config;
import top.yzljc.atribot.service.ThreadManager;
import top.yzljc.atribot.service.official.CommandButton;
import top.yzljc.atribot.service.request.HttpService;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName HelpCommand
 * @Created_at 2026/05/25
 * @Project AtriBot
 * @Package top.yzljc.qqbot.functions.official
 */
@Slf4j
public class HelpCommand implements CommandExecutor {

    private static final String secret = Config.getInstance().getAtribotKeySecret();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String url = "https://www.yzljc.top/data/api/v2/atribot/function/help?key=" + secret + "&" + System.currentTimeMillis();

        ThreadManager.execute(() -> {
            try {
                HttpRequest preWarmRequest = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
                HttpResponse<Void> response = HttpService.httpClient.send(preWarmRequest, HttpResponse.BodyHandlers.discarding());
                HttpService.httpClient.send(preWarmRequest, HttpResponse.BodyHandlers.discarding());

                if (response.statusCode() != 200) {
                    log.warn("获取帮助信息 API 失败，状态码: {}", response.statusCode());
                }

                String help = "![today #941px #1672px](" + url + ")";

                List<List<CommandButton>> buttons = List.of(
                        List.of(
                                new CommandButton("s1", "提交反馈", "/feedback", true, 1, 2)
                        )
                );
                Object keyboard = Keyboard.build(buttons);

                sender.replyMarkdown(label, TC.md(help), keyboard);

            } catch (Exception e) {
                log.error("获取帮助信息 API 失败: ", e);
            }
        });

        return true;
    }
}
