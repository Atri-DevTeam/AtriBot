package top.yzljc.atricore.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashSet;

/**
 * 指令声明数据，包含名称、用法和两类触发别名
 *
 * @Author YZ_Ljc_
 * @ClassName CommandDefinition
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.command
 */
public record CommandDefinition(
        String name,
        String description,
        String usage,
        List<String> aliases,
        List<String> prefixlessAliases
) {

    public CommandDefinition {
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        description = description == null ? "" : description;
        usage = usage == null || usage.isBlank() ? "/" + name : usage;
        prefixlessAliases = parsePrefixlessAliases(prefixlessAliases);
    }

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

        return new CommandDefinition(name, description, usage, aliases,
                parsePrefixlessAliases(data.get("prefixless-aliases")));
    }

    private static List<String> parsePrefixlessAliases(Object raw) {
        if (raw == null) return List.of();
        if (!(raw instanceof List<?> values)) {
            throw new IllegalArgumentException("prefixless-aliases 必须是文本列表");
        }
        var result = new LinkedHashSet<String>();
        for (Object value : values) {
            if (!(value instanceof String alias) || alias.isBlank()
                    || alias.codePoints().anyMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))) {
                throw new IllegalArgumentException("prefixless-aliases 中的触发词必须是非空文本，且不能包含空白字符");
            }
            result.add(alias.toLowerCase(Locale.ROOT));
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
}
