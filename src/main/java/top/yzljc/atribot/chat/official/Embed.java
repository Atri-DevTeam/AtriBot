package top.yzljc.atribot.chat.official;

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
 * @Package top.yzljc.atribot.chat.official
 * @Description 频道专享
 */
@Getter
@AllArgsConstructor
public class Embed {
    private String title;
    private String prompt;
    private String imageUrl;
    private List<String> fields;

    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", title);
        payload.put("prompt", prompt);
        payload.put("thumbnail", Map.of("url", imageUrl));
        payload.put("fields", fields.stream().map(field -> Map.of("name", field)).toList());
        return payload;
    }
}