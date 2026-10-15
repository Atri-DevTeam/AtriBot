package top.yzljc.atribot.plugin;

import top.yzljc.atricore.command.CommandDefinition;
import top.yzljc.atricore.platform.Platform;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * @Author YZ_Ljc_
 * @ClassName PluginCommandDefinition
 * @Created_at 2026/09/10
 * @Project AtriMeow
 * @Package top.yzljc.atribot.plugin
 */
public record PluginCommandDefinition(CommandDefinition command, String permission, String permissionMessage,
                                      Set<Platform> platforms) {
    public PluginCommandDefinition {
        platforms = Set.copyOf(platforms);
    }

    public PluginCommandDefinition(CommandDefinition command, String permission, String permissionMessage) {
        this(command, permission, permissionMessage, Set.of());
    }
    static List<PluginCommandDefinition> parse(Object source) {
        if (source == null) return List.of();
        if (!(source instanceof Map<?, ?> commands)) throw new IllegalArgumentException("commands 必须是指令对象");
        List<PluginCommandDefinition> result = new ArrayList<>();
        Set<String> labels = new HashSet<>();
        for (var entry : commands.entrySet()) {
            String name = label(entry.getKey());
            if (!labels.add(name)) throw new IllegalArgumentException("插件内指令或别名重复: " + name);
            Object raw = entry.getValue();
            if (raw != null && !(raw instanceof Map<?, ?>)) throw new IllegalArgumentException("指令 " + name + " 的配置必须是对象");
            Map<?, ?> data = raw == null ? Map.of() : (Map<?, ?>) raw;
            List<String> aliases = new ArrayList<>();
            Object aliasValue = data.get("aliases");
            List<?> rawAliases;
            if (aliasValue == null) rawAliases = List.of();
            else if (aliasValue instanceof String alias) rawAliases = List.of(alias);
            else if (aliasValue instanceof List<?> list) rawAliases = list;
            else throw new IllegalArgumentException("aliases 必须是文本或文本列表");
            for (Object aliasValueItem : rawAliases) {
                String alias = label(aliasValueItem);
                if (!labels.add(alias)) throw new IllegalArgumentException("插件内指令或别名重复: " + alias);
                aliases.add(alias);
            }
            Map<String, Object> definition = new LinkedHashMap<>();
            definition.put("description", text(data, "description", ""));
            definition.put("usage", text(data, "usage", "/<command>"));
            definition.put("aliases", aliases);
            definition.put("prefixless-aliases", data.get("prefixless-aliases"));
            definition.put("options", data.get("options"));
            Set<Platform> platforms = new HashSet<>();
            if (data.get("platforms") != null) {
                if (!(data.get("platforms") instanceof List<?> list) || list.isEmpty())
                    throw new IllegalArgumentException("platforms 必须是非空平台列表");
                for (Object platform : list) platforms.add(Platform.valueOf(String.valueOf(platform)));
            }
            result.add(new PluginCommandDefinition(CommandDefinition.from(name, definition), text(data, "permission", ""),
                    text(data, "permission-message", "你没有权限执行此指令"), platforms));
        }
        return List.copyOf(result);
    }

    private static String label(Object value) {
        if (!(value instanceof String text) || !text.matches("[\\p{L}\\p{N}][\\p{L}\\p{N}_-]{0,63}")) {
            throw new IllegalArgumentException("指令和别名只能包含字母、数字、下划线、连字符，最长 64 字符，不包含 / 或命名空间");
        }
        return text.toLowerCase(Locale.ROOT);
    }

    private static String text(Map<?, ?> data, String key, String fallback) {
        Object value = data.get(key);
        if (value == null) return fallback;
        if (!(value instanceof String text)) throw new IllegalArgumentException(key + " 必须是文本");
        return text;
    }
}
