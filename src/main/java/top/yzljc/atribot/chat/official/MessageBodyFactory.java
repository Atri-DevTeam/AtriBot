package top.yzljc.atribot.chat.official;

import top.yzljc.atribot.chat.official.media.GroupMessageType;

import java.util.HashMap;
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

    public MessageBody textRef(String text, String refIdx) {
        return MessageBody.builder()
                .msgType(GroupMessageType.TEXT.getValue())
                .content(text)
                .messageReference(Map.of("message_id", refIdx))
                .build();
    }

    public MessageBody markdown(Markdown markdown) {
        return markdown(markdown.getText(), null, null, null);
    }

    public MessageBody markdown(Markdown markdown, Object keyboard) {
        return markdown(markdown.getText(), keyboard, null, null);
    }

    public MessageBody markdown(String markdownContent, Object keyboard, String msgId, String eventId) {
        MessageBody.MessageBodyBuilder builder = MessageBody.builder()
                .msgType(GroupMessageType.MARKDOWN.getValue())
                .markdown(buildMarkdown(markdownContent));
        if (keyboard != null) {
            if (hasField(keyboard, "keyboard")) {
                builder.promptKeyboard(keyboard);
            } else {
                builder.keyboard(keyboard);
            }
        }
        if (msgId != null) {
            builder.msgId(msgId).msgSeq(msgSeqProvider.apply(msgId));
        }
        if (eventId != null) {
            builder.eventId(eventId);
        }
        return builder.build();
    }

    public MessageBody replyText(String msgId, String text) {
        return MessageBody.builder()
                .msgType(GroupMessageType.TEXT.getValue())
                .msgId(msgId)
                .msgSeq(msgSeqProvider.apply(msgId))
                .content(text)
                .build();
    }

    public MessageBody eventText(String eventId, String text) {
        return MessageBody.builder()
                .msgType(GroupMessageType.TEXT.getValue())
                .eventId(eventId)
                .content(text)
                .build();
    }

    public MessageBody media(String fileInfo, String msgId) {
        return media(fileInfo, msgId, null);
    }

    public MessageBody media(String fileInfo, String msgId, String eventId) {
        return media(fileInfo, msgId, eventId, null);
    }

    public MessageBody media(String fileInfo, String msgId, String eventId, String content) {
        Map<String, Object> mediaObj = new HashMap<>();
        mediaObj.put("file_info", fileInfo);

        MessageBody.MessageBodyBuilder builder = MessageBody.builder()
                .msgType(GroupMessageType.MEDIA.getValue())
                .media(mediaObj);
        if (content != null) {
            builder.content(content);
        }
        if (msgId != null) {
            builder.msgId(msgId).msgSeq(msgSeqProvider.apply(msgId));
        }
        if (eventId != null) {
            builder.eventId(eventId);
        }
        return builder.build();
    }

    public String atMarkdown(String userOpenId, Markdown markdown) {
        return Markdown.at(userOpenId) + "\n" + markdown.getText();
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
