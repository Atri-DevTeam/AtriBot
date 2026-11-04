package top.yzljc.atribot.function.command.component;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.atribot.function.impl.ImageDTO;

import java.util.UUID;

/**
 * @param success 是否成功
 * @param message ugc 端生生成的响应文本
 * @param d       ugc 端生生成的响应数据
 * @param i       图片数据
 * @Author YZ_Ljc_
 */
public record Result(boolean success, String message, JsonNode d, ImageDTO i) {

    /**
     * 读取远端确认的玩家 UUID，缺失或格式无效时返回 null
     * 图片编号 uuid 不参与玩家身份解析
     */
    public UUID playerUuid() {
        if (d == null) return null;
        String value = d.path("player_uuid").asText("");
        if (!value.matches("(?i)[0-9a-f]{32}|[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")) return null;
        if (value.length() == 32) {
            value = value.substring(0, 8) + "-" + value.substring(8, 12) + "-" + value.substring(12, 16)
                    + "-" + value.substring(16, 20) + "-" + value.substring(20);
        }
        return UUID.fromString(value);
    }
}