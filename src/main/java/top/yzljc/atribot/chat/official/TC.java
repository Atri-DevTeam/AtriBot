package top.yzljc.atribot.chat.official;

import top.yzljc.atribot.chat.official.button.Button;
import top.yzljc.atribot.chat.official.button.PermissionType;
import top.yzljc.atribot.platform.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName TC
 * @Created_at 2026/06/02
 * @Project AtriBot
 * @Package top.yzljc.atribot.chat.official
 */
public class TC {
    public static Markdown md(String text) {
        return new Markdown(text);
    }

    public static Object promptKeyboard(List<List<Button>> layout) {
        return Map.of("keyboard", keyboard(layout));
    }

    public static Object keyboard(List<List<Button>> layout) {
        List<Map<String, Object>> rows = new ArrayList<>();

        for (List<Button> rowBtns : layout) {
            List<Map<String, Object>> buttons = new ArrayList<>();

            for (Button btn : rowBtns) {
                Map<String, Object> action = new HashMap<>();
                action.put("type", btn.getActionType().getCode());
                action.put("data", btn.getData());
                action.put("enter", btn.isEnter());
                action.put("unsupport_tips", "当前客户端版本不支持此按钮");
                if (btn.isReply()) {
                    action.put("reply", true);
                }

                Map<String, Object> permission = new HashMap<>();
                permission.put("type", btn.getPermissionType().getCode());
                if (btn.getPermissionType() == PermissionType.SPECIFIC_USER
                        && !btn.getAllowedOpenIds().isEmpty()) {
                    permission.put("specify_user_ids", btn.getAllowedOpenIds());
                }
                action.put("permission", permission);

                if (!btn.getModal().getContent().equals(Identifier.UNDEFINED)) {
                    Map<String, Object> modal = new HashMap<>();
                    modal.put("content", btn.getModal().getContent());
                    var confirm = btn.getModal().getConfirmText();
                    var cancel = btn.getModal().getCancelText();
                    if (confirm != null) modal.put("confirm_text", confirm);
                    if (cancel != null) modal.put("cancel_text", cancel);
                    action.put("modal", modal);
                }

                Map<String, Object> renderData = new HashMap<>();
                renderData.put("label", btn.getDisplayText());
                renderData.put("visited_label", btn.getVisitedDisplayText());
                renderData.put("style", btn.getStyle().getCode());

                Map<String, Object> button = new HashMap<>();
                button.put("id", btn.getButtonId());
                button.put("render_data", renderData);
                button.put("action", action);

                buttons.add(button);
            }
            Map<String, Object> row = new HashMap<>();
            row.put("buttons", buttons);
            rows.add(row);
        }
        Map<String, Object> keyboard = new HashMap<>();
        Map<String, Object> content = new HashMap<>();
        content.put("rows", rows);
        keyboard.put("content", content);
        return keyboard;
    }
}