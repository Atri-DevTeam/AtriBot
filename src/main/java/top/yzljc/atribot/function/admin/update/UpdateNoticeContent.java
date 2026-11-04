package top.yzljc.atribot.function.admin.update;

import top.yzljc.atricore.chat.official.Markdown;
import top.yzljc.atricore.chat.official.TC;
import top.yzljc.atricore.chat.official.button.Button;
import top.yzljc.atricore.chat.official.button.ButtonStyle;
import top.yzljc.atricore.chat.official.button.ButtonType;
import top.yzljc.atricore.chat.official.button.Keyboard;

import java.net.URI;
import java.util.List;

/**
 * 更新通知正文、分格式头尾模板及内联键盘
 *
 * @Author YZ_Ljc_
 * @ClassName UpdateNoticeContent
 * @Created_at 2026/11/03
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.admin.update
 */
public record UpdateNoticeContent(String text, String format, List<List<NoticeButton>> buttons,
                                  MessageLayout textLayout, MessageLayout markdownLayout) {
    public UpdateNoticeContent(String text, String format, List<List<NoticeButton>> buttons) {
        this(text, format, buttons, null, null);
    }

    public UpdateNoticeContent {
        text = text == null ? "" : text.trim();
        format = format == null ? "TEXT" : format;
        if (!format.equals("TEXT") && !format.equals("MARKDOWN")) throw new IllegalArgumentException("不支持的消息格式");
        if (text.length() > 16000) throw new IllegalArgumentException("正文不能超过 16000 字符");
        buttons = buttons == null ? List.of() : buttons;
        if (buttons.size() > 5) throw new IllegalArgumentException("按钮最多 5 行");
        for (var row : buttons) {
            if (row == null || row.isEmpty() || row.size() > 10 || row.stream().anyMatch(java.util.Objects::isNull))
                throw new IllegalArgumentException("每行需包含 1 至 10 个按钮");
        }
        buttons = buttons.stream().map(List::copyOf).toList();
        textLayout = textLayout == null ? MessageLayout.defaults() : textLayout;
        markdownLayout = markdownLayout == null ? MessageLayout.defaults() : markdownLayout;
    }

    /** 省略模板时使用原有头尾，空字符串表示不显示对应部分 */
    public record MessageLayout(String header, String footer) {
        public MessageLayout {
            header = header == null ? "" : header.trim();
            footer = footer == null ? "" : footer.trim();
            if (header.length() > 4000 || footer.length() > 4000)
                throw new IllegalArgumentException("头部和尾部分别不能超过 4000 字符");
        }

        public static MessageLayout defaults() {
            return new MessageLayout("{botName}近期更新日志:\n> {time}", "遇到了一些问题？使用/feedback向开发者反馈！");
        }
    }

    /** 按消息格式组合正文，变量仅在头尾模板中展开 */
    public String render(String botName, String time) {
        MessageLayout layout = format.equals("MARKDOWN") ? markdownLayout : textLayout;
        return java.util.stream.Stream.of(expand(layout.header(), botName, time), text,
                        expand(layout.footer(), botName, time))
                .filter(part -> !part.isBlank()).collect(java.util.stream.Collectors.joining("\n\n"));
    }

    private static String expand(String template, String botName, String time) {
        return java.util.regex.Pattern.compile("\\{(botName|time)}").matcher(template)
                .replaceAll(match -> java.util.regex.Matcher.quoteReplacement(
                        match.group(1).equals("botName") ? java.util.Objects.requireNonNullElse(botName, "机器人") : time));
    }

    public record NoticeButton(String label, String data, String type, String style, boolean enter) {
        public NoticeButton {
            label = label == null ? "" : label.trim();
            data = data == null ? "" : data.trim();
            if (label.isEmpty() || label.length() > 40) throw new IllegalArgumentException("按钮文字需为 1 至 40 字符");
            if (data.isEmpty() || data.length() > 2048) throw new IllegalArgumentException("按钮内容需为 1 至 2048 字符");
            if (type == null || !List.of("COMMAND", "LINK").contains(type)) throw new IllegalArgumentException("按钮类型无效");
            if (style == null || !List.of("GRAY", "BLUE", "RED", "BLUE_WITH_BACKGROUND").contains(style)) throw new IllegalArgumentException("按钮样式无效");
            if (type.equals("LINK")) {
                try {
                    URI uri = URI.create(data);
                    if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                            || uri.getHost() == null) throw new IllegalArgumentException();
                } catch (IllegalArgumentException failure) {
                    throw new IllegalArgumentException("链接按钮需填写完整的 HTTP 或 HTTPS 地址");
                }
            }
        }
    }

    public Markdown markdown(String renderedText) {
        var md = TC.md(renderedText);
        if (!buttons.isEmpty()) {
            md.setKeyboard(new Keyboard(buttons.stream().map(row -> row.stream().map(b ->
                    new Button("update_" + java.util.UUID.randomUUID(), b.label(), b.data(), b.enter(),
                            ButtonStyle.valueOf(b.style()), ButtonType.valueOf(b.type()))).toList()).toList()));
        }
        return md;
    }
}
