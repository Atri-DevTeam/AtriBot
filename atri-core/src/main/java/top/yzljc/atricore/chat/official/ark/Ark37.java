package top.yzljc.atricore.chat.official.ark;

import java.util.List;
import java.util.Map;

/**
 * QQ Ark 37 模板的数据构建器
 *
 * @Author YZ_Ljc_
 * @ClassName Ark37
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.chat.official.ark
 */
public record Ark37(String prompt, String title, String subTitle, String picUrl, String jumpUrl)
        implements Ark.ArkContent {

    @Override
    public Ark.Type getType() {
        return Ark.Type.Ark37;
    }

    @Override
    public List<Map<String, Object>> toObject() {
        return List.of(
                Map.of("key", "#PROMPT#", "value", prompt),
                Map.of("key", "#METATITLE#", "value", title),
                Map.of("key", "#METASUBTITLE#", "value", subTitle),
                Map.of("key", "#METACOVER#", "value", picUrl),
                Map.of("key", "#METAURL#", "value", jumpUrl)
        );
    }
}
