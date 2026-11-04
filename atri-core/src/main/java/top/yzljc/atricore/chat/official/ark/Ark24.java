package top.yzljc.atricore.chat.official.ark;

import java.util.List;
import java.util.Map;

/**
 * QQ Ark 24 模板的数据构建器
 *
 * @Author YZ_Ljc_
 * @ClassName Ark24
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.chat.official.ark
 */
public record Ark24(String description, String prompt, String title, String metaDescription,
                    String picUrl, String jumpUrl, String subTitle) implements Ark.ArkContent {

    @Override
    public Ark.Type getType() {
        return Ark.Type.Ark24;
    }

    @Override
    public List<ArkValue> values() {
        return List.of(
                new ArkValue("#DESC#", description),
                new ArkValue("#PROMPT#", prompt),
                new ArkValue("#TITLE#", title),
                new ArkValue("#METADESC#", metaDescription),
                new ArkValue("#IMG#", picUrl),
                new ArkValue("#LINK#", jumpUrl),
                new ArkValue("#SUBTITLE#", subTitle)
        );
    }

    @Override
    public List<Map<String, Object>> toObject() {
        return List.of(
                Map.of("key", "#DESC#", "value", description),
                Map.of("key", "#PROMPT#", "value", prompt),
                Map.of("key", "#TITLE#", "value", title),
                Map.of("key", "#METADESC#", "value", metaDescription),
                Map.of("key", "#IMG#", "value", picUrl),
                Map.of("key", "#LINK#", "value", jumpUrl),
                Map.of("key", "#SUBTITLE#", "value", subTitle)
        );
    }
}
