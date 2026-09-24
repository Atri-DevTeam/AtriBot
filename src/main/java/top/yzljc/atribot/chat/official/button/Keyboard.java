package top.yzljc.atribot.chat.official.button;

import lombok.Getter;
import lombok.Setter;
import org.jline.utils.WCWidth;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * @Author YZ_Ljc_
 * @ClassName Keyboard
 * @Created_at 2026/09/20
 * @Project AtriMeow
 * @Package top.yzljc.atribot.chat.official.button
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
     * 按钮数量相同的行按每列最长标签补齐间距，避免独占一行的长按钮撑宽其他布局。
     * 客户端使用比例字体时只能近似对齐。
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
            int graphemeWidth = grapheme.codePoints().map(cp -> Math.max(0, WCWidth.wcwidth(cp))).max().orElse(0);
            if (grapheme.codePoints().anyMatch(cp -> Character.isEmojiPresentation(cp) || cp == 0xFE0F || cp == 0x20E3)) {
                graphemeWidth = Math.max(2, graphemeWidth);
            }
            width += graphemeWidth;
        }
        return width;
    }

    /**
     * 当用户设置为设备不兼容按钮显示时，转化按钮为可交互的Markdown文本使用
     * @return Markdown 对象
     */
    public Markdown toMarkdown() {
        return new Markdown(this.toMarkdownString());
    }

    private static boolean isOutOfLimit(List<List<Button>> buttons) {
        if (buttons.size() > 5) return true;
        for (var row : buttons) {
            if (row.size() > 10) return true;
        }
        return false;
    }
}
