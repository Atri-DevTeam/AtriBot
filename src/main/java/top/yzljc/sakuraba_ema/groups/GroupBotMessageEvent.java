package top.yzljc.sakuraba_ema.groups;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.atribot.chat.official.Ark23;
import top.yzljc.atribot.chat.official.Markdown;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Parsed group message delivered only to its owning additional bot instance. */
public record GroupBotMessageEvent(
        GroupBotClient client,
        String eventType,
        String eventId,
        String groupOpenId,
        String messageId,
        String content,
        String memberOpenId,
        String username,
        String timestamp,
        boolean bot,
        boolean atBot,
        JsonNode rawData
) {

    public GroupBotMessageEvent {
        client = Objects.requireNonNull(client, "client");
        rawData = Objects.requireNonNull(rawData, "rawData").deepCopy();
    }

    public CompletableFuture<String> reply(String text) {
        return client.getChat().replyMessage(groupOpenId, messageId, text);
    }

    public CompletableFuture<String> reply(Markdown markdown) {
        return client.getChat().replyMessage(groupOpenId, messageId, markdown);
    }

    public CompletableFuture<String> reply(Markdown markdown, Object keyboard) {
        return client.getChat().replyMessage(groupOpenId, messageId, markdown, keyboard);
    }

    public CompletableFuture<String> reply(Ark23 ark) {
        return client.getChat().replyMessage(groupOpenId, messageId, ark);
    }

    public CompletableFuture<String> send(String text) {
        return client.getChat().sendMessage(groupOpenId, text);
    }

    public CompletableFuture<String> send(Markdown markdown) {
        return client.getChat().sendMessage(groupOpenId, markdown);
    }

    public CompletableFuture<String> send(Markdown markdown, Object keyboard) {
        return client.getChat().sendMessage(groupOpenId, markdown, keyboard);
    }

    public CompletableFuture<String> send(Ark23 ark) {
        return client.getChat().sendMessage(groupOpenId, ark);
    }

    public boolean recall(String targetMessageId) {
        return client.getChat().recallMessage(groupOpenId, targetMessageId);
    }
}
