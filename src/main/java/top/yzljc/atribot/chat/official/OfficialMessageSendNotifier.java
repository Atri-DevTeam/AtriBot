package top.yzljc.atribot.chat.official;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import top.yzljc.atribot.event.EventManager;
import top.yzljc.atribot.event.events.OfficialMessageSendEvent;
import top.yzljc.atribot.platform.Platform;

import java.net.URI;
import java.util.regex.Pattern;

/**
 * @Author YZ_Ljc_
 * @ClassName OfficialMessageSendNotifier
 * @Created_at 2026/09/23
 * @Project AtriMeow
 * @Package top.yzljc.atribot.chat.official
 * @Description 官机发送前的同步审核事件分发，过滤纯上传、输入状态和其他非消息请求
 */
public final class OfficialMessageSendNotifier {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern MESSAGE_PATH = Pattern.compile(
            "/(?:(v2/groups|v2/users)|(channels|dms))/([^/]+)/(messages|stream_messages|files)/?$");

    private OfficialMessageSendNotifier() {}

    /**
     * @param method 待发送的请求方法，仅 POST 消息请求触发事件
     * @param url 官机 API 请求的完整 URL，允许带查询参数和代理路径前缀
     * @param requestBody 待发送的 JSON 快照；纯上传及输入状态通知不会触发事件
     * @return 监听器未取消或请求不属于消息发送时为 true，取消发送时为 false
     */
    public static boolean allowSend(String method, String url, String requestBody) {
        if (!"POST".equalsIgnoreCase(method) || url == null) return true;
        String path;
        try {
            path = URI.create(url).normalize().getPath();
        } catch (IllegalArgumentException e) {
            return true;
        }
        if (path == null) return true;
        var matcher = MESSAGE_PATH.matcher(path);
        if (!matcher.find()) return true;

        String scope = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        String endpoint = matcher.group(4);
        boolean stream = "stream_messages".equals(endpoint);
        boolean file = "files".equals(endpoint);
        if (stream && !"v2/users".equals(scope)) return true;
        if (file && matcher.group(1) == null) return true;

        JsonNode request = readJson(requestBody);
        if (file && !request.path("srv_send_msg").asBoolean(false)) return true;
        if ("v2/users".equals(scope) && "messages".equals(endpoint) && request.size() == 2
                && request.path("msg_type").asInt(-1) == 6 && request.hasNonNull("input_notify")) return true;

        Platform platform = switch (scope) {
            case "v2/groups" -> Platform.OFFICIAL_GROUP;
            case "v2/users" -> Platform.OFFICIAL_C2C;
            case "channels" -> Platform.OFFICIAL_GUILD_CHANNEL;
            case "dms" -> Platform.OFFICIAL_GUILD_DM;
            default -> throw new IllegalStateException("未知官机消息目标: " + scope);
        };
        OfficialMessageSendEvent event = new OfficialMessageSendEvent(platform, matcher.group(3), url,
                requestBody, stream);
        EventManager.getInstance().callEvent(event);
        return !event.isCancelled();
    }

    private static JsonNode readJson(String body) {
        if (body == null || body.isBlank()) return NullNode.getInstance();
        try {
            JsonNode node = MAPPER.readTree(body);
            return node == null ? NullNode.getInstance() : node;
        } catch (JsonProcessingException e) {
            return NullNode.getInstance();
        }
    }
}
