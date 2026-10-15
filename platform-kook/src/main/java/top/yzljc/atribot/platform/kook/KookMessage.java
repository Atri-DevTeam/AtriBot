package top.yzljc.atribot.platform.kook;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import top.yzljc.atribot.platform.Message;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.Recallable;
import top.yzljc.atribot.platform.User;

import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName KookMessage
 * @Created_at 2026/10/04
 * @Project AtriMeow
 * @Package top.yzljc.atribot.platform.kook
 */
public final class KookMessage extends Message implements Recallable {
    @Getter private final int type;
    @Getter private final String targetId;
    @Getter private final String guildId;
    @Getter private final String chatCode;
    @Getter private final JsonNode extra;
    @Getter private final JsonNode raw;
    private final KookApiClient api;

    public KookMessage(Platform platform, JsonNode data, List<User> mentionedUsers, KookApiClient api) {
        super(platform, data.path("msg_id").asText(), data.path("content").asText(""),
                data.path("msg_timestamp").asText(), List.copyOf(mentionedUsers));
        if (platform != Platform.KOOK_CHANNEL && platform != Platform.KOOK_DM) {
            throw new IllegalArgumentException("KookMessage 仅支持 KOOK 频道和私信");
        }
        this.type = data.path("type").asInt();
        this.targetId = data.path("target_id").asText();
        this.extra = data.path("extra");
        this.guildId = extra.path("guild_id").asText(null);
        this.chatCode = extra.path("code").asText(null);
        this.raw = data;
        this.api = api;
    }

    public boolean isText() {
        return type == 1 || type == 9;
    }

    @Override
    public boolean recall() {
        return api.recallMessage(getPlatform(), getMessageId());
    }
}

