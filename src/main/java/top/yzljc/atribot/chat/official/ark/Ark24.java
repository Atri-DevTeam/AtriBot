package top.yzljc.atribot.chat.official.ark;

import java.util.List;
import java.util.Map;

/**
 * 模板 24：文本和缩略图，不支持PC端
 *
 * @param description     描述，对应 #DESC#
 * @param prompt          提示文本，对应 #PROMPT#
 * @param title           标题，对应 #TITLE#
 * @param metaDescription 详情描述，对应 #METADESC#
 * @param picUrl          图片链接，对应 #IMG#
 * @param jumpUrl         跳转链接，对应 #LINK#
 * @param subTitle        来源，对应 #SUBTITLE#
 */
public record Ark24(String description, String prompt, String title, String metaDescription,
                    String picUrl, String jumpUrl, String subTitle) implements Ark.ArkContent {

    @Override
    public Ark.Type getType() {
        return Ark.Type.Ark24;
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
