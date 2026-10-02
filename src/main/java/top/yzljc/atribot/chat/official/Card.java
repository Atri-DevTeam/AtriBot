package top.yzljc.atribot.chat.official;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName Card
 * @Created_at 2026/09/28
 * @Project AtriMeow
 * @Package top.yzljc.atribot.chat.official
 */
@Getter
@AllArgsConstructor
public class Card {

    private final CardContent content;

    public static Card tuWen(String title, String description, String picUrl, String jumpUrl) {
        return new Card(new TuWen(title, description, picUrl, jumpUrl));
    }

    @AllArgsConstructor
    @Getter
    public enum Type {
        TuWen("tuwen");

        private final String key;
    }

    public interface CardContent {
        Type getType();

        Object toObject();
    }

    public Type getType() {
        return content.getType();
    }

    public Object toObject() {
        return Map.of(
                "type", getType().getKey(),
                "content", content.toObject()
        );
    }
}
