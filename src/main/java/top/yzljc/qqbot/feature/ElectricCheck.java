package top.yzljc.qqbot.feature;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import top.yzljc.qqbot.chat.GroupMessage;

import top.yzljc.qqbot.service.request.HttpRequest;
import top.yzljc.qqbot.service.thread.ThreadManager;
import top.yzljc.qqbot.config.groups.GroupConfigManager;
import top.yzljc.qqbot.event.EventHandler;
import top.yzljc.qqbot.event.Listener;
import top.yzljc.qqbot.event.impl.GroupMessageEvent;
import top.yzljc.qqbot.utils.Logger;

public class ElectricCheck implements Listener {
    private static final String[] KEYWORDS_ELECTRIC = {"电表", "dianbiao", "db"};
    private static final String QUERY_URL = "https://di.tjufe.edu.cn:8088/CardApp2021/ElecSearch.php?ec=903004&xq=1";

    @EventHandler
    public void onGroupMessage(GroupMessageEvent event) {
        long groupId = event.getGroupId();
        if (!GroupConfigManager.isFeatureEnabled(groupId, "electric_check")) {
            return;
        }
        for (String k : KEYWORDS_ELECTRIC) {
            if (event.getRawMessage().equalsIgnoreCase(k)) {
                ThreadManager.execute(() -> {
                    String feedback;
                    try {
                        String respJsonStr = HttpRequest.getRequestStr(QUERY_URL);
                        ObjectMapper mapper = new ObjectMapper();
                        JsonNode respJson = null;
                        try {
                            respJson = mapper.readTree(respJsonStr);
                        } catch (Exception e) {
                            Logger.warn("解析电表查询结果失败，返回内容：{}", respJsonStr);
                        }

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
                            Logger.info("电表数据发送 => {}", feedback.replace("\n", " | "));
                        } else {
                            feedback = "[电表查询失败] 后台接口返回格式异常或无法解析。";
                            Logger.warn("返回内容无法解析为JSON对象");
                        }
                    } catch (Exception ex) {
                        feedback = "[电表查询失败] 网络异常或远端接口错误。";
                        Logger.warn("查询异常：{}", ex.getMessage());
                    }

                    GroupMessage.chatMessage(groupId, feedback);
                });
                break;
            }
        }
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
