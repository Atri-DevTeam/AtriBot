package top.yzljc.atribot.function.official;

import top.yzljc.atribot.configuration.ResourcesProperties;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.chat.official.button.Button;
import top.yzljc.atribot.chat.official.button.ButtonStyle;
import top.yzljc.atribot.chat.official.button.ButtonType;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.NapcatGroupMessageEvent;
import top.yzljc.atribot.function.general.impl.PreImageGenerate;
import top.yzljc.atribot.function.official.minecraft.MinecraftBind;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.utils.socket.BindResponse;

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

    private static final Map<String, Long> pendingPossibleQQNum = new ConcurrentHashMap<>();

    private static final Object keyboard = TC.keyboard(List.of(
            List.of(new Button("c1", "绑定账号", "/verify ", false, ButtonStyle.BLUE, ButtonType.COMMAND))
    ));

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender.getPlatform() != Platform.OFFICIAL_C2C && sender.getPlatform() != Platform.OFFICIAL_GROUP) return true;

        if (args.length != 1) {
            Markdown md = TC.md("参数错误！请提供社区服务器内生成的验证码，格式: /verify <验证码>\n\n" + Markdown.img(ResourcesProperties.HOW_TO_VERIFY_GIF, 640, 360));
            sender.sendMessage(md, keyboard);
            return true;
        }

        String code = args[0].trim();

        long possibleQQNum = -1;
        if (pendingPossibleQQNum.containsKey(code)) {
            possibleQQNum = pendingPossibleQQNum.remove(code);
        }

        var result = MinecraftBind.bindAccount(sender.getUserId(), possibleQQNum, code, sender.getGroupId());
        return handleBindResponse(sender, label, result);
    }

    private static boolean handleBindResponse(CommandSender sender, String label, BindResponse result) {
        int statusCode = result.code();
        String uuid = result.uuid();

        switch (statusCode) {
            case 200:
                String headUrl = getPlayerHead(uuid);
                if (!headUrl.equals("-1")) {
                    String markdown = "> ✅ 绑定成功！\n> 玩家 UUID: `" + uuid + "`\n" + (OfficialGroups.isWhitelist(sender.getGroupId()) ? "> ![玩家头像 #96px #96px](" + headUrl + ")" : "");

                    List<List<Button>> layout = new ArrayList<>();
                    layout.add(Arrays.asList(
                            new Button("c1", "在档数据", "/stats " + uuid, true, ButtonStyle.GRAY, ButtonType.COMMAND),
                            new Button("c2", "成就数据", "/stats am " + uuid, true, ButtonStyle.GRAY, ButtonType.COMMAND),
                            new Button("c2", "小游戏数据", "/stats games " + uuid, true, ButtonStyle.GRAY, ButtonType.COMMAND)
                    ));
                    layout.add(List.of(
                            new Button("c3", "查询玩家在档数据", "/stats ", false, ButtonStyle.BLUE, ButtonType.COMMAND)
                    ));

                    Object keyboard = TC.keyboard(layout);
                    sender.sendMessage(TC.md(markdown), keyboard);
                }
                break;

            case 100:
                sender.sendMessage("⚠️ 绑定失败：你游戏内的账号已经绑定过其他 QQ 了！");
                break;
            case 400:
                sender.sendMessage("❌ 验证码错误或已过期(有效时间5分钟)，请在游戏内重新生成");
                break;
            case 500:
                sender.sendMessage("🔧 服务器未开启或网络异常，请稍后再试!");
                break;
            default:
                sender.sendMessage("❓ 未知错误代码: " + result);
                break;
        }
        return true;
    }

    private static String getPlayerHead(String uuid) {
        String url = ResourcesProperties.PLAYER_AVATAR_API.replace("{uuid}", uuid);

        int code = PreImageGenerate.create(url);
        if (code != 200) return "-1";
        return url;
    }

    @EventHandler
    public void onInfoGet(NapcatGroupMessageEvent event) {
        long userId = -1;
        try {
            Long.parseLong(event.getUser().getUserId());
        } catch (NumberFormatException _) {
            return;
        }
        String message = stripCQCode(event.getMessage().getContent().trim());
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