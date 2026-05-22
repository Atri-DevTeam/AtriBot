package top.yzljc.qqbot.event.impl;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import top.yzljc.qqbot.AtriBot;
import top.yzljc.qqbot.event.Event;

import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName OfficialGroupMessageCreateEvent
 * @Created_at 2026/05/22
 * @Project AtriBot
 * @Package top.yzljc.qqbot.event.impl
 */
@Getter
public class OfficialGroupMessageCreateEvent extends Event {
    private final Author author;
    private final String content;
    private final String groupId;
    private final String groupOpenId;
    private final String messageId;
    private final List<Mention> mentions;
    private final MessageScene messageScene;
    private final Integer messageType;
    private final String timestamp;
    private final JsonNode attachments;
    private final JsonNode arkData;
    private final boolean isAtBotMessage;

    public OfficialGroupMessageCreateEvent(Author author, String content, String groupId, String groupOpenId, String messageId,
                                           List<Mention> mentions, MessageScene messageScene, Integer messageType, String timestamp,
                                           JsonNode attachments, JsonNode arkData) {
        this.author = author;
        this.content = content;
        this.groupId = groupId;
        this.groupOpenId = groupOpenId;
        this.messageId = messageId;
        this.mentions = mentions;
        this.messageScene = messageScene;
        this.messageType = messageType;
        this.timestamp = timestamp;
        this.isAtBotMessage = mentions.stream().anyMatch(Mention::isYou);
        this.attachments = attachments;
        this.arkData = arkData;
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Author {
        private boolean bot;
        private String id;
        private String memberOpenId;
        private String unionOpenId;
        private String username;
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Mention {
        private boolean bot;
        private String id;
        private boolean isYou;
        private String memberOpenId;
        private String scope;
        private String username;
    }

    @Getter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MessageScene {
        private List<String> ext;
        private String source;
    }

    @SuppressWarnings("UnusedReturnValue")
    public String replyText(String text) {
        return AtriBot.getInstance().getMessageService().replyGroupTextMessage(this.groupOpenId, this.messageId, text);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String replyMarkdown(String markdownContent) {
        return AtriBot.getInstance().getMessageService().replyGroupMarkdownMessage(this.groupOpenId, this.author.unionOpenId, this.messageId, markdownContent);
    }

    @SuppressWarnings("UnusedReturnValue")
    public String replyMarkdown(String markdownContent, Object keyboard) {
        return AtriBot.getInstance().getMessageService().replyGroupMarkdownWithKeyboard(this.groupOpenId, this.author.unionOpenId, this.messageId, markdownContent, keyboard);
    }
}