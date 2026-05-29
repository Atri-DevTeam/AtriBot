package top.yzljc.atribot.functions.overall;

import top.yzljc.atribot.Atri;
import top.yzljc.atribot.chat.official.ImageType;
import top.yzljc.atribot.chat.onebot.GroupMessage;
import top.yzljc.atribot.chat.onebot.impl.MessageUtils;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.config.Config;
import top.yzljc.atribot.service.request.HttpService;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * @Author YZ_Ljc_
 * @ClassName MojangStatus
 * @Created_at 2026/06/01
 * @Project AtriBot
 * @Package top.yzljc.atribot.functions.overall
 */
public class MojangStatus implements CommandExecutor {

    private static final String secret = Config.getInstance().getAtribotKeySecret();

    private static final String url = "https://www.yzljc.top/data/api/v2/atribot/function/mojang-status?key=" + secret + "&" + System.currentTimeMillis();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (label.equals("0")) {
            long messageId = sender.reply("正在检查 Mojang 服务状态，请稍候...");
            try {
                int response = getStatusImg();

                if (response != 200) {
                    sender.reply("检查 Mojang 服务状态失败，API 返回状态码: " + response);
                    GroupMessage.recallMessage(messageId);
                    return true;
                }

                GroupMessage.recallMessage(messageId);
                GroupMessage.chatMessage(sender.groupId(), url, MessageUtils.ImageType.URL);
                return true;
            } catch (Exception e) {
                sender.reply("检查 Mojang 服务状态失败: " + e.getMessage());
                return true;
            }
        }

        if (label.equals("1") || label.equals("2")) {
            String messageId = sender.replyText(label, "正在检查 Mojang 服务状态，请稍候...");
            try {
                int response = getStatusImg();

                if (response != 200) {
                    sender.replyText(label, "检查 Mojang 服务状态失败，API 返回状态码: " + response);
                    Atri.getInstance().getChatService().recallGroupMessage(sender.groupOpenId(), messageId);
                    return true;
                }

                Atri.getInstance().getChatService().recallGroupMessage(sender.groupOpenId(), messageId);
                sender.replyImage(label, url, ImageType.URL);
                return true;
            } catch (Exception e) {
                sender.replyText(label, "检查 Mojang 服务状态失败: " + e.getMessage());
                return true;
            }
        }
        return true;
    }

    private static int getStatusImg() throws Exception {
        HttpRequest preWarmRequest = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        HttpResponse<Void> response = HttpService.httpClient.send(preWarmRequest, HttpResponse.BodyHandlers.discarding());
        HttpService.httpClient.send(preWarmRequest, HttpResponse.BodyHandlers.discarding());
        return response.statusCode();
    }
}