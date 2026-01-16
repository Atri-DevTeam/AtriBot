package top.yzljc.qqbot.feature;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;
import top.yzljc.qqbot.botkits.message.MessageSender;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public class CheckBilibili {


    private static final Logger log = LoggerFactory.getLogger(CheckBilibili.class);

    private static final String LIST_FILE = "bvidlist.json";

    private static final ObjectMapper jsonMapper = new ObjectMapper();

    static Settings settings = Config.getInstance();
    private static final String SESSDATA = settings.getBilibiliCookie();

    public static void process(JsonNode json) {
        String messageType = json.path("message_type").asText();
        if (!"group".equals(messageType)) return;

        String rawMessage = json.path("raw_message").asText().trim();
        long groupId = json.path("group_id").asLong();
        String lowerMsg = rawMessage.toLowerCase();

        // 匹配 /bl BVxxxxxx
        if (lowerMsg.startsWith("/bl bv")) {
            String bvid = rawMessage.substring(4).trim();
            if (!bvid.isEmpty()) {
                fetchVideoDetail(groupId, bvid);
            } else {
                MessageSender.sendGroupMessage(groupId, "请提供 BV 号，例如: /bl BV1xx411c7");
            }
        }
        // 匹配 /bl 数字 (查询历史记录)
        else if (lowerMsg.startsWith("/bl ")) {
            try {
                String numStr = rawMessage.substring(4).trim();
                int index = Integer.parseInt(numStr);
                String savedBvid = getBvidByIndex(index);

                if (savedBvid != null) {
                    MessageSender.sendGroupMessage(groupId, "正在查询历史记录 #" + index + " (" + savedBvid + ")...");
                    fetchVideoDetail(groupId, savedBvid);
                } else {
                    MessageSender.sendGroupMessage(groupId, "未找到序号为 " + index + " 的记录。");
                }
            } catch (NumberFormatException e) {
                // 忽略非数字输入
            }
        }
    }

    private static void fetchVideoDetail(long groupId, String bvid) {
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                // 1. 直接请求详情接口 (1次请求)
                String viewUrl = "https://api.bilibili.com/x/web-interface/view?bvid=" + bvid;
                JsonNode root = sendBilibiliRequest(viewUrl);

                if (root == null) {
                    log.warn("view interface no response");
                    MessageSender.sendGroupMessage(groupId, "B站接口无响应 (可能是网络或IP封禁)");
                    return;
                }

                int code = root.path("code").asInt();
                if (code != 0) {
                    String msg = root.path("message").asText();
                    log.warn("API Error: {} (Code: {})", msg, code);
                    MessageSender.sendGroupMessage(groupId, "查询失败: " + msg + " (Code: " + code + ")");
                    return;
                }

                JsonNode data = root.path("data");
                JsonNode stat = data.path("stat");

                // 2. 解析数据
                String title = data.path("title").asText();
                String upName = data.path("owner").path("name").asText();
                String uid = data.path("owner").path("mid").asText();
                long created = data.path("pubdate").asLong();
                long cid = data.path("cid").asLong();

                String timeStr = Instant.ofEpochSecond(created)
                        .atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

                // 3. 获取正在看
                String online = getOnline(bvid, cid);

                // 4. 保存 BV 号到本地并获取序号
                int savedIndex = saveBvid(bvid, title);

                // 5. 控制台日志
                log.info("========== [Bilibili Query Log] ==========");
                log.info("Index: {}", savedIndex);
                log.info("BVID:  {}", bvid);
                log.info("Title: {}", title);
                log.info("==========================================");
                StringBuilder sb = new StringBuilder();
                sb.append("标题: ").append(title).append("\n");
                sb.append("UP主: ").append(upName).append("\n");
                sb.append("BV号: ").append(bvid).append("\n");
                sb.append("发布: ").append(timeStr).append("\n");
                sb.append("----------------\n");
                sb.append("播放: ").append(formatNum(stat.path("view").asInt())).append("\n");
                sb.append("点赞: ").append(formatNum(stat.path("like").asInt())).append("\n");
                sb.append("投币: ").append(formatNum(stat.path("coin").asInt())).append("\n");
                sb.append("收藏: ").append(formatNum(stat.path("favorite").asInt())).append("\n");
                sb.append("分享: ").append(formatNum(stat.path("share").asInt())).append("\n");
                sb.append("在看: ").append(online).append("\n");
                sb.append("----------------\n");
                sb.append("已记录! 下次可用 /bl ").append(savedIndex).append(" 快速查询");

                MessageSender.sendGroupMessage(groupId, sb.toString());

            } catch (Exception e) {
                e.printStackTrace();
                log.warn("Exception: {}", e.getMessage());
                MessageSender.sendGroupMessage(groupId, "处理异常: " + e.getMessage());
            }
        });
    }

    // 保存 BV 号到 JSON 文件，返回其序号
    private static synchronized int saveBvid(String bvid, String title) {
        try {
            File file = new File(LIST_FILE);
            ArrayNode rootArray;

            if (file.exists()) {
                JsonNode node = jsonMapper.readTree(file);
                if (node.isArray()) {
                    rootArray = (ArrayNode) node;
                } else {
                    rootArray = jsonMapper.createArrayNode();
                }
            } else {
                rootArray = jsonMapper.createArrayNode();
            }

            // 检查是否已存在，如果存在直接返回序号 (序号 = index + 1)
            for (int i = 0; i < rootArray.size(); i++) {
                if (rootArray.get(i).path("bvid").asText().equals(bvid)) {
                    return i + 1;
                }
            }

            // 如果不存在，添加到末尾
            ObjectNode newItem = jsonMapper.createObjectNode();
            newItem.put("bvid", bvid);
            newItem.put("title", title); // 顺便存个标题方便以后看
            newItem.put("time", System.currentTimeMillis());
            rootArray.add(newItem);

            // 写回文件
            jsonMapper.writerWithDefaultPrettyPrinter().writeValue(file, rootArray);

            return rootArray.size(); // 返回新的序号

        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    // 根据序号获取 BV 号
    private static synchronized String getBvidByIndex(int index) {
        try {
            File file = new File(LIST_FILE);
            if (!file.exists()) return null;

            JsonNode node = jsonMapper.readTree(file);
            if (node.isArray() && index > 0 && index <= node.size()) {
                // 用户输入 1，对应数组下标 0
                return node.get(index - 1).path("bvid").asText();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static String getOnline(String bvid, long cid) {
        try {
            String url = "https://api.bilibili.com/x/player/online/total?bvid=" + bvid + "&cid=" + cid;
            JsonNode root = sendBilibiliRequest(url);
            if (root != null && root.path("code").asInt() == 0) {
                return root.path("data").path("total").asText();
            }
        } catch (Exception ignored) {}
        return "-";
    }

    private static JsonNode sendBilibiliRequest(String urlStr) {
        try {
            URL url = new URI(urlStr).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

            if (urlStr.contains("bvid=")) {
                String bvidPart = urlStr.substring(urlStr.indexOf("bvid=") + 5);
                int ampIndex = bvidPart.indexOf("&");
                if (ampIndex > 0) bvidPart = bvidPart.substring(0, ampIndex);
                conn.setRequestProperty("Referer", "https://www.bilibili.com/video/" + bvidPart);
            } else {
                conn.setRequestProperty("Referer", "https://www.bilibili.com/");
            }

            if (SESSDATA != null && !SESSDATA.isEmpty()) {
                conn.setRequestProperty("Cookie", "SESSDATA=" + SESSDATA);
            }

            if (conn.getResponseCode() == 200) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);
                    return jsonMapper.readTree(response.toString());
                }
            }
        } catch (Exception e) {
            log.warn("[Bili API] Request Failed: {}", e.getMessage());
        }
        return null;
    }

    private static String formatNum(int num) {
        if (num >= 10000) return String.format("%.1f万", num / 10000.0);
        return String.valueOf(num);
    }
}
