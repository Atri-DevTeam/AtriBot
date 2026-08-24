package top.yzljc.atribot.command;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Discord Slash Command 参数声明
 *
 * <p>字段对应 Discord {@code ApplicationCommandOption} JSON schema，
 * type 取 Discord 官方编号：1=SUB_COMMAND、2=SUB_COMMAND_GROUP、3=STRING、4=INTEGER、
 * 5=BOOLEAN、6=USER、7=CHANNEL、8=ROLE、9=MENTIONABLE、10=NUMBER、11=ATTACHMENT。
 */
public record CommandOptionDefinition(
        String name,
        int type,
        String description,
        boolean required,
        List<OptionChoice> choices,
        Double minValue,
        Double maxValue,
        List<Integer> channelTypes,
        List<CommandOptionDefinition> options
) {
    public CommandOptionDefinition {
        choices = choices == null ? List.of() : List.copyOf(choices);
        channelTypes = channelTypes == null ? List.of() : List.copyOf(channelTypes);
        options = options == null ? List.of() : List.copyOf(options);
        description = description == null ? "" : description;
    }

    /**
     * Discord option 的预选值（仅 STRING/INTEGER/NUMBER 支持）。
     * value 类型由所属 option 的 type 决定。
     */
    public record OptionChoice(String name, JsonNode value) {
    }
}
