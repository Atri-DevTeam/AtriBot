package top.yzljc.atribot.function.impl;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.service.request.HttpService;

/**
 * @Author YZ_Ljc_
 * @ClassName FetchHitokoto
 * @Created_at 2026/06/02
 * @Project AtriBot
 * @Package top.yzljc.atribot.utils
 */
@Slf4j
public class FetchHitokoto {
    public static String get() {
        try {
            JsonNode respJson = HttpService.sendGetRequest("https://v1.hitokoto.cn/");

            if (respJson == null) {
                log.warn("一言获取异常: 接口未返回数据");
                return "一言获取失败：接口异常。";
            }

            String hitokoto = respJson.path("hitokoto").asText();
            String from = respJson.path("from").asText();
            String fromWho = respJson.path("from_who").asText();

            String authorOrigin = (fromWho.equals("null") || fromWho.isEmpty() || fromWho.equals(from)) ? from : from + " · " + fromWho;
            log.info("调用一言API: {} —— {}", hitokoto, authorOrigin);

            return "> " + hitokoto + "\n" +
                    ">     —— " + authorOrigin;

        } catch (Exception ex) {
            log.warn("一言获取异常: {}", ex.getMessage());
            return "一言获取失败：接口异常";
        }
    }
}