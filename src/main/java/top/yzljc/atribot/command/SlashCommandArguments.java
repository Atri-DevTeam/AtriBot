package top.yzljc.atribot.command;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;

import java.util.*;

/**
 * @Author YZ_Ljc_
 * @ClassName SlashCommandArguments
 * @Created_at 2026/07/20
 * @Project AtriMeow
 * @Package top.yzljc.atribot.command
 */
public class SlashCommandArguments {
    @Getter
    private final List<String> commandPath;
    @Getter
    private final List<Option> optionList;
    private final Map<String, Option> optionMap;
    private final String[] flatArgs;

    public SlashCommandArguments(List<String> commandPath, List<Option> options) {
        this.commandPath = List.copyOf(commandPath);
        this.optionList = List.copyOf(options);
        Map<String, Option> byName = new LinkedHashMap<>();
        List<String> flat = new ArrayList<>(commandPath);
        for (Option option : optionList) {
            byName.put(option.name().toLowerCase(Locale.ROOT), option);
            JsonNode value = option.value();
            if (value != null && !value.isMissingNode() && !value.isNull()) flat.add(value.asText());
        }
        this.optionMap = Collections.unmodifiableMap(byName);
        this.flatArgs = flat.toArray(String[]::new);
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

    /**
     * 将子命令路径和选项值按顺序转换为位置参数，忽略空值，不保留选项名称及类型。
     *
     * @return 位置参数数组的副本
     */
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

    public record Option(String name, JsonNode value) {
        public Option {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("参数名称不能为空");
        }
    }
}
