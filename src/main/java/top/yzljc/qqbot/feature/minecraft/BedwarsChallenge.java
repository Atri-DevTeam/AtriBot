package top.yzljc.qqbot.feature.minecraft;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.botservice.request.HttpRequest;
import top.yzljc.qqbot.chat.impl.MessageUtils;
import top.yzljc.qqbot.command.Command;
import top.yzljc.qqbot.command.CommandExecutor;
import top.yzljc.qqbot.command.CommandSender;
import top.yzljc.qqbot.data.VarData;
import top.yzljc.qqbot.botservice.thread.ThreadManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BedwarsChallenge implements CommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(BedwarsChallenge.class);

    private static final String[] CHALLENGE = {
            "no_team_upgrades", "no_utilities", "selfish", "slow_generator", "assassin", "reset_armor",
            "invisible_shop", "collector", "woodworker", "sponge", "toxic_rain", "defuser", "mining_fatigue",
            "no_healing", "hotbar", "weighted_items", "knockback_stick_only", "no_swords", "archer_only",
            "patriot", "stamina", "no_sprint", "capped_resources", "stop_light", "delayed_hitting",
            "no_hitting", "master_assassin", "no_shift", "protect_the_president", "cant_touch_this"
    };

    private static final String[] CHALLENGE_NAME = {
            "无队之谈", "公正决斗！", "无他，自用", "财源......难进", "刺客", "失去如常",
            "无形之店", "环队收藏家", "木工与床", "傻人建傻桥", "小心毒雨！", "拆弹......拆床！", "慢镐也穿石",
            "极限生存·起床·冠军", "一手遮天", "负重前行", "保持社交距离！", "剑锋略钝", "神射手",
            "唯此一心", "耐力极限", "行稳致远", "能源危机", "红灯停，绿灯行", "反射弧......过长！",
            "世界\"核\"平", "刺杀，仅此一敌", "挺立，不可退缩", "一主，不得僭越", "破敌，不染一尘"
    };

    private static final double[] CLG_DFC = {
            2, 4.5, 6, 8, 2, 2, 8, 1, 8.5, 13, 15, 3, 5, 4.5, 7.5, 7, 8, 8.5, 10, 7, 8.5, 12.5, 8, 12, 9, 8, 5, 12.5, 6, 18
    };

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args == null || args.length == 0) {
            return false;
        }
        if ("api".equalsIgnoreCase(args[0])) {
            if (args.length < 2) {
                return false;
            }
            String key = args[1].trim();
            VarData.saveBwcApiKey(key);
            sender.reply("Hypixel API Key 已保存。", false);
            return true;
        }

        String playerName = args[0].trim();
        if (playerName.isEmpty()) {
            return false;
        }

        String apiKey = VarData.loadBwcApiKey();
        if (apiKey.isEmpty()) {
            sender.reply("请先使用 /bwc api <API Key> 设置 Hypixel API Key", false);
            return true;
        }

        ThreadManager.execute(() -> queryAndReply(sender, playerName, apiKey));
        return true;
    }

    private void queryAndReply(CommandSender sender, String playerName, String apiKey) {
        try {
//            String mojangUrl = "https://api.mojang.com/users/profiles/minecraft/" + playerName;
//            JsonNode mojangResp = HttpRequest.sendGetRequest(mojangUrl);
//            if (mojangResp == null || !mojangResp.has("id")) {
//                sender.reply("未找到该玩家", false);
//                return;
//            }
//            String uuid = mojangResp.path("id").asText();
//            String displayName = mojangResp.has("name") ? mojangResp.path("name").asText() : playerName;

            String hypixelUrl = "https://api.hypixel.net/player?key=" + apiKey + "&name=" + playerName;
            JsonNode hypixelResp = HttpRequest.sendGetRequest(hypixelUrl);
            if (hypixelResp == null || !hypixelResp.path("success").asBoolean(false)) {
                sender.reply("Hypixel API 请求失败或该玩家未游玩过 Bedwars（或 API Key 无效/过期）", false);
                return;
            }
            JsonNode player = hypixelResp.path("player");
            if (player.isMissingNode()) {
                sender.reply("未获取到玩家数据。", false);
                return;
            }
            JsonNode bwStats = player.path("stats").path("Bedwars");
            if (bwStats.isMissingNode()) {
                sender.reply("这人甚至都没玩过 Bedwars（或 API 已过期？请重新设置 Key）", false);
                return;
            }

            int uc = 0;
            int tc = 0;
            if (bwStats.has("bw_unique_challenges_completed")) {
                uc = bwStats.path("bw_unique_challenges_completed").asInt(0);
            }
            if (bwStats.has("total_challenges_completed")) {
                tc = bwStats.path("total_challenges_completed").asInt(0);
            }

            int[] times = new int[30];
            String[] timeStr = new String[30];
            for (int i = 0; i < 30; i++) {
                times[i] = 0;
                timeStr[i] = "未获取";
            }

            JsonNode challenges = bwStats.path("challenges");
            for (int i = 0; i < 30; i++) {
                String tk1 = "bw_challenge_" + CHALLENGE[i];
                String tk2 = tk1 + "_best_time";
                if (bwStats.has(tk1)) {
                    times[i] = bwStats.path(tk1).asInt(0);
                }
                if (!challenges.isMissingNode() && challenges.has(tk2)) {
                    long ms = challenges.path(tk2).asLong(0);
                    timeStr[i] = msToNm(ms);
                }
            }

            double s = 0;
            StringBuilder out = new StringBuilder();
            out.append(playerName).append("的挑战完成状况如下:\n");
            out.append("挑战完成数:").append(uc).append(" 总计完成数:").append(tc);
            for (int i = 0; i < 30; i++) {
                out.append("\n").append(CHALLENGE_NAME[i])
                        .append(":挑战胜场数:").append(times[i])
                        .append(" 最快完成:").append(timeStr[i]);
                s += times[i] * CLG_DFC[i];
            }
            if (tc != 0) {
                s /= tc;
            }
            out.append("\n").append(String.format("%.2f", s)).append("/10(仅作参考，挑战难度因人而定)");
            List<Map<String,Object>> result = new ArrayList<>();
            result.add(MessageUtils.createTextNode(out.toString(), "3199590352", "YZ_Ljc_"));
            MessageUtils.sendGroupForwardMessage(sender.groupId(), result, "起床战争挑战完成情况", "查看详细数据",
                    playerName + "挑战情况:", "总计完成数: " + tc, "评分: " + String.format("%.2f", s) + "/10 (仅作参考)");        } catch (Exception e) {
            log.warn("BWC 查询异常: {}", e.getMessage(), e);
            sender.reply("查询时发生错误: " + e.getMessage(), false);
        }
    }

    private static String msToNm(long ms) {
        long h = ms / (60 * 60 * 1000);
        long min = ms / (60 * 1000) - h * 60;
        long sec = ms / 1000 - min * 60;
        long msPart = ms - (ms / 1000) * 1000;
        return String.format("%02d:%02d:%02d(+%dms)", h, min, sec, msPart);
    }
}
