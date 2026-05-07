package top.yzljc.qqbot.official.impl;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import top.yzljc.qqbot.event.EventHandler;
import top.yzljc.qqbot.event.EventManager;
import top.yzljc.qqbot.event.Listener;
import top.yzljc.qqbot.event.impl.OfficialGroupChatEvent;
import top.yzljc.qqbot.event.impl.OfficialPrivateChatEvent;
import top.yzljc.qqbot.official.service.QQBotMessageService;

/**
 * @Author YZ_Ljc_
 * @ClassName Test
 * @Created_at 2026/05/06
 * @Project AtriBot
 * @Package top.yzljc.qqbot.official.impl
 */
@Slf4j
@Component
public class TotalPlayers implements Listener {

    @Autowired
    private QQBotMessageService messageService;

    private final RestTemplate restTemplate = new RestTemplate();

    @PostConstruct
    public void init() {
        EventManager.getInstance().registerEvents(this);
    }

    @EventHandler
    public void onChat(OfficialGroupChatEvent event) {
        if (event.getContent().contains("/total")) {
            try {
                String url = "https://www.yzljc.top/data/api/v1/playerdata/total";
                JsonNode response = restTemplate.getForObject(url, JsonNode.class);

                int total = 0;
                if (response != null && response.has("total")) {
                    total = response.get("total").asInt();
                }

                String mdText = "# 📊 社区数据统计\n" +
                        "> 当前社区在档人数：**" + total + "** 人\n\n" +
                        "*(数据实时同步中...)*";

                // 4. 发送 Markdown 消息
                messageService.replyGroupMarkdownMessage(event.getGroupOpenId(), event.getMsgId(), mdText);

                log.info("已回复状态消息，当前人数: {}", total);

            } catch (Exception e) {
                log.error("请求 API 获取人数失败: ", e);
                messageService.replyGroupTextMessage(event.getGroupOpenId(), event.getMsgId(), "获取社区人数失败，请检查 API 状态。");
            }
        }
    }

    @EventHandler
    public void onPrivateChat(OfficialPrivateChatEvent event) {
        if (event.getContent().contains("/total")) {
            try {
                String url = "https://www.yzljc.top/data/api/v1/playerdata/total";
                JsonNode response = restTemplate.getForObject(url, JsonNode.class);

                int total = 0;
                if (response != null && response.has("total")) {
                    total = response.get("total").asInt();
                }

                String mdText = "# 📊 社区数据统计\n" +
                        "> 当前社区在档人数：**" + total + "** 人\n\n" +
                        "*(数据实时同步中...)*";

                // 4. 发送 Markdown 消息
                messageService.replyPrivateMarkdownMessage(event.getOpenId(), event.getMsgId(), mdText);

                log.info("已回复状态消息，当前人数: {}", total);

            } catch (Exception e) {
                log.error("请求 API 获取人数失败: ", e);
                messageService.replyPrivateTextMessage(event.getOpenId(), event.getMsgId(), "获取社区人数失败，请检查 API 状态。");
            }
        }
    }
}