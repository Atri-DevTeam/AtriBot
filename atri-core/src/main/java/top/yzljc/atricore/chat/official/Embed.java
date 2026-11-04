package top.yzljc.atricore.chat.official;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName Embed
 * @Created_at 2026/09/21
 * @Project AtriMeow
 * @Package top.yzljc.atricore.chat.official
 * @Description 频道专享
 */
@Getter
@AllArgsConstructor
public class Embed {
    private String title;
    private String prompt;
    private String imageUrl;
    private List<String> fields;

    /**
     * 返回频道 Embed 的协议结构
     */
    @JsonValue
    public Payload toMessagePayload() {
        return new Payload(title, prompt, new Thumbnail(imageUrl), fields.stream().map(Field::new).toList());
    }

    /**
     * 频道 Embed 请求结构
     */
    public record Payload(String title, String prompt, Thumbnail thumbnail, List<Field> fields) {
    }

    /**
     * 缩略图地址
     */
    public record Thumbnail(String url) {
    }

    /**
     * 展示字段
     */
    public record Field(String name) {
    }

    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", title);
        payload.put("prompt", prompt);
        payload.put("thumbnail", Map.of("url", imageUrl));
        payload.put("fields", fields.stream().map(field -> Map.of("name", field)).toList());
        return payload;
    }
}