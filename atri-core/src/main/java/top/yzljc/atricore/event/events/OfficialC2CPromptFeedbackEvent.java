package top.yzljc.atricore.event.events;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;

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

    /**
     * 在 data 里还有一层 type，我也不知道干啥的顺手给封装了
     */
    private final int dataType;
    private final int chatType;
    private final String userOpenId;
    /**
     * 注意，这个message_id是你发送带有气泡按钮那条消息的message_id，而不是用户按下选项后发出消息的message_id
     */
    private final String targetMessageId;
    private final String feedbackOpt;
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
    }


}