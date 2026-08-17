package top.yzljc.sakuraba_ema.manager;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.sakuraba_ema.ChannelCliClient;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliResult;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliOptions;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Explicit Java entry points for every command in the CLI manage domain.
 * Parameters use the field names returned by {@code schema manage.<action>}.
 */
public final class ChannelManageClient {

    private final ChannelCliClient client;

    public ChannelManageClient(ChannelCliClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    public ChannelCliResult execute(String action, JsonNode parameters) {
        return client.execute("manage", action, parameters);
    }

    public ChannelCliResult execute(String action, JsonNode parameters, ChannelCliOptions options) {
        return client.execute("manage", action, parameters, options);
    }

    public CompletableFuture<ChannelCliResult> executeAsync(String action, JsonNode parameters) {
        return client.executeAsync("manage", action, parameters);
    }

    public CompletableFuture<ChannelCliResult> executeAsync(String action, JsonNode parameters,
                                                            ChannelCliOptions options) {
        return client.executeAsync("manage", action, parameters, options);
    }

    public ChannelCliResult getGuildInfo(JsonNode parameters) {
        return execute("get-guild-info", parameters);
    }

    public ChannelCliResult getMyJoinGuildInfo(JsonNode parameters) {
        return execute("get-my-join-guild-info", parameters);
    }

    public ChannelCliResult getUserInfo(JsonNode parameters) {
        return execute("get-user-info", parameters);
    }

    public ChannelCliResult getGuildMemberList(JsonNode parameters) {
        return execute("get-guild-member-list", parameters);
    }

    public ChannelCliResult guildMemberSearch(JsonNode parameters) {
        return execute("guild-member-search", parameters);
    }

    public ChannelCliResult getGuildChannelList(JsonNode parameters) {
        return execute("get-guild-channel-list", parameters);
    }

    public ChannelCliResult searchGuildContent(JsonNode parameters) {
        return execute("search-guild-content", parameters);
    }

    public ChannelCliResult getJoinGuildSetting(JsonNode parameters) {
        return execute("get-join-guild-setting", parameters);
    }

    public ChannelCliResult getGuildShareUrl(JsonNode parameters) {
        return execute("get-guild-share-url", parameters);
    }

    public ChannelCliResult getShareInfo(JsonNode parameters) {
        return execute("get-share-info", parameters);
    }

    public ChannelCliResult kickGuildMember(JsonNode parameters, boolean confirmed) {
        return execute("kick-guild-member", parameters, optionsForConfirmation(confirmed));
    }

    public ChannelCliResult modifyMemberShutUp(JsonNode parameters, boolean confirmed) {
        return execute("modify-member-shut-up", parameters, optionsForConfirmation(confirmed));
    }

    public ChannelCliResult updateGuildInfo(JsonNode parameters) {
        return execute("update-guild-info", parameters);
    }

    public ChannelCliResult modifyGuildNumber(JsonNode parameters) {
        return execute("modify-guild-number", parameters);
    }

    public ChannelCliResult createGuildRoleGroup(JsonNode parameters) {
        return execute("create-guild-role-group", parameters);
    }

    public ChannelCliResult modifyGuildRoleGroup(JsonNode parameters) {
        return execute("modify-guild-role-group", parameters);
    }

    public ChannelCliResult addRoleMembers(JsonNode parameters) {
        return execute("add-role-members", parameters);
    }

    public ChannelCliResult removeRoleMembers(JsonNode parameters, boolean confirmed) {
        return execute("remove-role-members", parameters, optionsForConfirmation(confirmed));
    }

    public ChannelCliResult joinGuild(JsonNode parameters) {
        return execute("join-guild", parameters);
    }

    public ChannelCliResult createChannel(JsonNode parameters) {
        return execute("create-channel", parameters);
    }

    public ChannelCliResult deleteChannel(JsonNode parameters, boolean confirmed) {
        return execute("delete-channel", parameters, optionsForConfirmation(confirmed));
    }

    public ChannelCliResult modifyChannel(JsonNode parameters) {
        return execute("modify-channel", parameters);
    }

    public ChannelCliResult uploadGuildAvatar(JsonNode parameters) {
        return execute("upload-guild-avatar", parameters);
    }

    public ChannelCliResult createThemePrivateGuild(JsonNode parameters) {
        return execute("create-theme-private-guild", parameters);
    }

    public ChannelCliResult addAdmin(JsonNode parameters) {
        return execute("add-admin", parameters);
    }

    public ChannelCliResult removeAdmin(JsonNode parameters, boolean confirmed) {
        return execute("remove-admin", parameters, optionsForConfirmation(confirmed));
    }

    public ChannelCliResult pushGroupDmMsg(JsonNode parameters) {
        return execute("push-group-dm-msg", parameters);
    }

    public ChannelCliResult updateJoinGuildSetting(JsonNode parameters) {
        return execute("update-join-guild-setting", parameters);
    }

    public ChannelCliResult leaveGuild(JsonNode parameters, boolean confirmed) {
        return execute("leave-guild", parameters, optionsForConfirmation(confirmed));
    }

    public ChannelCliResult noticesOn(JsonNode parameters) {
        return execute("notices-on", parameters);
    }

    public ChannelCliResult noticesOff(JsonNode parameters) {
        return execute("notices-off", parameters);
    }

    public ChannelCliResult noticesStatus(JsonNode parameters) {
        return execute("notices-status", parameters);
    }

    public ChannelCliResult checkNotices(JsonNode parameters) {
        return execute("check-notices", parameters);
    }

    public ChannelCliResult subscribeNotices(JsonNode parameters) {
        return execute("subscribe-notices", parameters);
    }

    public ChannelCliResult unsubscribeNotices(JsonNode parameters) {
        return execute("unsubscribe-notices", parameters);
    }

    public ChannelCliResult checkNewNotices(JsonNode parameters) {
        return execute("check-new-notices", parameters);
    }

    public ChannelCliResult getRecentNotices(JsonNode parameters) {
        return execute("get-recent-notices", parameters);
    }

    public ChannelCliResult dealNotice(JsonNode parameters) {
        return execute("deal-notice", parameters);
    }

    public ChannelCliResult notifyDaemon(JsonNode parameters) {
        return execute("notify-daemon", parameters);
    }

    public ChannelCliResult searchAndJoin(JsonNode parameters) {
        return execute("search-and-join", parameters);
    }

    private static ChannelCliOptions optionsForConfirmation(boolean confirmed) {
        return confirmed ? ChannelCliOptions.CONFIRMED : ChannelCliOptions.DEFAULT;
    }
}
