package top.yzljc.qqbot.official.function;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.qqbot.AtriBot;
import top.yzljc.qqbot.command.Command;
import top.yzljc.qqbot.command.CommandExecutor;
import top.yzljc.qqbot.command.CommandSender;
import top.yzljc.qqbot.config.Result;
import top.yzljc.qqbot.event.Listener;
import top.yzljc.qqbot.official.AtText;
import top.yzljc.qqbot.official.impl.BindMinecraft;
import top.yzljc.qqbot.official.impl.MinecraftUserData;
import top.yzljc.qqbot.official.permission.GroupList;
import top.yzljc.qqbot.official.service.CommandButton;
import top.yzljc.qqbot.official.service.QQBotMessageService;
import top.yzljc.qqbot.service.request.HttpService;
import top.yzljc.qqbot.service.request.SaSignHeader;
import top.yzljc.qqbot.utils.FormatTools;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
public class PlayerProfile implements Listener, CommandExecutor {

    private final QQBotMessageService service = AtriBot.getInstance().getMessageService();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (label.equals("0")) {
            sender.reply("请转官方机器人指令查询玩家数据喵！格式: @亚托莉喵 /stats <玩家名>");
            return true;
        }

        if (!GroupList.isWhitelist(sender.groupOpenId())) {
            sender.replyText(label, "该指令仅供YZ_Ljc_ Network社区使用喵！");
            return true;
        }

        if (args.length < 1) {
            MinecraftUserData data = BindMinecraft.getDataByOpenId(sender.userOpenId());
            if (data.memberOpenId().equals("-1")) {
                sender.replyText(label, "❌ 你还没有绑定游戏内账号喵！请先加入服务器使用`/verify`绑定账号！");
                return true;
            }

            String uuid = data.uuid();
            return getMarkdownText(uuid, label, sender, getKeyBoard(uuid));
        }

        String key = args[0];

        if (key.equalsIgnoreCase("am") || key.equalsIgnoreCase("achievements")) {
            String playerKey = args.length > 1 ? args[1] : null;
            return getAchievements(playerKey, label, sender, getKeyBoard(playerKey));
        }

        if (key.equalsIgnoreCase("games") || key.equalsIgnoreCase("gamestats")) {
            String playerKey = args.length > 1 ? args[1] : null;
            return getGameStats(playerKey, label, sender, getKeyBoard(playerKey));
        }

        if (key.equalsIgnoreCase("friends")) {
            String playerKey = args.length > 1 ? args[1] : null;
            return getFriends(playerKey, label, sender, getKeyBoard(playerKey));
        }

        return getMarkdownText(key, label, sender, getKeyBoard(key));
    }

    private Object getKeyBoard(String key) {
        List<List<CommandButton>> layout = new ArrayList<>();
        layout.add(Arrays.asList(
                new CommandButton("c1", "在档数据", "/stats " + key, true, 0, 2),
                new CommandButton("c2", "成就数据", "/stats am " + key, true, 0, 2),
                new CommandButton("c3", "小游戏数据", "/stats games " + key, true, 0, 2)
        ));
        layout.add(List.of(
                new CommandButton("c6", "好友数据", "/stats friends " + key, true, 0, 2)
        ));
        layout.add(List.of(
                new CommandButton("c4", "查询玩家在档数据", "/stats ", false, 1, 2)
        ));
        layout.add(List.of(
                new CommandButton("c5", "网页数据查询", "https://www.yzljc.top/query/", true, 1, 0)
        ));
        return service.buildCmdKeyboard(layout);
    }

    private static boolean getMarkdownText(String key, String label, CommandSender sender, Object keyboard) {

        if (key == null) return false;

        String messageId = sender.replyText(label, "正在获取玩家数据喵，请稍等片刻！");

        try {

            String url = "https://www.yzljc.top/data/api/v2/player/card/" + key + "?key=atri-player-card-2026@yzljc.top&timestamp=" + System.currentTimeMillis();

            HttpRequest preWarmRequest = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            HttpResponse<Void> response = HttpService.httpClient.send(preWarmRequest, HttpResponse.BodyHandlers.discarding());
            HttpService.httpClient.send(preWarmRequest, HttpResponse.BodyHandlers.discarding());

            if (label.equals("1")) {
                AtriBot.getInstance().getMessageService().recallPrivateMessage(sender.userOpenId(), messageId);
            } else {
                AtriBot.getInstance().getMessageService().recallGroupMessage(sender.groupOpenId(), messageId);
            }

            if (response.statusCode() != 200) {
                if (response.statusCode() == 404) {
                    sender.replyText(label, "未找到玩家数据，请检查玩家名或UUID是否正确喵！");
                } else {
                    sender.replyText(label, "无法获取玩家数据，服务器返回状态码: " + response.statusCode() + "，大概率是服务器死了喵");
                }
                return true;
            }

            String text = "玩家 **" + key + "** 的在档数据如下";
            sender.replyMarkdown(label, text + "\n" + "![图片 #1188px #1188px](" + url + ")", keyboard);

        } catch (Exception e) {
            log.error("发生异常: ", e);
            sender.replyText(label, "获取数据失败，请稍后再试！");
        }
        return true;
    }

    public static boolean getAchievements(String key, String label, CommandSender sender, Object keyboard) {

        if (key == null) return false;

        try {
            String dataNameUrl = "https://www.yzljc.top/data/api/v2/player/achievements/name/{name}";
            String dataUuidUrl = "https://www.yzljc.top/data/api/v2/player/achievements/uuid/{uuid}";

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
                    sender.replyText(label, "未找到玩家数据，请检查玩家名或UUID是否正确喵！");
                } else {
                    sender.replyText(label, "无法获取玩家数据，服务器返回状态码: " + responseCode + "，大概率是服务器死了喵");
                }
                return true;
            }

            StringBuilder markdown = new StringBuilder("玩家 **" + key + "** 的成就数据如下：\n\n");
            for (Achievement ach : resultObject.getData()) {
                String statusEmoji = ach.finished() ? "✅" : "❌";
                String title = (ach.hidden && !ach.finished) ? "**隐藏成就**" : String.format("**%s** %s ", ach.name(), statusEmoji);
                String body = (ach.hidden && !ach.finished) ? "> ???" : "> " + ach.description() + " (+" + ach.rewardPoints() + "成就点数)";
                String resultPart = title + "\n" + body + "\n\n";
                markdown.append(resultPart);
            }

            sender.replyMarkdown(label, markdown.toString(), keyboard);

            return true;
        } catch (Exception e) {
            log.error("发生异常: ", e);
            sender.replyText(label, "获取数据失败，请稍后再试！");
            return true;
        }
    }

    public static boolean getFriends(String key, String label, CommandSender sender, Object keyboard) {

        if (key == null) return false;

        try {
            String dataNameUrl = "https://www.yzljc.top/data/api/v2/player/friends/name/{name}";
            String dataUuidUrl = "https://www.yzljc.top/data/api/v2/player/friends/uuid/{uuid}";

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
                    sender.replyText(label, "未找到玩家数据，请检查玩家名或UUID是否正确喵！");
                } else if (responseCode == 201) {
                    sender.replyText(label, "该玩家没有好友喵！");
                } else {
                    sender.replyText(label, "无法获取玩家数据，服务器返回状态码: " + responseCode + "，大概率是服务器死了喵");
                }
                return true;
            }

            String markdown = "玩家 **" + key + "** 的好友列表如下：\n\n";

            FriendsData data = resultObject.getData();
            if (data.friendList() == null || data.friendList().isEmpty()) {
                sender.replyText(label, "该玩家没有好友喵！");
                return true;
            }

            for (FriendsData.FriendInfo friend : data.friendList()) {
                String name = "**" + friend.friendName() + "**";
                String body = "> UUID: `" + friend.friendUuid() + "`\n> 添加时间: `" + FormatTools.formatTimestampMilli(friend.addTime()) + "`";
                markdown += name + "\n" + body + "\n\n";
            }

            sender.replyMarkdown(label, markdown, keyboard);

            return true;
        } catch (Exception e) {
            log.error("发生异常: ", e);
            sender.replyText(label, "获取数据失败，请稍后再试！");
            return true;
        }
    }

    public static boolean getGameStats(String key, String label, CommandSender sender, Object keyboard) {

        if (key == null) return false;

        try {
            String dataNameUrl = "https://www.yzljc.top/data/api/v2/player/gamestats/name/{name}";
            String dataUuidUrl = "https://www.yzljc.top/data/api/v2/player/gamestats/uuid/{uuid}";

            String finalUrl;

            if (key.length() > 16) {
                finalUrl = dataUuidUrl.replace("{uuid}", key);
            } else {
                finalUrl = dataNameUrl.replace("{name}", key);
            }

            String result = HttpService.getRequestStr(finalUrl);
            Result<PlayerGameStatsResponse> resultObject = new ObjectMapper().readValue(result, new TypeReference<>() {
            });

            int responseCode = resultObject.getStatus();

            if (responseCode != 200) {
                if (responseCode == 404) {
                    sender.replyText(label, "未找到玩家数据，请检查玩家名或UUID是否正确喵！");
                } else {
                    sender.replyText(label, "无法获取玩家数据，服务器返回状态码: " + responseCode + "，大概率是服务器死了喵");
                }
                return true;
            }

            String markdown = "玩家 **" + key + "** 的大厅小游戏数据如下：\n\n";

            PlayerGameStatsResponse data = resultObject.getData();
            markdown += String.format("**飞行执照挑战**\n> 最佳成绩: %dms\n\n", data.flyLicenseStats.bestTime());
            markdown += String.format("**慧眼识铜**\n> 最佳成绩: 第%d轮\n\n", data.copperGameStats.bestRound());
            markdown += String.format("**礼物猎手(2026)**\n> 已找到礼物数量: %d\n\n", data.presentsClaimedStats.count());
            markdown += "**音游挑战**\n";
            markdown += "| 曲目 | 最佳准确率 |\n";
            markdown += "|:-----|-----------:|\n";
            for (Map.Entry<String, Double> entry : data.melodyStats.melodyBestAccuracyByTrack().entrySet()) {
                markdown += String.format("| %s | %.2f%% |\n", entry.getKey(), entry.getValue());
            }
            markdown += String.format("\n**街机游戏综合**\n击鸡即急寄\n> 最佳存活：%d轮, 最佳得分：%d, 总击杀数：%d\n",
                    data.arcadeGameData.maxRound(), data.arcadeGameData.maxScore(), data.arcadeGameData.totalKills());

            sender.replyMarkdown(label, markdown, keyboard);

            return true;
        } catch (Exception e) {
            log.error("发生异常: ", e);
            sender.replyText(label, "获取数据失败，请稍后再试！");
            return true;
        }
    }

    private record Achievement(String id, String name, String description, int rewardPoints, boolean finished,
                               boolean hidden) {
    }

    public record FriendsData(String uuid, List<FriendInfo> friendList) {
        public record FriendInfo(String friendName, String friendUuid, long addTime) {}
    }


    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class PlayerGameStatsResponse {
        private String uuid;
        private FlyLicenseStats flyLicenseStats;
        private CopperGameStats copperGameStats;
        private PresentsClaimedStats presentsClaimedStats;
        private MelodyStats melodyStats;
        private ArcadeGameData arcadeGameData;

        public record FlyLicenseStats(long bestTime) {
        }

        public record CopperGameStats(int bestRound) {
        }

        public record PresentsClaimedStats(int count) {
        }

        public record MelodyStats(Map<String, Double> melodyBestAccuracyByTrack) {
        }

        public record ArcadeGameData(int maxRound, int maxScore, int totalKills) {
        }
    }
}