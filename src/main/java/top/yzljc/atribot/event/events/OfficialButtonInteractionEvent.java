package top.yzljc.atribot.event.events;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import top.yzljc.atribot.Atri;
import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.chat.official.C2CChat;
import top.yzljc.atribot.chat.official.GroupChat;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.event.Cancellable;
import top.yzljc.atribot.event.impl.AnswerCode;
import top.yzljc.atribot.event.impl.UnknownButtonInteractionScene;
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
public class OfficialButtonInteractionEvent extends OfficialInteractionEvents implements Cancellable {
    private final int chatType;
    private final Data data;
    private final String groupOpenId;
    private final String unionOpenId;
    private boolean cancelled;

    public OfficialButtonInteractionEvent(String applicationId, String eventId, int chatType, Data data, String groupOpenId, String unionOpenId, String id, String scene, String timestamp, int type, int version) {
        super(applicationId, eventId, id, scene, timestamp, type, version);
        this.chatType = chatType;
        this.data = data;
        this.groupOpenId = groupOpenId;
        this.unionOpenId = unionOpenId;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Getter
    @AllArgsConstructor
    public static class Data {
        private final JsonNode resolved;
        private final int type;
    }

    @SuppressWarnings("UnusedReturnValue")
    public boolean answer(AnswerCode status) {
        if (this.cancelled) {
            status = AnswerCode.FAIL;
        }
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
    public String replyMessage(Markdown markdown) {
        return replyMessage(markdown, false);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String replyMessage(Markdown markdown, boolean at) {
        if (this.chatType == 1) {
            if (at) {
                return GroupChat.replyEventMessage(this.groupOpenId, this.unionOpenId, this.eventId, markdown);
            }
            return GroupChat.replyEventMessage(this.groupOpenId, this.eventId, markdown);
        } else if (this.chatType == 2) {
            return C2CChat.replyEventMessage(this.unionOpenId, this.eventId, markdown);
        } else {
            throw new UnknownButtonInteractionScene(this.chatType, this.scene);
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public String replyMessage(Markdown markdown, Object keyboard) {
        return replyMessage(markdown, keyboard, false);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String replyMessage(Markdown markdown, Object keyboard, boolean at) {
        if (this.chatType == 1) {
            if (at) {
                return GroupChat.replyEventMessage(this.groupOpenId, this.unionOpenId, this.eventId, markdown, keyboard);
            }
            return GroupChat.replyEventMessage(this.groupOpenId, this.eventId, markdown, keyboard);
        } else if (this.chatType == 2) {
            return C2CChat.replyEventMessage(this.unionOpenId, this.eventId, markdown, keyboard);
        } else {
            throw new UnknownButtonInteractionScene(this.chatType, this.scene);
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public String replyMessage(String text) {
        if (this.chatType == 1) {
            return GroupChat.replyEventMessage(this.groupOpenId, this.eventId, text);
        } else if (this.chatType == 2) {
            return C2CChat.replyEventMessage(this.unionOpenId, this.eventId, text);
        } else {
            throw new UnknownButtonInteractionScene(this.chatType, this.scene);
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public String replyMessage(ImageComponent image) {
        if (this.chatType == 1) {
            return GroupChat.replyEventMessage(this.groupOpenId, this.eventId, image);
        } else if (this.chatType == 2) {
            return C2CChat.replyEventMessage(this.unionOpenId, this.eventId, image);
        } else {
            throw new UnknownButtonInteractionScene(this.chatType, this.scene);
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String text) {
        if (this.chatType == 1) {
            return GroupChat.sendMessage(this.groupOpenId, text);
        } else if (this.chatType == 2) {
            return C2CChat.sendMessage(this.unionOpenId, text);
        } else {
            throw new UnknownButtonInteractionScene(this.chatType, this.scene);
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(Markdown content) {
        if (this.chatType == 1) {
            return GroupChat.sendMessage(this.groupOpenId, content);
        } else if (this.chatType == 2) {
            return C2CChat.sendMessage(this.unionOpenId, content);
        } else {
            throw new UnknownButtonInteractionScene(this.chatType, this.scene);
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(Markdown content, Object keyboard) {
        if (this.chatType == 1) {
            return GroupChat.sendMessage(this.groupOpenId, content, keyboard);
        } else if (this.chatType == 2) {
            return C2CChat.sendMessage(this.unionOpenId, content, keyboard);
        } else {
            throw new UnknownButtonInteractionScene(this.chatType, this.scene);
        }
    }

    public boolean shouldIgnore() {
        if (this.groupOpenId != null) {
            if (OfficialGroups.isGroupBlacklisted(this.groupOpenId)) {
                answer(AnswerCode.FAIL);
                return true;
            }
        }
        if (OfficialUsers.isBlocked(this.unionOpenId) || OfficialUsers.isIgnored(this.unionOpenId)) {
            answer(AnswerCode.FAIL);
            return true;
        }
        return false;
    }
}
