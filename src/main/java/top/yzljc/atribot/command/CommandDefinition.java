package top.yzljc.atribot.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record CommandDefinition(String name, String description, String usage, List<String> aliases) {

    public CommandDefinition {
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        description = description == null ? "" : description;
        usage = usage == null || usage.isBlank() ? "/" + name : usage;
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

        return new CommandDefinition(name, description, usage, aliases);
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
}
