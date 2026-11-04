package top.yzljc.atricore.chat.official.ark;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName Ark
 * @Created_at 2026/09/29
 * @Project AtriBot
 * @Package top.yzljc.atricore.chat.official.ark
 * @Description 公域机器人无被动 Ark 消息权限，仅能主动调用
 */
@Getter
@AllArgsConstructor
public class Ark {

    private final ArkContent content;

    public static Ark ark23(String description, String prompt, List<Ark23.Item> items) {
        return new Ark(new Ark23(description, prompt, items));
    }

    public static Ark ark24(String description, String prompt, String title, String metaDescription,
                            String picUrl, String jumpUrl, String subTitle) {
        return new Ark(new Ark24(description, prompt, title, metaDescription, picUrl, jumpUrl, subTitle));
    }

    public static Ark ark37(String prompt, String title, String subTitle, String picUrl, String jumpUrl) {
        return new Ark(new Ark37(prompt, title, subTitle, picUrl, jumpUrl));
    }

    @AllArgsConstructor
    @Getter
    public enum Type {
        Ark23(23),
        Ark24(24),
        Ark37(37);

        private final int templateId;
    }

    public interface ArkContent {
        Type getType();

        List<Map<String, Object>> toObject();

        /** 旧版自定义模板的兼容入口，内置模板直接返回协议对象 */
        default List<ArkValue> values() {
            return LegacyMapper.INSTANCE.convertValue(toObject(), new TypeReference<List<ArkValue>>() {});
        }
    }

    public Type getType() {
        return content.getType();
    }

    /**
     * 返回 QQ Ark 协议结构
     *
     * @return 模板编号及模板字段
     */
    @JsonValue
    public Payload toPayload() {
        return new Payload(getType().getTemplateId(), content.values());
    }

    /** Ark 模板请求结构 */
    public record Payload(@JsonProperty("template_id") int templateId, List<ArkValue> kv) {}

    private static final class LegacyMapper {
        private static final ObjectMapper INSTANCE = new ObjectMapper();
    }

    public Map<String, Object> toObject() {
        return Map.of(
                "template_id", getType().getTemplateId(),
                "kv", content.toObject()
        );
    }
}
