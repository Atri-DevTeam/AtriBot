package top.yzljc.sakuraba_ema.groups;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.atribot.chat.official.Ark23;
import top.yzljc.atribot.chat.official.Markdown;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Group button callback delivered only to its owning additional bot instance. */
public record GroupBotButtonInteractionEvent(
        GroupBotClient client,
        String eventId,
        String interactionId,
        String groupOpenId,
        String memberOpenId,
        String buttonId,
        String buttonValue,
        String timestamp,
        JsonNode rawData
) {

    public GroupBotButtonInteractionEvent {
        client = Objects.requireNonNull(client, "client");
        rawData = Objects.requireNonNull(rawData, "rawData").deepCopy();
    }

    public CompletableFuture<String> reply(String text) {
        return client.getChat().replyEventMessage(groupOpenId, eventId, text);
    }

    public CompletableFuture<String> reply(Markdown markdown) {
        return client.getChat().replyEventMessage(groupOpenId, eventId, markdown);
    }

    public CompletableFuture<String> reply(Markdown markdown, Object keyboard) {
        return client.getChat().replyEventMessage(groupOpenId, eventId, markdown, keyboard);
    }

    public CompletableFuture<String> reply(Ark23 ark) {
        return client.getChat().replyEventMessage(groupOpenId, eventId, ark);
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

    public boolean recall(String messageId) {
        return client.getChat().recallMessage(groupOpenId, messageId);
    }
}
