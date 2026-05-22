package top.yzljc.qqbot.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record CommandDefinition(String name, String description, String usage, List<String> aliases, String featureKey,
                                boolean officialOnly) {

    public CommandDefinition {
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        description = description == null ? "" : description;
        usage = usage == null || usage.isBlank() ? "/" + name : usage;
        featureKey = featureKey == null || featureKey.isBlank() ? null : featureKey;
    }

    public static CommandDefinition from(String name, Map<String, ?> data) {
        String description = stringValue(data.get("description"), "");
        String usage = stringValue(data.get("usage"), null);
        String featureKey = stringValue(data.get("feature"), null);
        boolean officialOnly = booleanValue(data.get("official-only"));

        List<String> aliases = new ArrayList<>();
        Object aliasesObj = data.get("aliases");
        if (aliasesObj instanceof Iterable<?> iterable) {
            for (Object alias : iterable) {
                if (alias instanceof String aliasName && !aliasName.isBlank()) {
                    aliases.add(aliasName);
                }
            }
        }

        return new CommandDefinition(name, description, usage, aliases, featureKey, officialOnly);
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return false;
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
