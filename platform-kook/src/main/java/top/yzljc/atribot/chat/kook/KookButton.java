package top.yzljc.atribot.chat.kook;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.util.Objects;

/**
 * @Author YZ_Ljc_
 * @ClassName KookButton
 * @Created_at 2026/10/04
 * @Project AtriMeow
 * @Package top.yzljc.atribot.chat.kook
 */
public final class KookButton {
    private final ObjectNode data;

    private KookButton(String label, String value, String click) {
        data = KookCard.JSON.createObjectNode().put("type", "button")
                .put("theme", KookTheme.PRIMARY.value())
                .put("value", Objects.requireNonNull(value, "value")).put("click", click);
        data.set("text", KookCard.textNode("plain-text", label, 2000));
    }

    public static KookButton link(String label, String url) {
        URI uri = URI.create(Objects.requireNonNull(url, "url"));
        if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null) {
            throw new IllegalArgumentException("按钮链接必须为 HTTP 或 HTTPS 地址");
        }
        return new KookButton(label, url, "link");
    }

    /**
     * 创建回调按钮，点击后触发 {@code KookButtonClickEvent}。
     *
     * @param label 按钮文字
     * @param value 原样返回给事件监听器的值，不会自动执行为指令
     * @return 回调按钮
     */
    public static KookButton callback(String label, String value) {
        return new KookButton(label, value, "return-val");
    }

    public KookButton theme(KookTheme theme) {
        data.put("theme", Objects.requireNonNull(theme, "theme").value());
        return this;
    }

    ObjectNode toJson() {
        return data.deepCopy();
    }
}

