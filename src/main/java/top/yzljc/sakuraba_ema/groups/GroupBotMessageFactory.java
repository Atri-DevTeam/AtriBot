package top.yzljc.sakuraba_ema.groups;

import top.yzljc.atribot.chat.official.Ark23;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.MessageBody;
import top.yzljc.atribot.chat.official.media.GroupMessageType;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/** Builds only group-message payloads for an isolated bot instance. */
final class GroupBotMessageFactory {

    private final Function<String, Integer> msgSeqProvider;

    GroupBotMessageFactory(Function<String, Integer> msgSeqProvider) {
        this.msgSeqProvider = msgSeqProvider;
    }

    MessageBody text(String content) {
        return MessageBody.builder()
                .msgType(GroupMessageType.TEXT.getValue())
                .content(content)
                .build();
    }

    MessageBody markdown(Markdown markdown, Object keyboard, String msgId, String eventId) {
        MessageBody.MessageBodyBuilder builder = MessageBody.builder()
                .msgType(GroupMessageType.MARKDOWN.getValue())
                .markdown(markdownPayload(markdown.getText()));
        attachKeyboard(builder, keyboard);
        attachReply(builder, msgId, eventId);
        return builder.build();
    }

    MessageBody ark(Ark23 ark, String msgId, String eventId) {
        MessageBody.MessageBodyBuilder builder = MessageBody.builder()
                .msgType(GroupMessageType.ARK.getValue())
                .ark(ark.toPayload());
        attachReply(builder, msgId, eventId);
        return builder.build();
    }

    MessageBody replyText(String msgId, String content) {
        return MessageBody.builder()
                .msgType(GroupMessageType.TEXT.getValue())
                .content(content)
                .msgId(msgId)
                .msgSeq(msgSeqProvider.apply(msgId))
                .build();
    }

    MessageBody eventText(String eventId, String content) {
        return MessageBody.builder()
                .msgType(GroupMessageType.TEXT.getValue())
                .content(content)
                .eventId(eventId)
                .build();
    }

    private void attachReply(MessageBody.MessageBodyBuilder builder, String msgId, String eventId) {
        if (msgId != null && !msgId.isBlank()) {
            builder.msgId(msgId).msgSeq(msgSeqProvider.apply(msgId));
        }
        if (eventId != null && !eventId.isBlank()) {
            builder.eventId(eventId);
        }
    }

    private static void attachKeyboard(MessageBody.MessageBodyBuilder builder, Object keyboard) {
        if (keyboard == null) {
            return;
        }
        if (keyboard instanceof Map<?, ?> map && map.containsKey("keyboard")) {
            builder.promptKeyboard(keyboard);
        } else {
            builder.keyboard(keyboard);
        }
    }

    private static Map<String, Object> markdownPayload(String content) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("content", content);
        return payload;
    }
}
