package top.yzljc.atribot.function.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import top.yzljc.atribot.chat.official.button.ButtonSize;
import top.yzljc.atribot.chat.official.button.ButtonStyle;
import top.yzljc.atribot.chat.official.button.ButtonType;
import top.yzljc.atribot.chat.official.button.PermissionType;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;

/**
 * @Author YZ_Ljc_
 * @ClassName JoinWelcomeConfig
 * @Created_at 2026/09/07
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.tasks
 */
public final class JoinWelcomeDAO {
    private JoinWelcomeDAO() {}

    public static ObjectNode validate(JsonNode source) {
        require(source != null && source.isObject(), "配置必须是 JSON 对象");
        ObjectNode config = (ObjectNode) source.deepCopy();
        String text = string(config, "text", "", 16000);
        enumValue(config, "button_size", ButtonSize.class, ButtonSize.UNDEFINED);
        JsonNode keyboard = config.path("keyboard");
        require(keyboard.isMissingNode() || keyboard.isArray(), "keyboard 必须是按钮二维数组");
        require(keyboard.size() <= 5, "编辑器最多支持 5 行按钮");
        Set<String> ids = new HashSet<>();
        int count = 0;
        for (JsonNode row : keyboard) {
            require(row.isArray() && !row.isEmpty() && row.size() <= 10, "每行必须包含 1 至 10 个按钮");
            for (JsonNode button : row) {
                require(button.isObject(), "按钮配置必须是对象");
                String id = string(button, "button_id", "", 64);
                require(!id.isBlank() && ids.add(id), "按钮 ID 不能为空或重复");
                require(!string(button, "display_text", "", 128).isBlank(), "按钮文字不能为空");
                string(button, "visited_display_text", "", 128);
                string(button, "button_group_id", "", 64);
                String data = string(button, "data", "", 4096);
                require(!data.isBlank(), "按钮动作内容不能为空");
                ButtonType type = enumValue(button, "type", ButtonType.class, ButtonType.COMMAND);
                enumValue(button, "style", ButtonStyle.class, ButtonStyle.BLUE);
                PermissionType permission = enumValue(button, "permission", PermissionType.class, PermissionType.ALL);
                if (type == ButtonType.LINK) {
                    require(isHttpUrl(data), "链接按钮必须填写有效的 HTTP 或 HTTPS 地址");
                }
                for (String flag : new String[]{"enter", "reply"}) {
                    require(!button.has(flag) || button.path(flag).isBoolean(), flag + " 必须是布尔值");
                }
                JsonNode allowed = button.path("allowed_open_ids");
                require(allowed.isMissingNode() || allowed.isArray(), "允许用户必须是 OpenID 数组");
                require(allowed.size() <= 100, "允许用户最多 100 个");
                for (JsonNode user : allowed) {
                    require(user.isTextual() && !user.asText().isBlank() && user.asText().length() <= 256,
                            "允许用户的 OpenID 不能为空且不能超过 256 字符");
                }
                require(permission != PermissionType.SPECIFIC_USER || !allowed.isEmpty(), "指定用户权限至少需要一个 OpenID");
                JsonNode modal = button.path("modal");
                if (!modal.isMissingNode()) {
                    require(modal.isObject(), "确认弹窗配置必须是对象");
                    require(!string(modal, "content", "", 1024).isBlank(), "确认弹窗内容不能为空");
                    String confirm = string(modal, "confirm_text", "", 4);
                    String cancel = string(modal, "cancel_text", "", 4);
                    require(confirm.isBlank() == cancel.isBlank(), "确认和取消文字必须同时填写或同时留空");
                }
                count++;
            }
        }
        require(!text.isBlank() || count > 0, "请填写 Markdown 正文或添加按钮；恢复默认请使用恢复默认操作");
        return config;
    }

    private static String string(JsonNode node, String key, String fallback, int max) {
        JsonNode value = node.path(key);
        if (value.isMissingNode()) return fallback;
        require(value.isTextual(), key + " 必须是文本");
        require(value.asText().length() <= max, key + " 不能超过 " + max + " 字符");
        return value.asText();
    }

    private static <E extends Enum<E>> E enumValue(JsonNode node, String key, Class<E> type, E fallback) {
        String value = string(node, key, fallback.name(), 64);
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(key + " 的选项无效: " + value);
        }
    }

    private static boolean isHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
