package top.yzljc.atribot.command;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.configuration.Properties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * 群指令停用规则。数据量很小，修改时整体写入 JSON，读取使用内存快照。
 * 对外方法刻意保持静态且与 WebUI 无关，后续机器人指令可直接复用。
 */
public final class CommandDisableService {
    private static final Logger log = LoggerFactory.getLogger(CommandDisableService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final Path FILE = Path.of(Properties.COMMAND_DISABLE_SETTINGS);
    private static final TypeReference<Map<String, CommandRules>> TYPE = new TypeReference<>() {};
    private static Map<String, CommandRules> rules = loadFromDisk();

    private CommandDisableService() {}

    public enum Scope { GLOBAL, GROUP }

    public record DisableRule(String reason, String startsAt, String endsAt) {
        public DisableRule {
            reason = clean(reason);
            startsAt = clean(startsAt);
            endsAt = clean(endsAt);
        }

        @JsonIgnore
        public boolean isActive() {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime start = parseTime(startsAt);
            LocalDateTime end = parseTime(endsAt);
            return (start == null || !now.isBefore(start)) && (end == null || now.isBefore(end));
        }
    }

    public record CommandRules(DisableRule global, Map<String, DisableRule> groups) {
        public CommandRules {
            groups = groups == null ? Map.of() : new LinkedHashMap<>(groups);
        }
    }

    public record DisableDecision(String commandName, Scope scope, String groupId, String reason,
                                  String startsAt, String endsAt) {}

    /** 全局优先于单群；没有生效规则时返回 empty。 */
    public static synchronized Optional<DisableDecision> resolve(String commandName, String groupId) {
        String name = normalizeCommand(commandName);
        CommandRules commandRules = rules.get(name);
        if (commandRules == null) return Optional.empty();
        if (commandRules.global() != null && commandRules.global().isActive()) {
            DisableRule rule = commandRules.global();
            return Optional.of(new DisableDecision(name, Scope.GLOBAL, null, replyReason(rule, name),
                    rule.startsAt(), rule.endsAt()));
        }
        if (groupId != null) {
            DisableRule rule = commandRules.groups().get(groupId.trim());
            if (rule != null && rule.isActive()) {
                return Optional.of(new DisableDecision(name, Scope.GROUP, groupId.trim(), replyReason(rule, name),
                        rule.startsAt(), rule.endsAt()));
            }
        }
        return Optional.empty();
    }

    public static boolean isDisabled(String commandName, String groupId) {
        return resolve(commandName, groupId).isPresent();
    }

    public static synchronized Map<String, CommandRules> snapshot() {
        Map<String, CommandRules> copy = new LinkedHashMap<>();
        rules.forEach((name, value) -> copy.put(name,
                new CommandRules(value.global(), new LinkedHashMap<>(value.groups()))));
        return copy;
    }

    public static synchronized void setGlobal(String commandName, DisableRule rule) throws IOException {
        String name = requireCommand(commandName);
        validate(rule);
        CommandRules current = rules.getOrDefault(name, new CommandRules(null, Map.of()));
        rules.put(name, new CommandRules(rule, current.groups()));
        persist();
    }

    public static synchronized void clearGlobal(String commandName) throws IOException {
        String name = normalizeCommand(commandName);
        CommandRules current = rules.get(name);
        if (current == null) return;
        if (current.groups().isEmpty()) rules.remove(name);
        else rules.put(name, new CommandRules(null, current.groups()));
        persist();
    }

    public static synchronized void setGroup(String commandName, String groupId, DisableRule rule) throws IOException {
        setGroups(commandName, groupId == null ? java.util.List.of() : java.util.List.of(groupId), rule);
    }

    /** 为多个群批量写入同一规则，仅落盘一次。 */
    public static synchronized void setGroups(String commandName, Collection<String> groupIds, DisableRule rule) throws IOException {
        String name = requireCommand(commandName);
        if (groupIds == null || groupIds.isEmpty()) throw new IllegalArgumentException("群 ID 不能为空");
        validate(rule);
        CommandRules current = rules.getOrDefault(name, new CommandRules(null, Map.of()));
        Map<String, DisableRule> groups = new LinkedHashMap<>(current.groups());
        int added = 0;
        for (String groupId : groupIds) {
            if (groupId != null && !groupId.isBlank()) {
                groups.put(groupId.trim(), rule);
                added++;
            }
        }
        if (added == 0) throw new IllegalArgumentException("群 ID 不能为空");
        rules.put(name, new CommandRules(current.global(), groups));
        persist();
    }

    public static synchronized void clearGroup(String commandName, String groupId) throws IOException {
        String name = normalizeCommand(commandName);
        CommandRules current = rules.get(name);
        if (current == null || groupId == null) return;
        Map<String, DisableRule> groups = new LinkedHashMap<>(current.groups());
        groups.remove(groupId.trim());
        if (current.global() == null && groups.isEmpty()) rules.remove(name);
        else rules.put(name, new CommandRules(current.global(), groups));
        persist();
    }

    private static String requireCommand(String commandName) {
        String name = normalizeCommand(commandName);
        CommandFeature command = CommandManager.getCommand(name);
        if (command == null) throw new IllegalArgumentException("指令不存在: " + commandName);
        return command.getName().toLowerCase();
    }

    private static String normalizeCommand(String value) {
        if (value == null) return "";
        String name = value.trim().toLowerCase();
        CommandFeature command = CommandManager.getCommand(name);
        return command == null ? name : command.getName().toLowerCase();
    }

    private static void validate(DisableRule rule) {
        if (rule == null) throw new IllegalArgumentException("规则不能为空");
        LocalDateTime start = parseRequiredTime(rule.startsAt(), "开始时间");
        LocalDateTime end = parseRequiredTime(rule.endsAt(), "结束时间");
        if (start != null && end != null && !end.isAfter(start)) {
            throw new IllegalArgumentException("结束时间必须晚于开始时间");
        }
    }

    private static LocalDateTime parseRequiredTime(String value, String label) {
        if (value == null || value.isBlank()) return null;
        try { return LocalDateTime.parse(value.trim()); }
        catch (DateTimeParseException e) { throw new IllegalArgumentException(label + "格式无效"); }
    }

    private static LocalDateTime parseTime(String value) {
        try { return value == null || value.isBlank() ? null : LocalDateTime.parse(value); }
        catch (DateTimeParseException e) { return null; }
    }

    private static String replyReason(DisableRule rule, String name) {
        return rule.reason() == null ? "指令 /" + name + " 当前已停用" : rule.reason();
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Map<String, CommandRules> loadFromDisk() {
        if (!Files.exists(FILE)) return new LinkedHashMap<>();
        try {
            Map<String, CommandRules> loaded = MAPPER.readValue(FILE.toFile(), TYPE);
            return loaded == null ? new LinkedHashMap<>() : new LinkedHashMap<>(loaded);
        } catch (Exception e) {
            log.error("读取指令停用配置失败: {}", FILE, e);
            return new LinkedHashMap<>();
        }
    }

    private static void persist() throws IOException {
        Path parent = FILE.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), rules);
        try {
            Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ignored) {
            Files.move(temp, FILE, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
