package top.yzljc.atribot.chat.official.button;

import lombok.Getter;
import top.yzljc.atribot.platform.Identifier;

import java.util.Collections;
import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName Button
 * @Created_at 2026/06/11
 * @Project AtriBot
 * @Package top.yzljc.atribot.chat.official.button
 */
@Getter
public class Button {
    private final String buttonId;
    private final String displayText;
    private String visitedDisplayText;
    private final String data;
    private boolean enter = true;
    private boolean reply = false;
    private final ButtonStyle style;
    private final ButtonType actionType;
    private PermissionType permissionType = PermissionType.ALL;
    private List<String> allowedOpenIds = Collections.emptyList();
    private Modal modal;

    public Button(String buttonId, String displayText, String data, ButtonStyle style, ButtonType actionType) {
        this.buttonId = buttonId;
        this.displayText = displayText;
        this.visitedDisplayText = displayText;
        this.data = data;
        this.style = style;
        this.actionType = actionType;
        this.modal = new Modal(Identifier.UNDEFINED);
    }

    public Button(String buttonId, String displayText, String data, boolean enter, ButtonStyle style, ButtonType actionType) {
        this.buttonId = buttonId;
        this.displayText = displayText;
        this.visitedDisplayText = displayText;
        this.data = data;
        this.enter = enter;
        this.style = style;
        this.actionType = actionType;
        this.modal = new Modal(Identifier.UNDEFINED);
    }

    public Button setReply(boolean reply) {
        this.reply = reply;
        return this;
    }

    public Button setVisitedDisplayText(String visitedDisplayText) {
        this.visitedDisplayText = visitedDisplayText;
        return this;
    }

    public Button setPermissionType(PermissionType permissionType) {
        this.permissionType = permissionType;
        return this;
    }

    public Button setAllowedOpenIds(List<String> allowedOpenIds) {
        this.allowedOpenIds = allowedOpenIds;
        return this;
    }

    public Button setEnter(boolean enter) {
        this.enter = enter;
        return this;
    }

    public Button setModal(String content) {
        this.modal.content = content;
        return this;
    }

    public Button setModal(String content, String confirmText, String cancelText) {
        if (confirmText.length() > 4 || cancelText.length() > 4) {
            throw new IllegalArgumentException("键盘按钮的Modal参数中，确认和取消按钮的字数不能超过4个");
        }
        this.modal.content = content;
        this.modal.confirmText = confirmText;
        this.modal.cancelText = cancelText;
        return this;
    }

    @Getter
    public static class Modal {
        private String content;
        private String confirmText;
        private String cancelText;

        public Modal(String content) {
            this.content = content;
        }
    }
}