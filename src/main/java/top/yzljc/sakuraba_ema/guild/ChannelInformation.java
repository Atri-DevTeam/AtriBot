package top.yzljc.sakuraba_ema.guild;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import top.yzljc.atribot.Atri;
import top.yzljc.sakuraba_ema.guild.impl.ChannelCliResult;
import top.yzljc.sakuraba_ema.manager.ChannelManageClient;

public final class ChannelInformation {

    /**
     * Returns all Tencent Channels joined by the configured second account.
     */
    public static ChannelCliResult getJoinedGuilds() {
        return manager().getMyJoinGuildInfo(null);
    }

    /**
     * Returns profile information for a Tencent Channel.
     */
    public static ChannelCliResult getGuildInfo(String guildId) {
        return manager().getGuildInfo(guildParameters(guildId));
    }

    /**
     * Returns the section list used to choose a publish target.
     */
    public static ChannelCliResult getChannelList(String guildId) {
        return manager().getGuildChannelList(guildParameters(guildId));
    }

    private static ObjectNode guildParameters(String guildId) {
        ObjectNode parameters = JsonNodeFactory.instance.objectNode();
        parameters.put("guild_id", guildId);
        return parameters;
    }

    private static ChannelManageClient manager() {
        return Atri.getInstance()
                .getTencentChannelCliClient()
                .manage();
    }
}
