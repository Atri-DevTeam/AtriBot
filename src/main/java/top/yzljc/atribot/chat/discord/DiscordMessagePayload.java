package top.yzljc.atribot.chat.discord;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import top.yzljc.atribot.service.request.HttpService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DiscordMessagePayload {
    public static final int FLAG_EPHEMERAL = 1 << 6;
    public static final int FLAG_SUPPRESS_EMBEDS = 1 << 2;
    public static final int FLAG_SUPPRESS_NOTIFICATIONS = 1 << 12;
    public static final int FLAG_COMPONENTS_V2 = 1 << 15;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ObjectNode data = MAPPER.createObjectNode();
    private final List<FileUpload> files = new ArrayList<>();
    private int flags;

    public static DiscordMessagePayload text(String content) {
        return new DiscordMessagePayload().content(content);
    }

    public static DiscordMessagePayload embed(DiscordEmbed embed) {
        return new DiscordMessagePayload().addEmbed(embed);
    }

    public DiscordMessagePayload content(String content) {
        data.put("content", content == null ? "" : content);
        return this;
    }

    public DiscordMessagePayload ephemeral() {
        return ephemeral(true);
    }

    public DiscordMessagePayload ephemeral(boolean value) {
        if (value) {
            addFlag(FLAG_EPHEMERAL);
        } else {
            removeFlag(FLAG_EPHEMERAL);
        }
        return this;
    }

    public DiscordMessagePayload suppressEmbeds() {
        addFlag(FLAG_SUPPRESS_EMBEDS);
        return this;
    }

    public DiscordMessagePayload suppressNotifications() {
        addFlag(FLAG_SUPPRESS_NOTIFICATIONS);
        return this;
    }

    public DiscordMessagePayload componentsV2() {
        addFlag(FLAG_COMPONENTS_V2);
        return this;
    }

    public DiscordMessagePayload flags(int flags) {
        this.flags = flags;
        writeFlags();
        return this;
    }

    public DiscordMessagePayload addFlag(int flag) {
        this.flags |= flag;
        writeFlags();
        return this;
    }

    public DiscordMessagePayload removeFlag(int flag) {
        this.flags &= ~flag;
        writeFlags();
        return this;
    }

    public DiscordMessagePayload addEmbed(DiscordEmbed embed) {
        if (embed != null) {
            data.withArray("embeds").add(embed.toJson());
        }
        return this;
    }

    public DiscordMessagePayload addEmbed(JsonNode embed) {
        if (embed != null && !embed.isMissingNode() && !embed.isNull()) {
            data.withArray("embeds").add(embed);
        }
        return this;
    }

    public DiscordMessagePayload addEmbed(Object embed) {
        if (embed instanceof DiscordEmbed discordEmbed) {
            return addEmbed(discordEmbed);
        }
        return addEmbed(MAPPER.valueToTree(embed));
    }

    public DiscordMessagePayload embeds(List<?> embeds) {
        ArrayNode array = data.putArray("embeds");
        if (embeds != null) {
            for (Object embed : embeds) {
                if (embed instanceof DiscordEmbed discordEmbed) {
                    array.add(discordEmbed.toJson());
                } else {
                    array.add(MAPPER.valueToTree(embed));
                }
            }
        }
        return this;
    }

    public DiscordMessagePayload components(Object components) {
        JsonNode node = MAPPER.valueToTree(components);
        if (node.isArray()) {
            data.set("components", node);
        } else {
            ArrayNode array = data.putArray("components");
            array.add(node);
        }
        return this;
    }

    public DiscordMessagePayload addComponent(Object component) {
        data.withArray("components").add(MAPPER.valueToTree(component));
        return this;
    }

    public DiscordMessagePayload file(Path path) {
        return file(path, null);
    }

    public DiscordMessagePayload file(Path path, String description) {
        if (path != null) {
            files.add(new FileUpload(path, description));
        }
        return this;
    }

    public ObjectNode getData() {
        return data;
    }

    public boolean hasFiles() {
        return !files.isEmpty();
    }

    public String toInteractionResponseJson() {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("type", 4);
        root.set("data", payloadData());
        return write(root);
    }

    public String toWebhookJson() {
        return write(payloadData());
    }

    public Map<String, String> toInteractionMultipartFields() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("payload_json", toInteractionResponseJson());
        return fields;
    }

    public Map<String, String> toWebhookMultipartFields() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("payload_json", toWebhookJson());
        return fields;
    }

    public List<HttpService.MultipartFile> toMultipartFiles() {
        List<HttpService.MultipartFile> parts = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            FileUpload upload = files.get(i);
            Path path = upload.path();
            try {
                String filename = path.getFileName() == null ? "file" + i : path.getFileName().toString();
                String contentType = Files.probeContentType(path);
                if (contentType == null || contentType.isBlank()) {
                    contentType = "application/octet-stream";
                }
                parts.add(new HttpService.MultipartFile("files[" + i + "]", filename, contentType, Files.readAllBytes(path)));
            } catch (Exception e) {
                throw new IllegalStateException("读取 Discord 附件失败: " + path, e);
            }
        }
        return parts;
    }

    private ObjectNode payloadData() {
        ObjectNode copy = data.deepCopy();
        if (!files.isEmpty()) {
            ArrayNode attachments = copy.putArray("attachments");
            for (int i = 0; i < files.size(); i++) {
                FileUpload upload = files.get(i);
                ObjectNode attachment = attachments.addObject();
                attachment.put("id", i);
                Path fileName = upload.path().getFileName();
                attachment.put("filename", fileName == null ? "file" + i : fileName.toString());
                if (upload.description() != null && !upload.description().isBlank()) {
                    attachment.put("description", upload.description());
                }
            }
        }
        return copy;
    }

    private void writeFlags() {
        if (flags == 0) {
            data.remove("flags");
        } else {
            data.put("flags", flags);
        }
    }

    private String write(JsonNode node) {
        try {
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalStateException("Discord payload JSON 序列化失败", e);
        }
    }

    private record FileUpload(Path path, String description) {
    }
}
