package top.yzljc.atricore.chat.official;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.With;
import top.yzljc.atricore.chat.official.button.Keyboard;
import top.yzljc.atricore.chat.official.media.HexColor;

/**
 * @Author YZ_Ljc_
 * @ClassName Markdown
 * @Created_at 2026/06/02
 * @Project AtriBot
 * @Package top.yzljc.atricore.chat.official
 */
@Getter
@With
@RequiredArgsConstructor
@AllArgsConstructor
public class Markdown {
    @JsonProperty("content")
    private final String text;

    /**
     * 为 true 时，图片转存失败会导致整条消息发送失败；未设置时使用平台默认值
     */
    @JsonProperty("force_verify_image_resource")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final Boolean forceVerifyImageResource;

    @JsonIgnore
    private Keyboard keyboard;

    public Markdown(String text) {
        this(text, null);
    }

    public static String img(String url, int width, int height) {
        return String.format("![atribot_pic #%dpx #%dpx](%s)", width, height, url);
    }

    public static String img(String alt, String url, int width, int height) {
        return String.format("![%s #%dpx #%dpx](%s)", alt, width, height, url);
    }

    /**
     * 引用当前机器人已上传的 Markdown 图片素材
     *
     * @param reference 素材接口返回的完整 qqbot-media 引用，或 image/v1/ 后的 token
     * @param width 显示宽度，单位为像素
     * @param height 显示高度，单位为像素
     * @return 包含素材引用的 Markdown 图片语法
     */
    public static String materialImage(String reference, int width, int height) {
        if (reference == null || reference.isBlank()) throw new IllegalArgumentException("素材引用不能为空");
        String prefix = "qqbot-media://image/v1/";
        String token = reference.startsWith(prefix) ? reference.substring(prefix.length()) : reference;
        if (!token.matches("[A-Za-z0-9._~%+=/-]+")) throw new IllegalArgumentException("素材引用无效");
        if (width < 1 || height < 1) throw new IllegalArgumentException("图片宽高必须大于 0");
        return img(prefix + token, width, height);
    }

    public static String enterCommand(String command) {
        command = sanitizeCommandAttribute(command);
        if (isOutOfLimit(command)) {
            throw new IllegalArgumentException("enterCommand 的 command 参数长度不能超过 100 个字符");
        }
        return "<qqbot-cmd-enter text=\"" + command + "\" />";
    }

    public static String enterCommand(String command, String display) {
        return enterCommand(command, display, false);
    }

    public static String enterCommand(String command, String display, boolean reference) {
        command = sanitizeCommandAttribute(command);
        display = sanitizeCommandAttribute(display);
        if (isOutOfLimit(display)) {
            throw new IllegalArgumentException("enterCommand 的 display 参数长度不能超过 100 个字符");
        }
        if (isOutOfLimit(command)) {
            throw new IllegalArgumentException("enterCommand 的 command 参数长度不能超过 100 个字符");
        }
        return "<qqbot-cmd-input text=\"" + command + "\" show=\"" + display + "\" reference=\"" + reference + "\" />";
    }

    /**
     * 清除交互标签属性中的引号、标签分隔符、实体起始符、反斜杠及控制字符
     * 保留普通空格与 Unicode 文本，清理后再校验属性长度
     */
    private static String sanitizeCommandAttribute(String value) {
        if (value == null) throw new IllegalArgumentException("交互标签属性不能为空");
        StringBuilder result = new StringBuilder(value.length());
        value.codePoints().forEach(codePoint -> {
            if (codePoint == '"' || codePoint == '\'' || codePoint == '<' || codePoint == '>'
                    || codePoint == '&' || codePoint == '\\' || Character.isISOControl(codePoint)
                    || codePoint == 0x2028 || codePoint == 0x2029) return;
            result.appendCodePoint(codePoint);
        });
        return result.toString();
    }

    public static String at(String userOpenId) {
        return "<@" + userOpenId + ">";
    }

    public static String link(String url, String display) {
        return "[" + display + "](" + url + ")";
    }

    /**
     * 每日仅限一次，允许在普通文本和 Markdown 中使用，且需要开发者启用发送权限
     */
    public static String atAll() {
        return "<qqbot-at-everyone />";
    }

    public static String colored(HexColor color, String content) {
        return "$\\textcolor{" + color.value() + "}{\\text{" + content + "}}$";
    }

    @Override
    public String toString() {
        return text;
    }

    public Markdown append(String text) {
        return new Markdown(this.text + text, this.forceVerifyImageResource, this.keyboard);
    }

    public Markdown append(Markdown markdown) {

        if (markdown.getKeyboard() != null) {
            throw new UnsupportedOperationException("不能拼接带有键盘的 Markdown 消息！");
        }

        return new Markdown(this.text + markdown.text, this.forceVerifyImageResource, this.getKeyboard());
    }

    public Markdown setKeyboard(Keyboard keyboard) {
        this.keyboard = keyboard;
        return this;
    }

    private static boolean isOutOfLimit(String str) {
        return str.length() > 100;
    }
}
