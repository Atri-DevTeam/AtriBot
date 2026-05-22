package top.yzljc.qqbot.feature;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.qqbot.AtriBot;

import top.yzljc.qqbot.command.Command;
import top.yzljc.qqbot.command.CommandExecutor;
import top.yzljc.qqbot.command.CommandSender;
import top.yzljc.qqbot.event.impl.OfficialGroupMessageCreateEvent;
import top.yzljc.qqbot.official.permission.GroupList;
import top.yzljc.qqbot.official.service.CommandButton;
import top.yzljc.qqbot.service.request.HttpService;
import top.yzljc.qqbot.service.thread.ThreadManager;
import top.yzljc.qqbot.event.EventHandler;
import top.yzljc.qqbot.event.Listener;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ElectricCheck implements Listener, CommandExecutor {
    private static final String[] KEYWORDS_ELECTRIC = {"电表", "dianbiao", "db"};
    private static final String QUERY_URL = "https://di.tjufe.edu.cn:8088/CardApp2021/ElecSearch.php?ec=903004&xq=1";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.replyMarkdown(label, processCheck(), getKeys());
        return true;
    }

    @EventHandler
    public void onGroupMessage(OfficialGroupMessageCreateEvent event) {
        if (!GroupList.isWhitelist(event.getGroupOpenId())) return;
        for (String k : KEYWORDS_ELECTRIC) {
            if (event.getContent().equalsIgnoreCase(k)) {
                ThreadManager.execute(() -> {
                    event.replyMarkdown(processCheck(), getKeys());
                });
                break;
            }
        }
    }

    private static String processCheck() {
        String feedback;
        try {
            String respJsonStr = HttpService.getRequestStr(QUERY_URL);
            ObjectMapper mapper = new ObjectMapper();
            JsonNode respJson = null;
            try {
                respJson = mapper.readTree(respJsonStr);
            } catch (Exception e) {
                log.warn("解析电表查询结果失败，返回内容：{}", respJsonStr);
            }

            if (respJson != null) {
                String rec = respJson.path("rec").asText();
                String rsmd = respJson.path("rsmd").asText();
                String rsfd = respJson.path("rsfd").asText();
                String rljd = respJson.path("rljd").asText();
                String rtzd = respJson.path("rtzd").asText();
                String rgzzt = respJson.path("rgzzt").asText();

                String status = decodeUnicode(rgzzt);

                feedback = "![tufe #35px #34px](https://www.yzljc.top/img/tufe-logo.png) **天津财经大学电表信息**\n\n" +
                        "电表号：`" + rec + "`\n" +
                        "剩余免费电量：`" + rsmd + "` 度\n" +
                        "剩余收费电量：`" + rsfd + "` 度\n" +
                        "累计电量：`" + rljd + "` 度\n" +
                        "透支电量：`" + rtzd + "` 度\n" +
                        "当前工作状态：`" + status + "`";


                log.info("电表数据发送 => {}", feedback.replace("\n", " | "));
            } else {
                feedback = "后台接口返回格式异常或无法解析。";
                log.warn("返回内容无法解析为JSON对象");
            }
        } catch (Exception ex) {
            feedback = "网络异常或远端接口错误。";
            log.warn("查询异常：{}", ex.getMessage());
        }
        return feedback;
    }

    private static Object getKeys() {
        List<List<CommandButton>> layout = new ArrayList<>();
        layout.add(List.of(
                new CommandButton("c1", "再次查询", "/tufe-electric-check-903004", false, 1, 2)
        ));
        return AtriBot.getInstance().getMessageService().buildCmdKeyboard(layout);
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
