package top.yzljc.atribot.chat.official.ark;

import java.util.List;
import java.util.Map;

/**
 * 模板 37：大图，疑似报废没法用，发出来是滚木
 *
 * @param prompt   提示消息，对应 #PROMPT#
 * @param title    标题，对应 #METATITLE#
 * @param subTitle 子标题，对应 #METASUBTITLE#
 * @param picUrl   大图链接，对应 #METACOVER#，图片尺寸为 975×540
 * @param jumpUrl  跳转链接，对应 #METAURL#
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
