package top.yzljc.atribot.command;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;

import java.util.*;

public class SlashCommandArguments {
    @Getter
    private final JsonNode options;
    @Getter
    private final JsonNode resolved;
    @Getter
    private final JsonNode raw;
    @Getter
    private final List<Option> optionList;
    private final Map<String, Option> optionMap;
    private final String[] flatArgs;

    public SlashCommandArguments(JsonNode options, JsonNode resolved, JsonNode raw) {
        this.options = options;
        this.resolved = resolved;
        this.raw = raw;

        List<Option> parsed = new ArrayList<>();
        List<String> flat = new ArrayList<>();
        collectOptions(options, parsed, flat);

        Map<String, Option> byName = new LinkedHashMap<>();
        for (Option option : parsed) {
            byName.put(option.name().toLowerCase(Locale.ROOT), option);
        }

        this.optionList = Collections.unmodifiableList(parsed);
        this.optionMap = Collections.unmodifiableMap(byName);
        this.flatArgs = flat.toArray(new String[0]);
    }

    public Option getOption(String name) {
        if (name == null) {
            return null;
        }
        return optionMap.get(name.toLowerCase(Locale.ROOT));
    }

    public JsonNode getValueNode(String name) {
        Option option = getOption(name);
        return option == null ? null : option.value();
    }

    public String getString(String name) {
        return getString(name, null);
    }

    public String getString(String name, String defaultValue) {
        JsonNode value = getValueNode(name);
        if (value == null || value.isMissingNode() || value.isNull()) {
            return defaultValue;
        }
        return value.asText(defaultValue);
    }

    public Integer getInteger(String name) {
        JsonNode value = getValueNode(name);
        if (value == null || !value.canConvertToInt()) {
            return null;
        }
        return value.asInt();
    }

    public Long getLong(String name) {
        JsonNode value = getValueNode(name);
        if (value == null || !value.canConvertToLong()) {
            return null;
        }
        return value.asLong();
    }

    public Double getNumber(String name) {
        JsonNode value = getValueNode(name);
        if (value == null || !value.isNumber()) {
            return null;
        }
        return value.asDouble();
    }

    public Boolean getBoolean(String name) {
        JsonNode value = getValueNode(name);
        if (value == null || !value.isBoolean()) {
            return null;
        }
        return value.asBoolean();
    }

    public String[] toArray() {
        return flatArgs.clone();
    }

    public int size() {
        return flatArgs.length;
    }

    public boolean isEmpty() {
        return flatArgs.length == 0;
    }

    public String get(int index) {
        return flatArgs[index];
    }

    private static void collectOptions(JsonNode optionsNode, List<Option> parsed, List<String> flat) {
        if (optionsNode == null || !optionsNode.isArray()) {
            return;
        }

        for (JsonNode optionNode : optionsNode) {
            String name = optionNode.path("name").asText(null);
            int type = optionNode.path("type").asInt(-1);

            if (type == 1 || type == 2) {
                if (name != null && !name.isBlank()) {
                    flat.add(name);
                }
                collectOptions(optionNode.path("options"), parsed, flat);
                continue;
            }

            JsonNode value = optionNode.path("value");
            if (name != null && !name.isBlank()) {
                parsed.add(new Option(name, type, value, optionNode));
            }
            if (!value.isMissingNode() && !value.isNull()) {
                flat.add(value.asText());
            }
        }
    }

    public record Option(String name, int type, JsonNode value, JsonNode raw) {
    }
}
