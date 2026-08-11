package top.yzljc.atribot.function.official;

import top.yzljc.atribot.configuration.ResourcesProperties;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.function.impl.PreImageGenerate;
import top.yzljc.atribot.function.official.minecraft.MinecraftBind;
import top.yzljc.atribot.function.official.minecraft.MinecraftUserData;
import top.yzljc.atribot.platform.Identifier;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.service.request.HttpService;
import top.yzljc.atribot.utils.FormatTools;
import top.yzljc.atribot.webui.Result;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
public class PlayerProfile implements Listener, CommandExecutor {

    private static final Object keyboard = TC.keyboard(List.of(
            List.of(new Button("c1", "绑定账号", "/verify ", false, ButtonStyle.BLUE, ButtonType.COMMAND))
    ));

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof QQCommandSender qq)) {
            return true;
        }

        boolean isWhiteListed = false;
        if (qq.getPlatform() == Platform.OFFICIAL_GROUP) {
            if (OfficialGroups.isWhitelist(qq.getGroupId())) isWhiteListed = true;
        }

        if (args.length < 1) {
            MinecraftUserData data = MinecraftBind.getDataByOpenId(qq.getUserId());
            if (data.memberOpenId().equals("-1")) {
                Markdown md = TC.md("您尚未绑定玩家身份，请先加入社区服务器使用`/verify`获取绑定码！");
                qq.sendMessage(md, keyboard);
                return true;
            }

            String uuid = data.uuid();
            return getMarkdownText(uuid, qq, getKeyBoard(uuid), isWhiteListed);
        }

        String key = args[0];

        if (key.equalsIgnoreCase("am") || key.equalsIgnoreCase("achievements")) {
            String playerKey = args.length > 1 ? args[1] : null;
            return getAchievements(playerKey, qq, getKeyBoard(playerKey), isWhiteListed);
        }

        if (key.equalsIgnoreCase("friends")) {
            String playerKey = args.length > 1 ? args[1] : null;
            return getFriends(playerKey, qq, getKeyBoard(playerKey), isWhiteListed);
        }

        return getMarkdownText(key, qq, getKeyBoard(key), isWhiteListed);
    }

    private Object getKeyBoard(String key) {
        List<List<Button>> layout = new ArrayList<>();
        layout.add(Arrays.asList(
                new Button("c1", "在档数据", "/stats " + key, true, ButtonStyle.GRAY, ButtonType.COMMAND),
                new Button("c2", "成就数据", "/stats am " + key, true, ButtonStyle.GRAY, ButtonType.COMMAND),
                new Button("c6", "好友数据", "/stats friends " + key, true, ButtonStyle.GRAY, ButtonType.COMMAND)
        ));
        layout.add(List.of(
                new Button("c4", "查询玩家在档数据", "/stats ", false, ButtonStyle.BLUE, ButtonType.COMMAND)
        ));
        layout.add(List.of(
                new Button("c5", "网页数据查询", ResourcesProperties.PLAYER_WEB_QUERY, true, ButtonStyle.BLUE, ButtonType.LINK)
        ));
        return TC.keyboard(layout);
    }

    private static boolean getMarkdownText(String key, QQCommandSender sender, Object keyboard, boolean isWhitelisted) {

        if (key == null) return false;

        String messageId = sender.sendMessage("正在获取玩家数据喵，请稍等片刻！");
        var d = PreImageGenerate.dump(ResourcesProperties.PLAYER_CARD_API, Map.of("player", key, "whitelist", isWhitelisted));
        sender.recall(messageId);

        if (d.url() == null) {
            sender.sendMessage(Identifier.HANDLER_ERROR);
            return true;
        }

        String text = "玩家 **" + (isWhitelisted ? key : "---") + "** 的在档数据如下";
        sender.sendMessage(TC.md(text + "\n\n" + Markdown.img(d.url(), d.width(), d.height())), keyboard);

        return true;
    }

    public static boolean getAchievements(String key, QQCommandSender sender, Object keyboard, boolean isWhitelisted) {

        if (key == null) return false;

        try {
            String dataNameUrl = ResourcesProperties.PLAYER_ACHIEVEMENTS_NAME_API;
            String dataUuidUrl = ResourcesProperties.PLAYER_ACHIEVEMENTS_UUID_API;

            String finalUrl;

            if (key.length() > 16) {
                finalUrl = dataUuidUrl.replace("{uuid}", key);
            } else {
                finalUrl = dataNameUrl.replace("{name}", key);
            }

            String result = HttpService.getRequestStr(finalUrl);
            Result<List<Achievement>> resultObject = new ObjectMapper().readValue(result, new TypeReference<>() {
            });

            int responseCode = resultObject.getStatus();

            if (responseCode != 200) {
                if (responseCode == 404) {
                    sender.sendMessage("未找到玩家数据，请检查玩家名或UUID是否正确喵！");
                } else {
                    sender.sendMessage("无法获取玩家数据，服务器返回状态码: " + responseCode + "，大概率是服务器死了喵");
                }
                return true;
            }

            StringBuilder markdown = new StringBuilder("玩家 **" + (isWhitelisted ? key : "---") + "** 的成就数据如下：\n\n");
            for (Achievement ach : resultObject.getData()) {
                String statusEmoji = ach.finished() ? "✅" : "❌";
                String title = (ach.hidden && !ach.finished) ? "**隐藏成就**" : String.format("**%s** %s ", ach.name(), statusEmoji);
                String body = (ach.hidden && !ach.finished) ? "> ???" : "> " + ach.description() + " (+" + ach.rewardPoints() + "成就点数)";
                String resultPart = title + "\n" + body + "\n\n";
                markdown.append(resultPart);
            }

            sender.sendMessage(TC.md(markdown.toString()), keyboard);

            return true;
        } catch (Exception e) {
            log.error("发生异常: ", e);
            sender.sendMessage("获取数据失败，请稍后再试！");
            return true;
        }
    }

    public static boolean getFriends(String key, QQCommandSender sender, Object keyboard, boolean isWhitelisted) {

        if (key == null) return false;

        try {
            String dataNameUrl = ResourcesProperties.PLAYER_FRIENDS_NAME_API;
            String dataUuidUrl = ResourcesProperties.PLAYER_FRIENDS_UUID_API;

            String finalUrl;

            if (key.length() > 16) {
                finalUrl = dataUuidUrl.replace("{uuid}", key);
            } else {
                finalUrl = dataNameUrl.replace("{name}", key);
            }

            String result = HttpService.getRequestStr(finalUrl);
            Result<FriendsData> resultObject = new ObjectMapper().readValue(result, new TypeReference<>() {
            });

            int responseCode = resultObject.getStatus();

            if (responseCode != 200) {
                if (responseCode == 404) {
                    sender.sendMessage("未找到玩家数据，请检查玩家名或UUID是否正确喵！");
                } else if (responseCode == 201) {
                    sender.sendMessage("该玩家没有好友喵！");
                } else {
                    sender.sendMessage("无法获取玩家数据，服务器返回状态码: " + responseCode + "，大概率是服务器死了喵");
                }
                return true;
            }

            String markdown = "玩家 **" + (isWhitelisted ? key : "---") + "** 的好友列表如下：\n\n";

            FriendsData data = resultObject.getData();
            if (data.friendList() == null || data.friendList().isEmpty()) {
                sender.sendMessage("该玩家没有好友喵！");
                return true;
            }

            for (FriendsData.FriendInfo friend : data.friendList()) {
                String name = "**" + (isWhitelisted ? friend.friendName() : maskMiddle(friend.friendName())) + "**";
                String body = "> UUID: `" + friend.friendUuid() + "`\n> 添加时间: `" + FormatTools.formatTimestampMilli(friend.addTime()) + "`";
                markdown += name + "\n" + body + "\n\n";
            }

            sender.sendMessage(TC.md(markdown), keyboard);

            return true;
        } catch (Exception e) {
            log.error("发生异常: ", e);
            sender.sendMessage("获取数据失败，请稍后再试！");
            return true;
        }
    }

    public static String maskMiddle(String str) {
        if (str == null || str.length() <= 2) {
            return str;
        }

        char[] chars = str.toCharArray();
        for (int i = 1; i < chars.length - 1; i++) {
            chars[i] = '*';
        }
        return new String(chars);
    }

    private record Achievement(String id, String name, String description, int rewardPoints, boolean finished,
                               boolean hidden) {
    }

    public record FriendsData(String uuid, List<FriendInfo> friendList) {
        public record FriendInfo(String friendName, String friendUuid, long addTime) {
        }
    }
}
