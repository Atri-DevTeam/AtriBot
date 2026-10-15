package top.yzljc.atribot.chat.kook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Objects;

/**
 * @Author YZ_Ljc_
 * @ClassName KookCard
 * @Created_at 2026/10/04
 * @Project AtriMeow
 * @Package top.yzljc.atribot.chat.kook
 */
public final class KookCard {
    static final ObjectMapper JSON = new ObjectMapper();
    private final ObjectNode data = JSON.createObjectNode().put("type", "card")
            .put("theme", KookTheme.PRIMARY.value()).put("size", "lg");
    private final ArrayNode modules = data.putArray("modules");

    public enum Size { SM, LG }

    public KookCard theme(KookTheme theme) {
        data.put("theme", Objects.requireNonNull(theme, "theme").value());
        return this;
    }

    public KookCard color(String color) {
        if (color == null || !color.matches("#[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("卡片颜色必须为 #RRGGBB 格式");
        }
        data.put("color", color);
        return this;
    }

    public KookCard size(Size size) {
        data.put("size", Objects.requireNonNull(size, "size") == Size.SM ? "sm" : "lg");
        return this;
    }

    public KookCard header(String title) {
        ObjectNode module = module("header");
        module.set("text", textNode("plain-text", title, 100));
        return add(module);
    }

    public KookCard text(String text) {
        ObjectNode module = module("section");
        module.set("text", textNode("plain-text", text, 2000));
        return add(module);
    }

    public KookCard markdown(String text) {
        ObjectNode module = module("section");
        module.set("text", textNode("kmarkdown", text, 5000));
        return add(module);
    }

    public KookCard section(String markdown, KookButton button) {
        ObjectNode module = module("section").put("mode", "right");
        module.set("text", textNode("kmarkdown", markdown, 5000));
        module.set("accessory", Objects.requireNonNull(button, "button").toJson());
        return add(module);
    }

    /**
     * 添加分栏内容，每项内容按 KMarkdown 渲染。
     *
     * @param columns 桌面端列数，范围为 1 至 3
     * @param fields 分栏内容，最多 50 项
     * @return 当前卡片
     */
    public KookCard fields(int columns, String... fields) {
        if (columns < 1 || columns > 3) throw new IllegalArgumentException("分栏列数必须为 1 至 3");
        checkCount(fields, 50, "分栏内容");
        ObjectNode paragraph = JSON.createObjectNode().put("type", "paragraph").put("cols", columns);
        ArrayNode values = paragraph.putArray("fields");
        for (String field : fields) values.add(textNode("kmarkdown", field, 5000));
        ObjectNode module = module("section");
        module.set("text", paragraph);
        return add(module);
    }

    public KookCard divider() {
        return add(module("divider"));
    }

    /**
     * 添加完整展示的图片。资源地址可使用当前机器人上传后返回的 URL。
     *
     * @param url 图片资源地址
     * @return 当前卡片
     */
    public KookCard image(String url) {
        return addImages("container", new String[]{url});
    }

    public KookCard images(String... urls) {
        return addImages("image-group", urls);
    }

    public KookCard buttons(KookButton... buttons) {
        checkCount(buttons, 4, "按钮");
        ObjectNode module = module("action-group");
        ArrayNode elements = module.putArray("elements");
        for (KookButton button : buttons) {
            elements.add(Objects.requireNonNull(button, "button").toJson());
        }
        return add(module);
    }

    public KookCard context(String... texts) {
        checkCount(texts, 10, "备注");
        ObjectNode module = module("context");
        ArrayNode elements = module.putArray("elements");
        for (String text : texts) elements.add(textNode("plain-text", text, 2000));
        return add(module);
    }

    public KookCard file(String url, String title) {
        return media("file", url, title);
    }

    public KookCard video(String url, String title) {
        return media("video", url, title);
    }

    public ObjectNode toJson() {
        return data.deepCopy();
    }

    /**
     * 将卡片组合为 KOOK 消息内容，并校验整条消息的卡片与模块数量。
     *
     * @param cards 1 至 5 张卡片，模块总数不超过 50
     * @return 可放入消息 {@code content} 字段的 JSON 字符串
     */
    public static String serialize(KookCard... cards) {
        checkCount(cards, 5, "卡片");
        ArrayNode result = JSON.createArrayNode();
        int totalModules = 0;
        for (KookCard card : cards) {
            Objects.requireNonNull(card, "card");
            if (card.modules.isEmpty()) throw new IllegalArgumentException("卡片至少需要一个模块");
            totalModules += card.modules.size();
            if (totalModules > 50) throw new IllegalArgumentException("一条卡片消息最多包含 50 个模块");
            result.add(card.toJson());
        }
        return result.toString();
    }

    static ObjectNode textNode(String type, String text, int maximum) {
        if (text == null || text.isBlank() || text.codePointCount(0, text.length()) > maximum) {
            throw new IllegalArgumentException("卡片文字不能为空，且不能超过 " + maximum + " 个字符");
        }
        return JSON.createObjectNode().put("type", type).put("content", text);
    }

    private KookCard addImages(String type, String[] urls) {
        checkCount(urls, 9, "图片");
        ObjectNode module = module(type);
        ArrayNode elements = module.putArray("elements");
        for (String url : urls) {
            if (url == null || url.isBlank()) throw new IllegalArgumentException("图片地址不能为空");
            elements.addObject().put("type", "image").put("src", url);
        }
        return add(module);
    }

    private KookCard media(String type, String url, String title) {
        if (url == null || url.isBlank()) throw new IllegalArgumentException("媒体地址不能为空");
        return add(module(type).put("src", url).put("title", Objects.requireNonNull(title, "title")));
    }

    private static ObjectNode module(String type) {
        return JSON.createObjectNode().put("type", type);
    }

    private KookCard add(ObjectNode module) {
        if (modules.size() >= 50) throw new IllegalArgumentException("一张卡片最多包含 50 个模块");
        modules.add(module);
        return this;
    }

    private static void checkCount(Object[] values, int maximum, String name) {
        if (values == null || values.length < 1 || values.length > maximum) {
            throw new IllegalArgumentException(name + "数量必须为 1 至 " + maximum);
        }
    }
}

