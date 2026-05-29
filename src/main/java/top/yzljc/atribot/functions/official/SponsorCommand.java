package top.yzljc.atribot.functions.official;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.chat.official.ImageType;
import top.yzljc.atribot.chat.onebot.GroupMessage;
import top.yzljc.atribot.chat.onebot.impl.MessageUtils;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.config.Config;
import top.yzljc.atribot.service.ThreadManager;
import top.yzljc.atribot.service.request.HttpService;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * @Author YZ_Ljc_
 * @ClassName SponsorCommand
 * @Created_at 2026/06/03
 * @Project AtriBot
 * @Package top.yzljc.atribot.functions.official
 */
@Slf4j
public class SponsorCommand implements CommandExecutor {

    private static final String secret = Config.getInstance().getAtribotKeySecret();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String url = "https://www.yzljc.top/data/api/v2/atribot/function/sponsors?key=" + secret + "&" + System.currentTimeMillis();

        if (label.equals("0")) {
            GroupMessage.chatMessage(sender.groupId(), url, MessageUtils.ImageType.URL);
            return true;
        }

        ThreadManager.execute(() -> {
            try {
                HttpRequest preWarmRequest = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
                HttpResponse<Void> response = HttpService.httpClient.send(preWarmRequest, HttpResponse.BodyHandlers.discarding());
                HttpService.httpClient.send(preWarmRequest, HttpResponse.BodyHandlers.discarding());

                if (response.statusCode() != 200) {
                    log.warn("获取帮助信息 API 失败，状态码: {}", response.statusCode());
                }

                sender.sendMessage(url, ImageType.URL);

            } catch (Exception e) {
                log.error("获取帮助信息 API 失败: ", e);
            }
        });

        return true;
    }
}