package top.yzljc.atricore.chat.official.button;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import top.yzljc.atricore.platform.Identifier;

import java.io.IOException;
import java.util.List;

/**
 * QQ 键盘协议结构及序列化映射，供消息发送管道使用
 *
 * @Author YZ_Ljc_
 * @ClassName KeyboardPayload
 * @Created_at 2026/11/04
 * @Project AtriMeow
 * @Package top.yzljc.atricore.chat.official.button
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record KeyboardPayload(String id, Content content) {
    /**
     * 将键盘布局转换为独立的协议快照
     *
     * @param keyboard 键盘布局
     * @return QQ 键盘请求结构
     */
    public static KeyboardPayload from(Keyboard keyboard) {
        var rows = keyboard.getButtons().stream()
                .map(row -> new Row(row.stream().map(KeyboardPayload::fromButton).toList())).toList();
        var style = keyboard.getSize() == ButtonSize.UNDEFINED ? null : new Style(keyboard.getSize().getSize());
        return new KeyboardPayload(null, new Content(rows, style));
    }

    private static Item fromButton(Button button) {
        var permission = new Permission(button.getPermissionType().getCode(),
                button.getPermissionType() == PermissionType.SPECIFIC_USER && !button.getAllowedOpenIds().isEmpty()
                        ? List.copyOf(button.getAllowedOpenIds()) : null, null);
        var modal = button.getModal();
        var dialog = modal != null && !Identifier.UNDEFINED.equals(modal.getContent())
                ? new Modal(modal.getContent(), modal.getConfirmText(), modal.getCancelText()) : null;
        var action = new Action(button.getActionType().getCode(), button.getData(), button.isEnter(),
                button.isReply() ? true : null, button.getUnsupportedTip(), permission, dialog, null);
        return new Item(button.getButtonId(), button.getButtonId() == null ? null : button.getButtonGroupId(),
                new RenderData(button.getDisplayText(), button.getVisitedDisplayText(), button.getStyle().getCode()), action);
    }

    /**
     * 将 Keyboard 按 QQ 键盘协议序列化，忽略布局辅助属性
     */
    public static final class Serializer extends JsonSerializer<Keyboard> {
        @Override
        public void serialize(Keyboard keyboard, JsonGenerator generator, SerializerProvider provider) throws IOException {
            provider.defaultSerializeValue(from(keyboard), generator);
        }
    }

    /**
     * 键盘内容及显示样式
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Content(List<Row> rows, Style style) {
    }

    /**
     * 单行按钮
     */
    public record Row(List<Item> buttons) {
    }

    /**
     * 键盘字体样式
     */
    public record Style(@JsonProperty("font_size") String fontSize) {
    }

    /**
     * 按钮标识、显示内容及点击动作
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Item(String id, @JsonProperty("group_id") String groupId,
                       @JsonProperty("render_data") RenderData renderData, Action action) {
    }

    /**
     * 按钮显示内容
     */
    public record RenderData(String label, @JsonProperty("visited_label") String visitedLabel, int style) {
    }

    /**
     * 按钮点击动作
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Action(int type, String data, Boolean enter, Boolean reply,
                         @JsonProperty("unsupport_tips") String unsupportedTips,
                         Permission permission, Modal modal,
                         @JsonProperty("click_limit") Integer clickLimit) {
    }

    /**
     * 按钮点击权限
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Permission(int type, @JsonProperty("specify_user_ids") List<String> specifyUserIds,
                             @JsonProperty("specify_role_ids") List<String> specifyRoleIds) {
    }

    /**
     * 点击确认弹窗
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Modal(String content, @JsonProperty("confirm_text") String confirmText,
                        @JsonProperty("cancel_text") String cancelText) {
    }
}
