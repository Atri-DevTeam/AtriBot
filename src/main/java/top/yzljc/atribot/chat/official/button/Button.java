package top.yzljc.atribot.chat.official.button;

import lombok.Getter;
import lombok.Setter;

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
    @Setter
    private String visitedDisplayText;
    private final String data;
    private final boolean enter;
    @Setter
    private boolean reply = false;
    private final ButtonStyle style;
    private final ButtonType actionType;
    @Setter
    private PermissionType permissionType = PermissionType.ALL;
    @Setter
    private List<String> allowedOpenIds = Collections.emptyList();

    public Button(String buttonId, String displayText, String data, boolean enter, ButtonStyle style, ButtonType actionType) {
        this.buttonId = buttonId;
        this.displayText = displayText;
        this.visitedDisplayText = displayText;
        this.data = data;
        this.enter = enter;
        this.style = style;
        this.actionType = actionType;
    }
}