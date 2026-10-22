package top.yzljc.atricore.chat.official.button;

import lombok.Getter;
import lombok.Setter;
import top.yzljc.atricore.chat.official.Markdown;
import top.yzljc.atricore.chat.official.TC;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * @Author YZ_Ljc_
 * @ClassName Keyboard
 * @Created_at 2026/09/20
 * @Project AtriMeow
 * @Package top.yzljc.atricore.chat.official.button
 */
@Getter
@Setter
public class Keyboard {

    private static final Pattern GRAPHEME = Pattern.compile("\\X");
    private static final String ALIGNMENT_SPACE = "\u2002";
    private static final int COLUMN_GAP = 2;

    private boolean isPromptStyle;
    private ButtonSize size;
    private final List<List<Button>> buttons;

    public Keyboard(List<List<Button>> buttons) {
        if (isOutOfLimit(buttons)) throw new IllegalArgumentException("内联键盘按钮行数不能超过 5 行，每行按钮数量不能超过 10 个");
        this.buttons = buttons;
        this.size = ButtonSize.UNDEFINED;
        this.isPromptStyle = false;
    }

    public Keyboard(List<List<Button>> buttons, ButtonSize size) {
        if (isOutOfLimit(buttons)) throw new IllegalArgumentException("内联键盘按钮行数不能超过 5 行，每行按钮数量不能超过 10 个");
        this.buttons = buttons;
        this.size = size;
        this.isPromptStyle = false;
    }

    public Keyboard(List<List<Button>> buttons, ButtonSize size, boolean isPromptStyle) {
        if (isOutOfLimit(buttons)) throw new IllegalArgumentException("内联键盘按钮行数不能超过 5 行，每行按钮数量不能超过 10 个");
        this.buttons = buttons;
        this.size = size;
        this.isPromptStyle = isPromptStyle;
    }

    public Keyboard setSize(ButtonSize size) {
        this.size = size;
        return this;
    }

    public Keyboard setPromptStyle(boolean isPrompt) {
        this.isPromptStyle = isPrompt;
        return this;
    }

    public Object toKeyboardObject() {
        if (this.isPromptStyle) {
            return TC.promptKeyboard(this.buttons);
        } else {
            return TC.keyboard(this.buttons, this.size);
        }
    }

    /**
     * 当用户设置为设备不兼容按钮显示时，转化按钮为可交互的Markdown文本使用
     * 按钮数量相同的行按每列最长标签补齐间距，避免独占一行的长按钮撑宽其他布局
     * 客户端使用比例字体时只能近似对齐
     * @return Markdown 格式的按钮文本
     */
    public String toMarkdownString() {
        Map<Integer, int[]> widthsByColumnCount = new HashMap<>();
        for (var row : buttons) {
            if (row.isEmpty()) continue;
            int[] columnWidths = widthsByColumnCount.computeIfAbsent(row.size(), int[]::new);
            for (int column = 0; column < row.size(); column++) {
                columnWidths[column] = Math.max(columnWidths[column], displayWidth(row.get(column).getDisplayText()));
            }
        }

        StringBuilder btnStr = new StringBuilder("\n\n---\n\n");
        for (var row : this.buttons) {
            if (row.isEmpty()) continue;
            int[] columnWidths = widthsByColumnCount.get(row.size());
            if (this.size == ButtonSize.SMALL) btnStr.append("> ");
            for (int column = 0; column < row.size(); column++) {
                var btn = row.get(column);
                if (btn.getActionType() == ButtonType.COMMAND) {
                    btnStr.append(Markdown.enterCommand(btn.getData(), btn.getDisplayText(), btn.isReply()));
                } else if (btn.getActionType() == ButtonType.LINK) {
                    btnStr.append(Markdown.link(btn.getData(), btn.getDisplayText()));
                } else {
                    throw new UnsupportedOperationException("回调按钮不支持转化为 Markdown 文本！");
                }
                if (column + 1 < row.size()) {
                    int padding = columnWidths[column] - displayWidth(btn.getDisplayText()) + COLUMN_GAP;
                    btnStr.append(ALIGNMENT_SPACE.repeat(padding));
                }
            }

            btnStr.append("\n\n");
        }

        return btnStr.toString();
    }

    private static int displayWidth(String text) {
        int width = 0;
        var matcher = GRAPHEME.matcher(text);
        while (matcher.find()) {
            String grapheme = matcher.group();
            int graphemeWidth = grapheme.codePoints().map(Keyboard::codePointWidth).max().orElse(0);
            if (graphemeWidth > 0 && grapheme.codePoints().anyMatch(cp -> Character.isEmojiPresentation(cp) || cp == 0xFE0F || cp == 0x20E3)) {
                graphemeWidth = Math.max(2, graphemeWidth);
            }
            width += graphemeWidth;
        }
        return width;
    }

    private static int codePointWidth(int codePoint) {
        int type = Character.getType(codePoint);
        if (type == Character.CONTROL || type == Character.FORMAT || type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK || type == Character.ENCLOSING_MARK) return 0;

        boolean wide = codePoint >= 0x1100 && codePoint <= 0x115F
                || codePoint == 0x2329 || codePoint == 0x232A
                || codePoint >= 0x2E80 && codePoint <= 0xA4CF && codePoint != 0x303F
                || codePoint >= 0xA960 && codePoint <= 0xA97F
                || codePoint >= 0xAC00 && codePoint <= 0xD7A3
                || codePoint >= 0xF900 && codePoint <= 0xFAFF
                || codePoint >= 0xFE10 && codePoint <= 0xFE19
                || codePoint >= 0xFE30 && codePoint <= 0xFE6F
                || codePoint >= 0xFF01 && codePoint <= 0xFF60
                || codePoint >= 0xFFE0 && codePoint <= 0xFFE6
                || codePoint >= 0x16FE0 && codePoint <= 0x18DFF
                || codePoint >= 0x1AFF0 && codePoint <= 0x1B2FF
                || codePoint >= 0x1F200 && codePoint <= 0x1F251
                || codePoint >= 0x20000 && codePoint <= 0x3FFFD;
        return wide ? 2 : 1;
    }

    /**
     * 当用户设置为设备不兼容按钮显示时，转化按钮为可交互的Markdown文本使用
     * @return Markdown 对象
     */
    public Markdown toMarkdown() {
        return new Markdown(this.toMarkdownString());
    }

    /**
     * 将当前键盘追加到现有键盘的按钮列表中
     * 注意，默认是加在原键盘的后方，如果需要加在前方，请添加参数 {@code reverse} = true
     * @param buttons 要追加的按钮列表
     * @return 新的 Keyboard 对象
     */
    public Keyboard append(List<List<Button>> buttons) {
        return append(buttons, false);
    }

    /**
     * 将当前键盘追加到现有键盘的按钮列表中
     * 注意，默认是加在原键盘的后方，如果需要加在前方，请添加参数 {@code reverse} = true
     * @param buttons 要追加的按钮列表
     * @param reverse false（默认）加在后方；true 加在前方
     * @return 新的 Keyboard 对象
     * @throws IllegalArgumentException 如果合并后行数超过 5 行，或任一行按钮数超过 10 个
     */
    public Keyboard append(List<List<Button>> buttons, boolean reverse) {
        if (isOutOfLimit(buttons) || this.buttons.size() + buttons.size() > 5) {
            throw new IllegalArgumentException("内联键盘按钮行数不能超过 5 行，每行按钮数量不能超过 10 个");
        }

        Stream<List<Button>> stream = reverse
                ? Stream.concat(buttons.stream(), this.buttons.stream())
                : Stream.concat(this.buttons.stream(), buttons.stream());

        List<List<Button>> newButtons = stream.map(List::copyOf).toList();
        return new Keyboard(newButtons, this.size, this.isPromptStyle);
    }

    private static boolean isOutOfLimit(List<List<Button>> buttons) {
        if (buttons.size() > 5) return true;
        for (var row : buttons) {
            if (row.size() > 10) return true;
        }
        return false;
    }
}
