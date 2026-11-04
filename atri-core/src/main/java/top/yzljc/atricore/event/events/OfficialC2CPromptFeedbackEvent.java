package top.yzljc.atricore.event.events;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import top.yzljc.atricore.chat.ImageComponent;
import top.yzljc.atricore.chat.official.C2CChat;
import top.yzljc.atricore.chat.official.Markdown;
import top.yzljc.atricore.chat.official.RT;
import top.yzljc.atricore.chat.official.button.ActionButton;
import top.yzljc.atricore.chat.official.card.Card;

/**
 * @Author YZ_Ljc_
 * @ClassName OfficialC2CPromptFeedbackEvent
 * @Created_at 2026/10/18
 * @Project AtriMeow
 * @Package top.yzljc.atricore.event.events
 * @Description 目前已知气泡按钮可以触发这个事件
 */
@Getter
public class OfficialC2CPromptFeedbackEvent extends OfficialInteractionEvents {

    public static final String LIKE = "LIKE";
    public static final String UNLIKE = "UNLIKE";
    public static final String REGENERATE = "REGENERATE";
    public static final String PROMPT_CLICK = "PROMPTCLICK";

    /**
     * 在 data 里还有一层 type，我也不知道干啥的顺手给封装了
     */
    private final int dataType;
    private final int chatType;
    private final String userOpenId;
    /**
     * 注意，这个message_id是机器人发送带有操作按钮的那条消息的message_id，而不是用户按下选项后发出消息的message_id
     */
    private final String targetMessageId;
    private final String callbackData;
    private final String feedbackOpt;
    /**
     * 1 = 选择，2 = 取消选择，如果你看到了 0 证明数据缺失
     */
    private final int checked;
    private final JsonNode messageScene;

    public OfficialC2CPromptFeedbackEvent(String applicationId, String eventId, String id, String scene, String timestamp, int type, int version,
                                          int dataType, int chatType, String userOpenId, JsonNode resolvedData) {
        super(applicationId, eventId, id, scene, timestamp, type, version);
        this.dataType = dataType;
        this.chatType = chatType;
        this.userOpenId = userOpenId;
        this.targetMessageId = resolvedData.path("message_id").asText(null);
        this.feedbackOpt = resolvedData.path("feedback_opt").asText(null);
        this.checked = resolvedData.path("checked").asInt(0);
        this.messageScene = resolvedData.path("message_scene");
        this.callbackData = resolvedData.path("button_data").asText(null);
    }

    public <T> String sendMessage(T content, ActionButton lastActionButton) {
        return switch (content) {
            case String text -> C2CChat.replyMessage(this.userOpenId, RT.event(this.eventId), text, lastActionButton);
            case Markdown md -> C2CChat.replyMessage(this.userOpenId, RT.event(this.eventId), md, lastActionButton);
            case ImageComponent i -> C2CChat.replyMessage(this.userOpenId, RT.event(this.eventId), i, lastActionButton);
            case Card card -> C2CChat.replyMessage(this.userOpenId, RT.event(this.eventId), card);
            case null, default -> C2CChat.replyMessage(this.userOpenId, RT.event(this.eventId),
                    content == null ? "" : content.toString(), lastActionButton);
        };
    }
}