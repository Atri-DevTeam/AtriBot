package top.yzljc.qqbot.feature.minecraft;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import top.yzljc.qqbot.botkits.message.MessageSender;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Executors;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minecraft 玩家全网数据查询
 */
public class McNetworkInfo {

    private static final Logger log = LoggerFactory.getLogger(McNetworkInfo.class);

    private static final String CMD_PREFIX = "/hbt";
    private static final String API_URL = "http://mc.yzljc.top:65123/playerinfo?name=";
    private static final ObjectMapper mapper = new ObjectMapper();

    // 使用统一的时间格式
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public static void process(JsonNode json) {
        if (!"message".equals(json.path("post_type").asText())) return;
        if (!"group".equals(json.path("message_type").asText())) return;

        String rawMsg = json.path("raw_message").asText().trim();
        long groupId = json.path("group_id").asLong();

        // 识别指令
        if (rawMsg.toLowerCase().startsWith(CMD_PREFIX)) {
            String[] parts = rawMsg.split("\\s+", 2);
            if (parts.length < 2 || parts[1].trim().isEmpty()) {
                MessageSender.sendGroupMessage(groupId, "用法错误。正确格式： " + CMD_PREFIX + " [玩家名]");
                return;
            }

            String playerName = parts[1].trim();
            Executors.newSingleThreadExecutor().submit(() -> fetchAndSend(groupId, playerName));
        }
    }

    private static void fetchAndSend(long groupId, String playerName) {
        try {
            String encodedName = URLEncoder.encode(playerName, StandardCharsets.UTF_8);
            String finalUrl = API_URL + encodedName;

            log.info("正在请求：{}", finalUrl);

            HttpURLConnection conn = (HttpURLConnection) new URI(finalUrl).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int responseCode = conn.getResponseCode();
            if (responseCode == 404) {
                MessageSender.sendGroupMessage(groupId, "❌ 未找到玩家 [" + playerName + "] 的信息记录，请先进入一次服务器。");
                return;
            }
            if (responseCode != 200) {
                MessageSender.sendGroupMessage(groupId, "⚠️ 查询失败，服务器响应异常 (Code: " + responseCode + ")");
                return;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            JsonNode root = mapper.readTree(reader);
            conn.disconnect();

            // 1. 基础信息读取
            String ign = root.path("ign").asText("未知");
            String uuid = root.path("uuid").asText("未知");
            String rank = root.path("permissionGroup").asText("DEFAULT");
            String title = root.path("currentTitle").asText("");

            // 2. 时间戳处理
            // 直接将 long 值传给 Date，不再进行任何 magnitude (量级) 猜测，确保兼容公元 1111 年 (-27080340872000)
            String firstJoin = formatRawTimestamp(root.path("firstJoinDate").asLong(0));
            String lastJoin = formatRawTimestamp(root.path("lastJoinTime").asLong(0));
            String lastQuit = formatRawTimestamp(root.path("lastQuitTime").asLong(0));

            // 3. 在线时间计算 (分钟转天/时/分)
            long totalMinutes = root.path("totalPlayTime").asLong(0);
            String playTimeFormatted = formatPlayTime(totalMinutes);

            // 4. 字段映射
            String giftHunter = "yes".equalsIgnoreCase(root.path("giftHunterInfo").asText()) ? "完成" : "未完成";
            String unbanMode = "yes".equalsIgnoreCase(root.path("unbanModeInfo").asText()) ? "开启" : "关闭";
            String canBuild = root.path("canBuildHub").asBoolean(false) ? "有权限" : "无权限";

            String specialTitleRaw = root.path("specialTitleInfo").asText("no");
            String specialTitle;
            if ("yes".equalsIgnoreCase(specialTitleRaw)) {
                specialTitle = "拥有";
            } else if ("no".equalsIgnoreCase(specialTitleRaw)) {
                specialTitle = "无";
            } else {
                specialTitle = specialTitleRaw;
            }

            // 5. 组装消息
            StringBuilder sb = new StringBuilder();
            sb.append("🎮 Hypixel Ban Test 玩家信息查询 🎮\n");
            sb.append("------------------------\n");
            sb.append("玩家名称: ").append(ign).append("\n");
            sb.append("称号: ").append(title.isEmpty() ? "无" : title).append("\n");
            sb.append("权限组: ").append(rank).append("\n");
            sb.append("UUID: ").append(uuid).append("\n");
            sb.append("------------------------\n");
            sb.append("首次加入: ").append(firstJoin).append("\n");
            sb.append("累计在线: ").append(playTimeFormatted).append("\n");
            sb.append("上次登录: ").append(lastJoin).append("\n");
            sb.append("上次退出: ").append(lastQuit).append("\n");
            sb.append("------------------------\n");
            sb.append("礼物猎手: ").append(giftHunter).append("\n");
            sb.append("解封模式: ").append(unbanMode).append("\n");
            sb.append("特殊称号: ").append(specialTitle).append("\n");
            sb.append("主城建筑权限: ").append(canBuild);
            sb.append("\n\n※ 如果无记录，请先进入一次服务器。");

            MessageSender.sendGroupMessage(groupId, sb.toString());

        } catch (Exception e) {
            log.warn("查询出错，请检查 API 接口：{}", e.getMessage());
            MessageSender.sendGroupMessage(groupId, "❌ 查询出错，请检查 API 接口。");
        }
    }

    private static String formatPlayTime(long totalMinutes) {
        if (totalMinutes <= 0) return "0分钟";
        long days = totalMinutes / (24 * 60);
        long hours = (totalMinutes % (24 * 60)) / 60;
        long minutes = totalMinutes % 60;
        StringBuilder result = new StringBuilder();
        if (days > 0) result.append(days).append("天");
        if (hours > 0) result.append(hours).append("小时");
        if (minutes > 0 || result.length() == 0) result.append(minutes).append("分钟");
        return result.toString();
    }

    /**
     * 原始毫秒时间戳转换
     * 不再包含任何 if 判断，直接信任传入的毫秒值
     */
    private static String formatRawTimestamp(long timestamp) {
        if (timestamp == 0) return "无记录";
        // Java Date(long) 构造函数完美支持负数时间戳（1970年以前）
        return sdf.format(new Date(timestamp));
    }
}
