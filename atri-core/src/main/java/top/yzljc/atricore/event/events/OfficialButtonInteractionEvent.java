package top.yzljc.atricore.event.events;

import top.yzljc.atricore.AtriProvider;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atricore.chat.official.C2CChat;
import top.yzljc.atricore.chat.official.GroupChat;
import top.yzljc.atricore.chat.official.Markdown;
import top.yzljc.atricore.chat.official.RT;
import top.yzljc.atricore.chat.ImageComponent;
import top.yzljc.atricore.event.Cancellable;
import top.yzljc.atricore.event.impl.AnswerCode;
import top.yzljc.atricore.event.impl.UnknownButtonInteractionScene;
import top.yzljc.atricore.service.request.HttpService;
import top.yzljc.atricore.service.runtime.ThreadManager;

import java.util.concurrent.RejectedExecutionException;

/**
 * 收到 {@code INTERACTION_CREATE} 中的按钮点击交互时触发
 *
 * @Author YZ_Ljc_
 * @ClassName OfficialButtonInteractionEvent
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.event.events
 */
@Getter
@Slf4j
public class OfficialButtonInteractionEvent extends OfficialInteractionEvents implements Cancellable {
    private final int chatType;
    private final Data data;
    private final String groupOpenId;
    private final String userOpenId;
    private boolean cancelled;

    public OfficialButtonInteractionEvent(String applicationId, String eventId, int chatType, Data data, String groupOpenId, String userOpenId, String id, String scene, String timestamp, int type, int version) {
        super(applicationId, eventId, id, scene, timestamp, type, version);
        this.chatType = chatType;
        this.data = data;
        this.groupOpenId = groupOpenId;
        this.userOpenId = userOpenId;
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
        if (this.shouldIgnore()) {
            status = AnswerCode.IGNORED;
        }
        String url = AtriProvider.getInstance().getSettings().getQqApiBaseUrl() + "/interactions/" + id;
        String requestJson = "{\"code\":" + status.getCode() + "}";
        var response = HttpService.putJsonDetailed(url, requestJson,
                "Authorization", "QQBot " + AtriProvider.getInstance().getChatService().getTokenManager().getAccessToken());
        boolean success = response.status() >= 200 && response.status() < 300;
        try {
            ThreadManager.execute(() -> {
                String scene = "交互回应";
                String traceId = AtriProvider.getInstance().getLogRecorder().recordSend(scene, "PUT", url, requestJson);
                if (success) {
                    AtriProvider.getInstance().getLogRecorder().recordResponse(traceId, scene, "PUT", url, requestJson,
                            response.status(), response.body());
                } else {
                    String reason = response.status() == 0 ? "请求异常: " + response.body()
                            : "接口返回状态异常 " + response.status();
                    AtriProvider.getInstance().getLogRecorder().recordError(traceId, scene, "PUT", url, requestJson,
                            response.status(), response.body(), reason);
                }
            });
        } catch (RejectedExecutionException e) {
            log.warn("交互回应日志任务被拒绝, interactionId: {}, status: {}", id, response.status(), e);
        }
        return success;
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
                return GroupChat.replyMessage(this.groupOpenId, RT.event(this.eventId), this.userOpenId, markdown);
            }
            return GroupChat.replyMessage(this.groupOpenId, RT.event(this.eventId), markdown);
        } else if (this.chatType == 2) {
            return C2CChat.replyMessage(this.userOpenId, RT.event(this.eventId), markdown);
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
                return GroupChat.replyMessage(this.groupOpenId, RT.event(this.eventId), this.userOpenId, markdown, keyboard);
            }
            return GroupChat.replyMessage(this.groupOpenId, RT.event(this.eventId), markdown, keyboard);
        } else if (this.chatType == 2) {
            return C2CChat.replyMessage(this.userOpenId, RT.event(this.eventId), markdown, keyboard);
        } else {
            throw new UnknownButtonInteractionScene(this.chatType, this.scene);
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public String replyMessage(String text) {
        if (this.chatType == 1) {
            return GroupChat.replyMessage(this.groupOpenId, RT.event(this.eventId), text);
        } else if (this.chatType == 2) {
            return C2CChat.replyMessage(this.userOpenId, RT.event(this.eventId), text);
        } else {
            throw new UnknownButtonInteractionScene(this.chatType, this.scene);
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public String replyMessage(ImageComponent image) {
        if (this.chatType == 1) {
            return GroupChat.replyMessage(this.groupOpenId, RT.event(this.eventId), image);
        } else if (this.chatType == 2) {
            return C2CChat.replyMessage(this.userOpenId, RT.event(this.eventId), image);
        } else {
            throw new UnknownButtonInteractionScene(this.chatType, this.scene);
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(String text) {
        if (this.chatType == 1) {
            return GroupChat.sendMessage(this.groupOpenId, text);
        } else if (this.chatType == 2) {
            return C2CChat.sendMessage(this.userOpenId, text);
        } else {
            throw new UnknownButtonInteractionScene(this.chatType, this.scene);
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(Markdown content) {
        if (this.chatType == 1) {
            return GroupChat.sendMessage(this.groupOpenId, content);
        } else if (this.chatType == 2) {
            return C2CChat.sendMessage(this.userOpenId, content);
        } else {
            throw new UnknownButtonInteractionScene(this.chatType, this.scene);
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(Markdown content, Object keyboard) {
        if (this.chatType == 1) {
            return GroupChat.sendMessage(this.groupOpenId, content, keyboard);
        } else if (this.chatType == 2) {
            return C2CChat.sendMessage(this.userOpenId, content, keyboard);
        } else {
            throw new UnknownButtonInteractionScene(this.chatType, this.scene);
        }
    }

    public boolean shouldIgnore() {
        if (this.groupOpenId != null) {
            if (AtriProvider.getInstance().getAccessService().isGroupBlacklisted(this.groupOpenId)) {
                return true;
            }
        }
        return AtriProvider.getInstance().getAccessService().isBlocked(this.userOpenId) || AtriProvider.getInstance().getAccessService().isIgnored(this.userOpenId);
    }
}
