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
import top.yzljc.atricore.chat.official.button.Keyboard;
import top.yzljc.atricore.chat.ImageComponent;
import top.yzljc.atricore.event.Cancellable;
import top.yzljc.atricore.event.impl.AnswerCode;
import top.yzljc.atricore.event.impl.UnknownButtonInteractionScene;
import top.yzljc.atricore.service.request.HttpService;
import top.yzljc.atricore.service.runtime.ThreadManager;

import java.util.concurrent.RejectedExecutionException;

/**
 * 收到 {@code INTERACTION_CREATE} 中的按钮点击交互时触发
 * <p>{@code replyMessage} 系列方法使用当前事件 ID 发送被动回复，{@code sendMessage} 系列方法发送主动消息。
 * 群聊消息发送到交互所在群，单聊消息发送给点击按钮的用户。</p>
 * <p>{@link #answer(AnswerCode)} 用于回执交互处理结果</p>
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
    /**
     * 聊天类型：1 为群聊，2 为单聊
     */
    private final int chatType;
    private final Data data;
    private final String groupOpenId;
    private final String userOpenId;
    /**
     * QQ 开放平台绑定的互联 ID，仅私聊场景下存在，未携带时为 {@code null}
     */
    private final String unionOpenId;
    private boolean cancelled;

    public OfficialButtonInteractionEvent(String applicationId, String eventId, int chatType, Data data, String groupOpenId, String userOpenId, String unionOpenId, String id, String scene, String timestamp, int type, int version) {
        super(applicationId, eventId, id, scene, timestamp, type, version);
        this.chatType = chatType;
        this.data = data;
        this.groupOpenId = groupOpenId;
        this.userOpenId = userOpenId;
        this.unionOpenId = unionOpenId;
    }

    /**
     * 设置交互的取消状态，取消后 {@link #answer(AnswerCode)} 将优先按失败状态回执
     * <p>若同时满足 {@link #shouldIgnore()}，则最终按忽略状态回执。</p>
     *
     * @param cancel 是否取消交互
     */
    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    /**
     * 按钮交互数据
     */
    @Getter
    @AllArgsConstructor
    public static class Data {
        private final JsonNode resolved;
        private final int type;
    }

    /**
     * 向平台回执按钮交互的处理结果
     * <p>交互已取消时使用 {@link AnswerCode#FAIL}，应忽略时使用 {@link AnswerCode#IGNORED}，后者优先。</p>
     *
     * @param status 交互处理状态
     * @return 平台返回 2xx 状态码时为 {@code true}，否则为 {@code false}
     */
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

    /**
     * 获取按钮携带的数据
     *
     * @return 按钮的 {@code button_data}，非按钮交互或字段缺失时返回 {@code "missing_data"}
     */
    public String getButtonValue() {
        if (type == 11 && data.resolved.has("button_data")) {
            return data.resolved.get("button_data").asText();
        } else {
            return "missing_data";
        }
    }

    /**
     * 获取被点击按钮的 ID
     *
     * @return 按钮的 {@code button_id}，非按钮交互或字段缺失时返回 {@code "missing_data"}
     */
    public String getButtonId() {
        if (type == 11 && data.resolved.has("button_id")) {
            return data.resolved.get("button_id").asText();
        } else {
            return "missing_data";
        }
    }

    /**
     * 被动消息回复 - Markdown 消息（默认不自动 @）
     * <p>按钮请用 {@link Markdown#setKeyboard(Keyboard)} 设置；群聊中如需自动 @ 点击者，请用 {@link #replyMessage(Markdown, boolean)}。</p>
     *
     * @param markdown Markdown 消息内容
     * @return 平台返回的消息 ID，发送失败时返回 {@code null}
     * @throws UnknownButtonInteractionScene 聊天类型不是群聊或单聊时抛出
     */
    @SuppressWarnings("UnusedReturnValue")
    public String replyMessage(Markdown markdown) {
        return replyMessage(markdown, false);
    }

    /**
     * 被动消息回复 - Markdown 消息（可控制自动 @）
     * <p>按钮请用 {@link Markdown#setKeyboard(Keyboard)} 设置。</p>
     *
     * @param markdown Markdown 消息内容
     * @param at       是否自动 @ 点击按钮的用户，仅群聊生效，单聊忽略此参数
     * @return 平台返回的消息 ID，发送失败时返回 {@code null}
     * @throws UnknownButtonInteractionScene 聊天类型不是群聊或单聊时抛出
     */
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

    /**
     * 被动消息回复 - Markdown 消息 + 自定义键盘（默认不自动 @，已弃用）
     *
     * @param markdown Markdown 消息内容
     * @param keyboard 自定义键盘内容
     * @return 平台返回的消息 ID，发送失败时返回 {@code null}
     * @throws UnknownButtonInteractionScene 聊天类型不是群聊或单聊时抛出
     * @deprecated 请用 {@link Markdown#setKeyboard(Keyboard)} 设置键盘，然后使用 {@link #replyMessage(Markdown)} 发送
     */
    @Deprecated
    @SuppressWarnings("UnusedReturnValue")
    public String replyMessage(Markdown markdown, Object keyboard) {
        return replyMessage(markdown, keyboard, false);
    }

    /**
     * 被动消息回复 - Markdown 消息 + 自定义键盘（可控制自动 @，已弃用）
     *
     * @param markdown Markdown 消息内容
     * @param keyboard 自定义键盘内容
     * @param at       是否自动 @ 点击按钮的用户，仅群聊生效，单聊忽略此参数
     * @return 平台返回的消息 ID，发送失败时返回 {@code null}
     * @throws UnknownButtonInteractionScene 聊天类型不是群聊或单聊时抛出
     * @deprecated 请用 {@link Markdown#setKeyboard(Keyboard)} 设置键盘，然后使用 {@link #replyMessage(Markdown, boolean)} 发送
     */
    @Deprecated
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

    /**
     * 被动消息回复 - 纯文本消息
     *
     * @param text 文本消息内容
     * @return 平台返回的消息 ID，发送失败时返回 {@code null}
     * @throws UnknownButtonInteractionScene 聊天类型不是群聊或单聊时抛出
     */
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

    /**
     * 被动消息回复 - 图片消息
     *
     * @param image 图片组件
     * @return 平台返回的消息 ID，上传或发送失败时返回 {@code null}
     * @throws UnknownButtonInteractionScene 聊天类型不是群聊或单聊时抛出
     */
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

    /**
     * 主动消息发送 - 纯文本消息
     *
     * @param text 文本消息内容
     * @return 平台返回的消息 ID，发送失败时返回 {@code null}
     * @throws UnknownButtonInteractionScene 聊天类型不是群聊或单聊时抛出
     */
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

    /**
     * 主动消息发送 - Markdown 消息
     * <p>按钮请用 {@link Markdown#setKeyboard(Keyboard)} 设置。</p>
     *
     * @param content Markdown 消息内容
     * @return 平台返回的消息 ID，发送失败时返回 {@code null}
     * @throws UnknownButtonInteractionScene 聊天类型不是群聊或单聊时抛出
     */
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

    /**
     * 主动消息发送 - 图片消息
     *
     * @param image 图片组件
     * @return 平台返回的消息 ID，上传或发送失败时返回 {@code null}
     * @throws UnknownButtonInteractionScene 聊天类型不是群聊或单聊时抛出
     */
    @SuppressWarnings("UnusedReturnValue")
    public String sendMessage(ImageComponent image) {
        if (this.chatType == 1) {
            return GroupChat.sendMessage(this.groupOpenId, image);
        } else if (this.chatType == 2) {
            return C2CChat.sendMessage(this.userOpenId, image);
        } else {
            throw new UnknownButtonInteractionScene(this.chatType, this.scene);
        }
    }

    /**
     * 主动消息发送 - Markdown 消息 + 自定义键盘（已弃用）
     *
     * @param content  Markdown 消息内容
     * @param keyboard 自定义键盘内容
     * @return 平台返回的消息 ID，发送失败时返回 {@code null}
     * @throws UnknownButtonInteractionScene 聊天类型不是群聊或单聊时抛出
     * @deprecated 请用 {@link Markdown#setKeyboard(Keyboard)} 设置键盘，然后使用 {@link #sendMessage(Markdown)} 发送
     */
    @Deprecated
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

    /**
     * 判断是否应忽略当前交互
     *
     * @return 交互所在群在黑名单中，或点击按钮的用户被屏蔽、被忽略时返回 {@code true}
     */
    public boolean shouldIgnore() {
        if (this.groupOpenId != null) {
            if (AtriProvider.getInstance().getAccessService().isGroupBlacklisted(this.groupOpenId)) {
                return true;
            }
        }
        return AtriProvider.getInstance().getAccessService().isBlocked(this.userOpenId) || AtriProvider.getInstance().getAccessService().isIgnored(this.userOpenId);
    }
}
