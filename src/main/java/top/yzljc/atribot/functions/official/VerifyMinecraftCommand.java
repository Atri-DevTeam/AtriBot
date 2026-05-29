package top.yzljc.atribot.functions.official;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.Atri;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.impl.GroupMessageEvent;
import top.yzljc.atribot.functions.official.minecraft.MinecraftBind;
import top.yzljc.atribot.functions.official.minecraft.BindResponse;
import top.yzljc.atribot.functions.official.permission.GroupList;
import top.yzljc.atribot.service.official.CommandButton;
import top.yzljc.atribot.chat.official.ChatService;
import top.yzljc.atribot.service.request.HttpService;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.*;

/**
 * @Author YZ_Ljc_
 * @ClassName VerifyMinecraftCommand
 * @Created_at 2026/05/09
 * @Project AtriBot
 * @Package top.yzljc.qqbot.official.function
 */
@Slf4j
public class VerifyMinecraftCommand implements CommandExecutor, Listener {

    private static final ChatService service = Atri.getInstance().getChatService();

    private static final Map<String, Long> pendingPossibleQQNum = new ConcurrentHashMap<>();

    private static final Object keyboard = service.buildCmdKeyboard(List.of(
            List.of(new CommandButton("c1", "绑定账号", "/verify", true, 1, 2))
    ));

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (label.equals("0")) {
            return true;
        }

        if (args.length != 1) {
            Markdown md = TC.md("参数错误！请提供社区服务器内生成的验证码，格式: /verify <验证码>\n\n" + TC.img("https://www.yzljc.top/img/how-to-verify.gif", 640, 360));
            sender.sendMessage(md, keyboard);
            return true;
        }

        String code = args[0].trim();

        long possibleQQNum = -1;
        if (pendingPossibleQQNum.containsKey(code)) {
            possibleQQNum = pendingPossibleQQNum.remove(code);
        }

        var result = MinecraftBind.bindAccount(sender.unionOpenId(), possibleQQNum, code, sender.groupOpenId());
        return handleBindResponse(sender, label, result);
    }

    private static boolean handleBindResponse(CommandSender sender, String label, BindResponse result) {
        int statusCode = result.code();
        String uuid = result.uuid();

        switch (statusCode) {
            case 200:
                String headUrl = getPlayerHead(uuid);
                if (!headUrl.equals("-1")) {
                    String markdown = "> ✅ 绑定成功！\n> 玩家 UUID: `" + uuid + "`\n" + (GroupList.isWhitelist(sender.groupOpenId()) ? "> ![玩家头像 #96px #96px](" + headUrl + ")" : "");

                    List<List<CommandButton>> layout = new ArrayList<>();
                    layout.add(Arrays.asList(
                            new CommandButton("c1", "在档数据", "/stats " + uuid, true, 0, 2),
                            new CommandButton("c2", "成就数据", "/stats am " + uuid, true, 0, 2),
                            new CommandButton("c2", "小游戏数据", "/stats games " + uuid, true, 0, 2)
                    ));
                    layout.add(List.of(
                            new CommandButton("c3", "查询玩家在档数据", "/stats ", false, 1, 2)
                    ));

                    Object keyboard = service.buildCmdKeyboard(layout);
                    sender.replyMarkdown(label, markdown, keyboard);
                }
                break;

            case 100:
                sender.replyText(label, "⚠️ 绑定失败：你游戏内的账号已经绑定过其他 QQ 了！");
                break;
            case 400:
                sender.replyText(label, "❌ 验证码错误或已过期(有效时间5分钟)，请在游戏内重新生成");
                break;
            case 500:
                sender.replyText(label, "🔧 服务器未开启或网络异常，请稍后再试!");
                break;
            default:
                sender.replyText(label, "❓ 未知错误代码: " + result);
                break;
        }
        return true;
    }

    private static String getPlayerHead(String uuid) {
        String url = "https://www.yzljc.top/data/api/v1/avatar/{uuid}".replace("{uuid}", uuid);
        try {
            HttpRequest preWarmRequest = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<Void> response = HttpService.httpClient.send(preWarmRequest, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() != 200) return "-1";
            HttpService.httpClient.send(preWarmRequest, HttpResponse.BodyHandlers.discarding());
            return url;
        } catch (Exception e) {
            return "-1";
        }
    }

    @EventHandler
    public void onInfoGet(GroupMessageEvent event) {
        long userId = event.getUserId();
        String message = stripCQCode(event.getRawMessage());
        message = stripMentions(message);

        if (message.startsWith("/verify")) {
            String[] parts = message.split("\\s+");
            if (parts.length == 2) {
                String code = parts[1].trim();
                pendingPossibleQQNum.put(code, userId);
            }
        }
    }

    public static String stripCQCode(String message) {
        if (message == null) return "";
        return message.replaceAll("\\[CQ:[^\\]]+\\]", "").trim();
    }

    public static String stripMentions(String message) {
        if (message == null) return "";
        return message.replaceFirst("^(@\\S+\\s*)+", "").trim();
    }
}