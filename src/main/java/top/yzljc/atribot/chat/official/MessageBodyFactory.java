package top.yzljc.atribot.chat.official;

import top.yzljc.atribot.chat.official.media.GroupMessageType;

import java.util.HashMap;
import java.util.Objects;
import java.util.Map;
import java.util.function.Function;

final class MessageBodyFactory {

    private static final String STREAM_CONTENT_TYPE_MARKDOWN = "markdown";

    private final Function<String, Integer> msgSeqProvider;

    public MessageBodyFactory(Function<String, Integer> msgSeqProvider) {
        this.msgSeqProvider = msgSeqProvider;
    }

    public MessageBody text(String text) {
        return MessageBody.builder()
                .msgType(GroupMessageType.TEXT.getValue())
                .content(text)
                .build();
    }

    public MessageBody ark23(Ark23 ark) {
        return ark23(ark, null);
    }

    public MessageBody ark23(Ark23 ark, RT rt) {
        if (ark == null) throw new IllegalArgumentException("Ark23 must not be null");
        return builder(rt, true)
                .msgType(GroupMessageType.ARK.getValue())
                .ark(ark.toPayload())
                .build();
    }

    public MessageBody textRef(String text, String refIdx) {
        return MessageBody.builder()
                .msgType(GroupMessageType.TEXT.getValue())
                .content(text)
                .messageReference(Map.of("message_id", refIdx))
                .build();
    }

    public MessageBody markdown(Markdown markdown) {
        return markdown(markdown, null, null);
    }

    public MessageBody markdown(Markdown markdown, Object keyboard) {
        return markdown(markdown, keyboard, null);
    }

    public MessageBody markdown(Markdown markdown, Object keyboard, RT rt) {
        MessageBody.MessageBodyBuilder builder = builder(rt, true)
                .msgType(GroupMessageType.MARKDOWN.getValue())
                .markdown(markdown);
        if (keyboard != null) {
            if (hasField(keyboard, "keyboard")) {
                builder.promptKeyboard(keyboard);
            } else {
                builder.keyboard(keyboard);
            }
        }
        return builder.build();
    }

    public MessageBody replyText(RT rt, String text) {
        Objects.requireNonNull(rt, "被动消息中msg_id和event_id不能同时为空");
        return builder(rt, true)
                .msgType(GroupMessageType.TEXT.getValue())
                .content(text)
                .build();
    }

    /**
     * 为消息添加独立的引用对象，保留回复来源、序号及其他字段
     *
     * @param body   原始消息体，可为 null
     * @param refIdx 被引用消息的索引 ID，null 表示不添加引用
     * @return 添加引用后的消息体；空消息、维护提示或无需引用时返回原值
     */
    public MessageBody withReference(MessageBody body, String refIdx) {
        if (body == null || refIdx == null || body.isMaintenanceReply()) {
            return body;
        }
        return body.toBuilder().messageReference(Map.of("message_id", refIdx)).build();
    }

    /**
     * 构造可引用指定消息的 Markdown 消息体
     *
     * @param markdown Markdown 回复内容
     * @param keyboard 键盘按钮对象，无键盘时传入 null
     * @param rt       消息或事件回复来源，主动消息传入 null
     * @param refIdx   被引用消息的索引 ID，null 表示不引用
     * @return Markdown 消息体
     */
    public MessageBody markdown(Markdown markdown, Object keyboard, RT rt, String refIdx) {
        return withReference(markdown(markdown, keyboard, rt), refIdx);
    }

    /**
     * 将普通消息体转换为单聊召回消息，清除被动回复来源、回复序号和引用
     *
     * @param body 原始消息体，可为 null
     * @return 设置 is_wakeup 的独立消息体，原始消息为空时返回 null
     */
    public MessageBody wakeup(MessageBody body) {
        return body == null ? null : body.toBuilder()
                .isWakeup(true)
                .msgId(null)
                .eventId(null)
                .msgSeq(null)
                .messageReference(null)
                .maintenanceReply(false)
                .build();
    }

    /**
     * 构造单聊正在输入通知，不携带回复来源或引用
     *
     * @param inputSecond 输入状态持续秒数，必须大于 0
     * @return 输入状态通知消息体
     */
    public MessageBody inputNotify(int inputSecond) {
        return MessageBody.builder()
                .msgType(6)
                .inputNotify(Map.of("input_type", 1, "input_second", inputSecond))
                .build();
    }

    public MessageBody replyTextRef(RT rt, String text, String refIdx) {
        Objects.requireNonNull(rt, "被动消息中msg_id和event_id不能同时为空");
        return builder(rt, true)
                .msgType(GroupMessageType.TEXT.getValue())
                .messageReference(Map.of("message_id", refIdx))
                .content(text)
                .build();
    }

    public MessageBody media(String fileInfo, RT rt) {
        return media(fileInfo, rt, null);
    }

    public MessageBody media(String fileInfo, RT rt, String content) {
        Map<String, Object> mediaObj = new HashMap<>();
        mediaObj.put("file_info", fileInfo);

        MessageBody.MessageBodyBuilder builder = builder(rt, true)
                .msgType(GroupMessageType.MEDIA.getValue())
                .media(mediaObj);
        if (content != null) {
            builder.content(content);
        }
        return builder.build();
    }

    public Markdown atMarkdown(String userOpenId, Markdown markdown) {
        return markdown.withText(Markdown.at(userOpenId) + "\n\n" + markdown.getText());
    }

    public Map<String, Object> buildMarkdown(String markdownContent) {
        Map<String, Object> markdownObj = new HashMap<>();
        markdownObj.put("content", markdownContent);
        return markdownObj;
    }

    public MessageBody streamRequestToMessageBody(Map<String, Object> request) {
        String msgId = stringValue(request.get("msg_id"));
        String eventId = stringValue(request.get("event_id"));
        Integer msgSeq = integerValue(request.get("msg_seq"));
        Boolean isWakeup = booleanValue(request.get("is_wakeup"));
        String contentType = stringValue(request.get("content_type"));
        String contentRaw = stringValue(request.get("content_raw"));

        MessageBody.MessageBodyBuilder builder = MessageBody.builder()
                .msgId(msgId)
                .eventId(eventId)
                .msgSeq(msgSeq)
                .isWakeup(isWakeup);

        if (STREAM_CONTENT_TYPE_MARKDOWN.equalsIgnoreCase(contentType)) {
            return builder
                    .msgType(GroupMessageType.MARKDOWN.getValue())
                    .markdown(buildMarkdown(contentRaw))
                    .build();
        }

        return builder
                .msgType(GroupMessageType.TEXT.getValue())
                .content(contentRaw)
                .build();
    }

    public MessageBody guildImage(String imageUrl, RT rt, String text) {
        return builder(rt, false)
                .image(imageUrl)
                .content(text)
                .build();
    }

    /**
     * 创建携带回复来源的消息构造器，并按需在构造阶段分配消息回复序号
     *
     * @param rt           消息或事件回复来源，主动消息传入 null
     * @param withSequence 是否为消息来源分配序号，频道图片传入 false；事件来源不分配序号
     * @return 已设置回复来源及相应序号的消息构造器
     */
    private MessageBody.MessageBodyBuilder builder(RT rt, boolean withSequence) {
        var builder = MessageBody.builder();
        switch (rt) {
            case null -> { }
            case RT.Message message -> {
                builder.msgId(message.id());
                if (withSequence) builder.msgSeq(msgSeqProvider.apply(message.id()));
            }
            case RT.Event event -> builder.eventId(event.id());
        }
        return builder;
    }

    /**
     * 从已有协议消息体解析回复来源，业务层应直接使用 RT.message(id) 或 RT.event(id)
     *
     * @param msgId   消息 ID，对应 msg_id，可为 null 或空白
     * @param eventId 事件 ID，对应 event_id，可为 null 或空白
     * @return 消息或事件回复来源，两种 ID 都为空时返回 null，表示主动消息
     * @throws IllegalArgumentException 两种 ID 同时非空时抛出
     */
    static RT sourceOf(String msgId, String eventId) {
        boolean message = !ChatService.isBlank(msgId);
        boolean event = !ChatService.isBlank(eventId);
        if (message && event) {
            throw new IllegalArgumentException("msg_id 与 event_id 不能同时设置");
        }
        return message ? RT.message(msgId) : event ? RT.event(eventId) : null;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer integerValue(Object value) {
        if (value instanceof Integer i) {
            return i;
        }
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private Boolean booleanValue(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s && !s.isBlank()) {
            return Boolean.parseBoolean(s);
        }
        return null;
    }

    private static boolean hasField(Object obj, String fieldName) {
        if (obj instanceof Map<?, ?> map) {
            return map.containsKey(fieldName);
        }
        return false;
    }
}
