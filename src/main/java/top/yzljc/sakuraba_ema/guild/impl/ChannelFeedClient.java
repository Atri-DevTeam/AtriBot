package top.yzljc.sakuraba_ema.guild.impl;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.sakuraba_ema.ChannelCliClient;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class ChannelFeedClient {

    private final ChannelCliClient client;

    public ChannelFeedClient(ChannelCliClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    public ChannelCliResult execute(String action, JsonNode parameters) {
        return client.execute("feed", action, parameters);
    }

    public ChannelCliResult execute(String action, JsonNode parameters, ChannelCliOptions options) {
        return client.execute("feed", action, parameters, options);
    }

    public CompletableFuture<ChannelCliResult> executeAsync(String action, JsonNode parameters) {
        return client.executeAsync("feed", action, parameters);
    }

    public CompletableFuture<ChannelCliResult> executeAsync(String action, JsonNode parameters,
                                                            ChannelCliOptions options) {
        return client.executeAsync("feed", action, parameters, options);
    }

    public ChannelCliResult getGuildFeeds(JsonNode parameters) {
        return execute("get-guild-feeds", parameters);
    }

    public ChannelCliResult getChannelTimelineFeeds(JsonNode parameters) {
        return execute("get-channel-timeline-feeds", parameters);
    }

    public ChannelCliResult getFeedDetail(JsonNode parameters) {
        return execute("get-feed-detail", parameters);
    }

    public ChannelCliResult getFeedComments(JsonNode parameters) {
        return execute("get-feed-comments", parameters);
    }

    public ChannelCliResult searchGuildFeeds(JsonNode parameters) {
        return execute("search-guild-feeds", parameters);
    }

    public ChannelCliResult getFeedShareUrl(JsonNode parameters) {
        return execute("get-feed-share-url", parameters);
    }

    public ChannelCliResult getNotices(JsonNode parameters) {
        return execute("get-notices", parameters);
    }

    public ChannelCliResult getNextPageReplies(JsonNode parameters) {
        return execute("get-next-page-replies", parameters);
    }

    public ChannelCliResult publishFeed(JsonNode parameters) {
        return execute("publish-feed", parameters);
    }

    public ChannelCliResult publishFeed(JsonNode parameters, boolean confirmed) {
        return execute("publish-feed", parameters, optionsForConfirmation(confirmed));
    }

    public ChannelCliResult delFeed(JsonNode parameters, boolean confirmed) {
        return execute("del-feed", parameters, optionsForConfirmation(confirmed));
    }

    public ChannelCliResult doComment(JsonNode parameters) {
        return execute("do-comment", parameters);
    }

    public ChannelCliResult doComment(JsonNode parameters, boolean confirmed) {
        return execute("do-comment", parameters, optionsForConfirmation(confirmed));
    }

    public ChannelCliResult doReply(JsonNode parameters) {
        return execute("do-reply", parameters);
    }

    public ChannelCliResult doReply(JsonNode parameters, boolean confirmed) {
        return execute("do-reply", parameters, optionsForConfirmation(confirmed));
    }

    public ChannelCliResult doLike(JsonNode parameters) {
        return execute("do-like", parameters);
    }

    public ChannelCliResult doFeedPrefer(JsonNode parameters) {
        return execute("do-feed-prefer", parameters);
    }

    public ChannelCliResult alterFeed(JsonNode parameters) {
        return execute("alter-feed", parameters);
    }

    public ChannelCliResult topFeed(JsonNode parameters) {
        return execute("top-feed", parameters);
    }

    public ChannelCliResult setFeedEssence(JsonNode parameters) {
        return execute("set-feed-essence", parameters);
    }

    public ChannelCliResult pushEssenceFeed(JsonNode parameters) {
        return execute("push-essence-feed", parameters);
    }

    public ChannelCliResult moveFeed(JsonNode parameters) {
        return execute("move-feed", parameters);
    }

    public ChannelCliResult quickPublish(JsonNode parameters) {
        return execute("quick-publish", parameters);
    }

    public ChannelCliResult searchAndComment(JsonNode parameters) {
        return execute("search-and-comment", parameters);
    }

    public ChannelCliResult deleteAndMute(JsonNode parameters, boolean confirmed) {
        return execute("delete-and-mute", parameters, optionsForConfirmation(confirmed));
    }

    public ChannelCliResult latestFeedsDetail(JsonNode parameters) {
        return execute("latest-feeds-detail", parameters);
    }

    public ChannelCliResult hotFeedsDetail(JsonNode parameters) {
        return execute("hot-feeds-detail", parameters);
    }

    private static ChannelCliOptions optionsForConfirmation(boolean confirmed) {
        return confirmed ? ChannelCliOptions.CONFIRMED : ChannelCliOptions.DEFAULT;
    }

}
