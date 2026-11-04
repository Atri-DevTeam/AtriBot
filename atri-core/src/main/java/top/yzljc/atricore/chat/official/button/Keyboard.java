package top.yzljc.atricore.chat.official.button;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
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
@JsonSerialize(using = KeyboardPayload.Serializer.class)
public class Keyboard {

    private boolean isPromptStyle;
    private ButtonSize size;
    private final List<List<Button>> buttons;

    public Keyboard(List<List<Button>> buttons) {
        if (isOutOfLimit(buttons))
            throw new IllegalArgumentException("内联键盘按钮行数不能超过 5 行，每行按钮数量不能超过 10 个");
        this.buttons = buttons;
        this.size = ButtonSize.UNDEFINED;
        this.isPromptStyle = false;
    }

    public Keyboard(List<List<Button>> buttons, ButtonSize size) {
        if (isOutOfLimit(buttons))
            throw new IllegalArgumentException("内联键盘按钮行数不能超过 5 行，每行按钮数量不能超过 10 个");
        this.buttons = buttons;
        this.size = size;
        this.isPromptStyle = false;
    }

    public Keyboard(List<List<Button>> buttons, ButtonSize size, boolean isPromptStyle) {
        if (isOutOfLimit(buttons))
            throw new IllegalArgumentException("内联键盘按钮行数不能超过 5 行，每行按钮数量不能超过 10 个");
        this.buttons = buttons;
        this.size = size;
        this.isPromptStyle = isPromptStyle;
    }

    public static Keyboard of(List<List<Button>> buttons) {
        return new Keyboard(buttons);
    }

    public static Keyboard single(Button button) {
        return new Keyboard(List.of(List.of(button)));
    }

    public static Keyboard singleLine(List<Button> buttons) {
        return new Keyboard(List.of(buttons));
    }

    public Keyboard setSize(ButtonSize size) {
        this.size = size;
        return this;
    }

    public Keyboard setPromptStyle(boolean isPrompt) {
        this.isPromptStyle = isPrompt;
        return this;
    }

    /**
     * 将当前键盘追加到现有键盘的按钮列表中
     * 注意，默认是加在原键盘的后方，如果需要加在前方，请添加参数 {@code reverse} = true
     *
     * @param buttons 要追加的按钮列表
     * @return 新的 Keyboard 对象
     */
    public Keyboard append(List<List<Button>> buttons) {
        return append(buttons, false);
    }

    /**
     * 将当前键盘追加到现有键盘的按钮列表中
     * 注意，默认是加在原键盘的后方，如果需要加在前方，请添加参数 {@code reverse} = true
     *
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
