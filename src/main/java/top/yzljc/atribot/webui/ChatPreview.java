package top.yzljc.atribot.webui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Author YZ_Ljc_
 * @ClassName ChatPreview
 * @Created_at 2026/10/13
 * @Project AtriMeow
 * @Package top.yzljc.atribot.webui
 */
public final class ChatPreview {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern TAG = Pattern.compile("<(?:faceType=|attachmentType\\s*=|qqbot-cmd-input\\b)[^>]*>");

    private ChatPreview() {}

    public static String render(String content, String attachments, boolean ark) {
        if (ark) return "[Ark]";
        JsonNode card = json(content);
        if (card != null && "tuwen".equals(card.path("type").asText()) && card.path("content").isObject()) {
            var body = card.path("content");
            String title = body.path("title").asText("").trim();
            String description = body.path("description").asText("").trim();
            if (!title.isEmpty() || !description.isEmpty() || !body.path("pic_url").asText("").isBlank()) {
                return "[卡片] " + (!title.isEmpty() ? title : !description.isEmpty() ? description : "图文消息");
            }
        }
        JsonNode media = json(attachments);
        if (media != null && media.isArray()) {
            for (String type : new String[]{"image", "video", "voice", "file"}) {
                for (var item : media) if (type.equals(attachmentType(item))) return "[" + label(type) + "]";
            }
        }
        String text = content == null ? "" : content;
        if (text.stripLeading().startsWith("[media]")) return "[媒体消息]";
        text = TAG.matcher(text).replaceAll(match -> Matcher.quoteReplacement(tagText(match.group())));
        return text.replaceAll("<qqbot-at-user id=\"[A-F0-9]+\"\\s*/>|<@[A-F0-9]+>", "@…")
                .replaceAll("\\s+", " ").trim();
    }

    public static String shorten(String text) {
        if (text == null) return "";
        return text.codePointCount(0, text.length()) <= 120 ? text : text.substring(0, text.offsetByCodePoints(0, 120)) + "…";
    }

    private static String tagText(String raw) {
        String tag = raw.replace("\\\"", "\"");
        if (tag.startsWith("<qqbot-cmd-input")) {
            String show = attribute(tag, "show");
            if (show == null) return raw;
            if (!Pattern.compile("%[0-9a-fA-F]{2}").matcher(show + attribute(tag, "text")).find()) return show;
            try { return URLDecoder.decode(show, StandardCharsets.UTF_8); }
            catch (IllegalArgumentException e) { return show; }
        }
        boolean face = tag.startsWith("<faceType");
        String text = decodedText(attribute(tag, face ? "ext" : "description"));
        if (face) return text.isBlank() ? "[表情]" : text.startsWith("[") || text.startsWith("/") ? text : "[" + text + "]";
        String mime = attribute(tag, "attachmentType");
        String label = label(mime == null ? "file" : mime.split("/")[0]);
        if (text.startsWith("[") && text.endsWith("]")) text = text.substring(1, text.length() - 1).trim();
        return "[" + label + (text.isBlank() ? "" : ": " + text) + "]";
    }

    private static String attribute(String tag, String name) {
        var matcher = Pattern.compile("\\b" + name + "\\s*=\\s*([\"'])(.*?)\\1", Pattern.DOTALL).matcher(tag);
        return matcher.find() ? matcher.group(2) : null;
    }

    private static String decodedText(String encoded) {
        if (encoded == null) return "";
        try {
            var value = MAPPER.readTree(Base64.getDecoder().decode(encoded.replace('-', '+').replace('_', '/')));
            return (value.isTextual() ? value.asText() : value.path("text").asText("")).trim();
        } catch (Exception e) { return ""; }
    }

    private static JsonNode json(String raw) {
        if (raw == null || !(raw.stripLeading().startsWith("{") || raw.stripLeading().startsWith("["))) return null;
        try { return MAPPER.readTree(raw); }
        catch (Exception e) { return null; }
    }

    private static String label(String type) {
        return switch (type) {
            case "image" -> "图片";
            case "video" -> "视频";
            case "voice", "audio" -> "语音";
            default -> "文件";
        };
    }

    private static String attachmentType(JsonNode item) {
        String mime = item.path("content_type").asText("");
        boolean hasUrl = !item.path("url").asText("").isBlank();
        if (mime.startsWith("image/") && hasUrl) return "image";
        if (mime.startsWith("video/")) return "video";
        if (mime.equals("voice") || mime.startsWith("audio/")) return "voice";
        return hasUrl ? "file" : "";
    }
}
