package top.yzljc.atricore.chat.official.card;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName Card
 * @Created_at 2026/09/28
 * @Project AtriMeow
 * @Package top.yzljc.atricore.chat.official.card
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

        @JsonValue
        private final String key;
    }

    public interface CardContent {
        Type getType();

        Object toObject();

        /** 旧版自定义卡片内容的序列化兼容入口 */
        @JsonValue
        default Object toPayload() {
            return toObject();
        }
    }

    public Type getType() {
        return content.getType();
    }

    /**
     * 返回可由 Jackson 直接序列化的卡片结构
     *
     * @return 卡片类型及内容
     */
    @JsonValue
    public Payload toPayload() {
        return new Payload(getType(), content);
    }

    /** QQ 卡片协议结构 */
    public record Payload(Type type, CardContent content) {}

    public Object toObject() {
        return Map.of(
                "type", getType().getKey(),
                "content", content.toObject()
        );
    }
}
