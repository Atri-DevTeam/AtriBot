package top.yzljc.atribot.chat.discord;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class DiscordEmbed {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ObjectNode data = MAPPER.createObjectNode();

    public DiscordEmbed title(String title) {
        data.put("title", title);
        return this;
    }

    public DiscordEmbed description(String description) {
        data.put("description", description);
        return this;
    }

    public DiscordEmbed url(String url) {
        data.put("url", url);
        return this;
    }

    public DiscordEmbed timestamp(String timestamp) {
        data.put("timestamp", timestamp);
        return this;
    }

    public DiscordEmbed color(int color) {
        data.put("color", color);
        return this;
    }

    public DiscordEmbed footer(String text) {
        ObjectNode footer = data.putObject("footer");
        footer.put("text", text);
        return this;
    }

    public DiscordEmbed footer(String text, String iconUrl) {
        ObjectNode footer = data.putObject("footer");
        footer.put("text", text);
        footer.put("icon_url", iconUrl);
        return this;
    }

    public DiscordEmbed image(String url) {
        data.putObject("image").put("url", url);
        return this;
    }

    public DiscordEmbed thumbnail(String url) {
        data.putObject("thumbnail").put("url", url);
        return this;
    }

    public DiscordEmbed author(String name) {
        data.putObject("author").put("name", name);
        return this;
    }

    public DiscordEmbed author(String name, String url, String iconUrl) {
        ObjectNode author = data.putObject("author");
        author.put("name", name);
        author.put("url", url);
        author.put("icon_url", iconUrl);
        return this;
    }

    public DiscordEmbed field(String name, String value) {
        return field(name, value, false);
    }

    public DiscordEmbed field(String name, String value, boolean inline) {
        ArrayNode fields = data.withArray("fields");
        ObjectNode field = fields.addObject();
        field.put("name", name);
        field.put("value", value);
        field.put("inline", inline);
        return this;
    }

    public JsonNode toJson() {
        return data.deepCopy();
    }
}
