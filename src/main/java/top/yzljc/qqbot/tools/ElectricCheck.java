package top.yzljc.qqbot.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import top.yzljc.qqbot.messages.MessageSender;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class ElectricCheck {
    private static final ObjectMapper jsonMapper = new ObjectMapper();
    // 电费查询接口
    private static final String QUERY_URL = "https://di.tjufe.edu.cn:8088/CardApp2021/ElecSearch.php?ec=903004&xq=1";
    // 允许的群组
    private static final long[] ALLOWED_GROUPS = {1065552660L, 818804507L, 413478250L, 1041561558L};
    // 触发关键词
    private static final String[] KEYWORDS = {"电表", "dianbiao", "db"};

    public static void processElectric(JsonNode json) {
        if (!json.has("group_id") || !json.has("raw_message")) return;

        long groupId = json.path("group_id").asLong();
        String rawMessage = json.path("raw_message").asText().trim().toLowerCase();

        if (!isAllowedGroup(groupId) || !containsKeyword(rawMessage)) return;

        Executors.newSingleThreadExecutor().submit(() -> {
            String feedback;
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(QUERY_URL).openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                String respStr = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                conn.getInputStream().close();

                JsonNode respJson = null;
                try { respJson = jsonMapper.readTree(respStr); } catch (Exception ignored) {}

                if (respJson != null) {
                    String rec = respJson.path("rec").asText();
                    String rsmd = respJson.path("rsmd").asText();
                    String rsfd = respJson.path("rsfd").asText();
                    String rljd = respJson.path("rljd").asText();
                    String rtzd = respJson.path("rtzd").asText();
                    String rgzzt = respJson.path("rgzzt").asText();

                    String status = decodeUnicode(rgzzt);

                    feedback = String.format("[电表信息]\n电表号：%s\n剩余免费电量：%s 度\n剩余收费电量：%s 度\n累计电量：%s 度\n透支电量：%s 度\n当前工作状态：%s",
                            rec, rsmd, rsfd, rljd, rtzd, status);
                    System.out.println("[INFO] 电表数据发送 => " + feedback.replace("\n", " | "));
                } else {
                    feedback = "[电表查询失败] 后台接口返回格式异常或无法解析。";
                    System.err.println("[INFO] 返回内容无法解析: " + respStr);
                }
            } catch (Exception ex) {
                feedback = "[电表查询失败] 网络异常或远端接口错误。";
                System.err.println("[INFO] 查询异常: " + ex.getMessage());
            }

            // ==== 修改点：调用 MessageSender 统一发送 ====
            MessageSender.sendGroupMessage(groupId, feedback);
        });
    }

    private static boolean isAllowedGroup(long groupId) {
        for (long g : ALLOWED_GROUPS)
            if (g == groupId) return true;
        return false;
    }

    private static boolean containsKeyword(String msg) {
        for (String kw : KEYWORDS)
            if (msg.equalsIgnoreCase(kw)) return true;
        return false;
    }

    private static String decodeUnicode(String unicodeStr) {
        StringBuilder out = new StringBuilder();
        int len = unicodeStr.length();
        for (int i = 0; i < len;) {
            char c = unicodeStr.charAt(i++);
            if (c == '\\' && i < len && unicodeStr.charAt(i) == 'u' && i + 4 < len) {
                String hex = unicodeStr.substring(i + 1, i + 5);
                try {
                    out.append((char) Integer.parseInt(hex, 16));
                } catch (Exception e) {
                    out.append("\\u").append(hex);
                }
                i += 5;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}