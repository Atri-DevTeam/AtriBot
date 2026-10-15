package top.yzljc.atribot.chat.discord;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class DiscordComponents {
    public static final int STYLE_PRIMARY = 1;
    public static final int STYLE_SECONDARY = 2;
    public static final int STYLE_SUCCESS = 3;
    public static final int STYLE_DANGER = 4;
    public static final int STYLE_LINK = 5;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DiscordComponents() {
    }

    public static JsonNode actionRow(Object... components) {
        ObjectNode row = MAPPER.createObjectNode();
        row.put("type", 1);
        ArrayNode array = row.putArray("components");
        if (components != null) {
            for (Object component : components) {
                array.add(MAPPER.valueToTree(component));
            }
        }
        return row;
    }

    public static JsonNode primaryButton(String customId, String label) {
        return button(STYLE_PRIMARY, customId, label);
    }

    public static JsonNode secondaryButton(String customId, String label) {
        return button(STYLE_SECONDARY, customId, label);
    }

    public static JsonNode successButton(String customId, String label) {
        return button(STYLE_SUCCESS, customId, label);
    }

    public static JsonNode dangerButton(String customId, String label) {
        return button(STYLE_DANGER, customId, label);
    }

    public static JsonNode linkButton(String url, String label) {
        ObjectNode button = baseButton(STYLE_LINK, label);
        button.put("url", url);
        return button;
    }

    public static JsonNode button(int style, String customId, String label) {
        ObjectNode button = baseButton(style, label);
        button.put("custom_id", customId);
        return button;
    }

    public static JsonNode disabled(JsonNode component) {
        ObjectNode copy = component == null || !component.isObject()
                ? MAPPER.createObjectNode()
                : (ObjectNode) component.deepCopy();
        copy.put("disabled", true);
        return copy;
    }

    private static ObjectNode baseButton(int style, String label) {
        ObjectNode button = MAPPER.createObjectNode();
        button.put("type", 2);
        button.put("style", style);
        button.put("label", label);
        return button;
    }
}
