package top.yzljc.atribot.platform.discord;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import top.yzljc.atribot.command.SlashCommandArguments;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName DiscordSlashCommandArguments
 * @Created_at 2026/10/04
 * @Project AtriMeow
 * @Package top.yzljc.atribot.platform.discord
 */
@Getter
public final class DiscordSlashCommandArguments extends SlashCommandArguments {
    private final JsonNode options;
    private final JsonNode resolved;
    private final JsonNode raw;

    public DiscordSlashCommandArguments(JsonNode options, JsonNode resolved, JsonNode raw) {
        this(parse(options), options, resolved, raw);
    }

    private DiscordSlashCommandArguments(Parsed parsed, JsonNode options, JsonNode resolved, JsonNode raw) {
        super(parsed.path(), parsed.options());
        this.options = options;
        this.resolved = resolved;
        this.raw = raw;
    }

    private static Parsed parse(JsonNode options) {
        List<String> path = new ArrayList<>();
        List<Option> values = new ArrayList<>();
        collect(options, path, values);
        return new Parsed(path, values);
    }

    private static void collect(JsonNode options, List<String> path, List<Option> values) {
        if (options == null || !options.isArray()) return;
        for (JsonNode option : options) {
            String name = option.path("name").asText(null);
            int type = option.path("type").asInt(-1);
            if (type == 1 || type == 2) {
                if (name != null && !name.isBlank()) path.add(name);
                collect(option.path("options"), path, values);
            } else if (name != null && !name.isBlank()) {
                values.add(new Option(name, option.path("value")));
            }
        }
    }

    private record Parsed(List<String> path, List<Option> options) {
    }
}
