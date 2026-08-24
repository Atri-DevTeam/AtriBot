package top.yzljc.atribot.command;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record CommandDefinition(
        String name,
        String description,
        String usage,
        List<String> aliases,
        List<CommandOptionDefinition> options
) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public CommandDefinition {
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        description = description == null ? "" : description;
        usage = usage == null || usage.isBlank() ? "/" + name : usage;
        options = options == null ? List.of() : List.copyOf(options);
    }

    /**
     * 兼容旧 4 参构造器。新的 options 字段默认为空列表。
     */
    public CommandDefinition(String name, String description, String usage, List<String> aliases) {
        this(name, description, usage, aliases, List.of());
    }

    public static CommandDefinition from(String name, Map<String, ?> data) {
        String description = stringValue(data.get("description"), "");
        String usage = stringValue(data.get("usage"), null);
        List<String> aliases = new ArrayList<>();
        Object aliasesObj = data.get("aliases");
        if (aliasesObj instanceof Iterable<?> iterable) {
            for (Object alias : iterable) {
                if (alias instanceof String aliasName && !aliasName.isBlank()) {
                    aliases.add(aliasName);
                }
            }
        }

        List<CommandOptionDefinition> options = parseOptions(data.get("options"));

        return new CommandDefinition(name, description, usage, aliases, options);
    }

    private static List<CommandOptionDefinition> parseOptions(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<CommandOptionDefinition> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> rawOption) {
                result.add(parseOption(rawOption));
            }
        }
        return List.copyOf(result);
    }

    private static CommandOptionDefinition parseOption(Map<?, ?> raw) {
        String name = stringValue(raw.get("name"), null);
        if (name == null || name.isBlank()) {
            return null;
        }
        int type = intValue(raw.get("type"), -1);
        String description = stringValue(raw.get("description"), "");
        boolean required = boolValue(raw.get("required"), false);

        List<CommandOptionDefinition.OptionChoice> choices = parseChoices(raw.get("choices"));
        Double minValue = doubleValue(raw.get("min_value"));
        Double maxValue = doubleValue(raw.get("max_value"));
        List<Integer> channelTypes = parseIntegers(raw.get("channel_types"));
        List<CommandOptionDefinition> nested = parseOptions(raw.get("options"));

        return new CommandOptionDefinition(
                name, type, description, required,
                choices, minValue, maxValue, channelTypes, nested
        );
    }

    private static List<CommandOptionDefinition.OptionChoice> parseChoices(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<CommandOptionDefinition.OptionChoice> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> rawChoice) {
                String cName = stringValue(rawChoice.get("name"), null);
                if (cName == null || cName.isBlank()) {
                    continue;
                }
                Object cValue = rawChoice.get("value");
                if (cValue == null) {
                    continue;
                }
                result.add(new CommandOptionDefinition.OptionChoice(
                        cName, OBJECT_MAPPER.valueToTree(cValue)
                ));
            }
        }
        return List.copyOf(result);
    }

    private static List<Integer> parseIntegers(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Integer> result = new ArrayList<>();
        for (Object item : list) {
            Integer parsed = intValueOrNull(item);
            if (parsed != null) {
                result.add(parsed);
            }
        }
        return List.copyOf(result);
    }

    private static String stringValue(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof String str) {
            return str;
        }
        return String.valueOf(value);
    }

    private static int intValue(Object value, int defaultValue) {
        Integer parsed = intValueOrNull(value);
        return parsed == null ? defaultValue : parsed;
    }

    private static Integer intValueOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String str) {
            try {
                return Integer.parseInt(str.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static Double doubleValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String str) {
            try {
                return Double.parseDouble(str.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static boolean boolValue(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String str) {
            if (str.equalsIgnoreCase("true") || str.equalsIgnoreCase("yes") || str.equals("1")) {
                return true;
            }
            if (str.equalsIgnoreCase("false") || str.equalsIgnoreCase("no") || str.equals("0")) {
                return false;
            }
        }
        return defaultValue;
    }
}
