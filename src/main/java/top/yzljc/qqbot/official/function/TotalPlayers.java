package top.yzljc.qqbot.official.function;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.qqbot.command.Command;
import top.yzljc.qqbot.command.CommandExecutor;
import top.yzljc.qqbot.command.CommandSender;
import top.yzljc.qqbot.service.request.HttpService;

@Slf4j
public class TotalPlayers implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (label) {
            case "0" -> sender.reply(getTotalPlayers(label));
            case "1" -> sender.officialPrivateReplyMarkdown(getTotalPlayers(label));
            case "2" -> sender.officialGroupReplyMarkdown(getTotalPlayers(label));
        }
        return true;
    }

    private static String getTotalPlayers(String label) {
        try {
            String url = "https://www.yzljc.top/data/api/v1/playerdata/total";
            JsonNode response = HttpService.sendGetRequest(url);

            int total = 0;
            if (response != null && response.has("total")) {
                total = response.get("total").asInt();
            }

            if (label.equals("0")) {
                return "当前社区在档人数: " + total + " 人";
            } else {
                return "# 📊 社区数据统计\n" +
                        "> 当前社区在档人数：**" + total + "** 人\n\n" +
                        "*(数据实时同步中...)*";
            }
        } catch (Exception e) {
            log.error("请求 API 获取人数失败: ", e);
            return "获取社区人数失败，请检查 API 状态";
        }
    }
}