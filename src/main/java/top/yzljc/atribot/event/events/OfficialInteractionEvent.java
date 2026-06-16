package top.yzljc.atribot.event.events;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import top.yzljc.atribot.Atri;
import top.yzljc.atribot.chat.official.C2CChat;
import top.yzljc.atribot.chat.official.GroupChat;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.event.Event;
import top.yzljc.atribot.event.impl.AnswerCode;
import top.yzljc.atribot.service.request.HttpService;

import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName OfficialInteractionEvent
 * @Created_at 2026/06/10
 * @Project AtriBot
 * @Package top.yzljc.atribot.event.impl
 */
@Getter
@AllArgsConstructor
public class OfficialInteractionEvent extends Event {
    private final int chatType;
    private final Data data;
    private final String groupOpenId;
    private final String unionOpenId;
    private final String id;
    private final String scene;
    private final String timestamp;
    private final int type;

    @Getter
    @AllArgsConstructor
    public static class Data {
        private final JsonNode resolved;
        private final int type;
    }

    @SuppressWarnings("UnusedReturnValue")
    public boolean answer(AnswerCode status) {
        Map<String, Integer> body = Map.of("code", status.getCode());
        JsonNode response = HttpService.putJson(Config.getInstance().getQqApiBaseUrl() + "/interactions/" + id, body, "Authorization", "QQBot " + Atri.getInstance().getTokenManager().getAccessToken());
        return response != null;
    }

    public String getButtonValue() {
        if (type == 11 && data.resolved.has("button_data")) {
            return data.resolved.get("button_data").asText();
        } else {
            return "missing_data";
        }
    }

    public String getButtonId() {
        if (type == 11 && data.resolved.has("button_id")) {
            return data.resolved.get("button_id").asText();
        } else {
            return "missing_data";
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String text) {
        if (this.chatType == 1) {
            return GroupChat.sendMessage(this.groupOpenId, text);
        } else if (this.chatType == 2) {
            return C2CChat.sendMessage(this.unionOpenId, text);
        } else {
            throw new UnsupportedOperationException("Unsupported chat type: " + this.chatType);
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(Markdown content) {
        if (this.chatType == 1) {
            return GroupChat.sendMessage(this.groupOpenId, content);
        } else if (this.chatType == 2) {
            return C2CChat.sendMessage(this.unionOpenId, content);
        } else {
            throw new UnsupportedOperationException("Unsupported chat type: " + this.chatType);
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(Markdown content, Object keyboard) {
        if (this.chatType == 1) {
            return GroupChat.sendMessage(this.groupOpenId, content, keyboard);
        } else if (this.chatType == 2) {
            return C2CChat.sendMessage(this.unionOpenId, content, keyboard);
        } else {
            throw new UnsupportedOperationException("Unsupported chat type: " + this.chatType);
        }
    }
}