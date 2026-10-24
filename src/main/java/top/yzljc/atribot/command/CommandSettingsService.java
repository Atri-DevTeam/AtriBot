package top.yzljc.atribot.command;

import top.yzljc.atricore.command.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.configuration.Properties;
import top.yzljc.atribot.utils.AtomicFiles;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.database.repo.GroupBindingRepository;
import top.yzljc.atribot.miniapp.service.MiniappGroupService.Problem;
import top.yzljc.atricore.event.EventHandler;
import top.yzljc.atricore.event.EventPriority;
import top.yzljc.atricore.event.events.UserRunCommandEvent;
import top.yzljc.atricore.platform.Platform;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/**
 * 群指令开关、频控及管理后台高级停用规则
 *
 * @Author YZ_Ljc_
 * @ClassName CommandSettingsService
 * @Created_at 2026/10/24
 * @Project AtriMeow
 * @Package top.yzljc.atribot.command
 */
public final class CommandSettingsService implements top.yzljc.atricore.event.Listener {
    private static final Logger log = LoggerFactory.getLogger(CommandSettingsService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final Path file;
    private static final TypeReference<Map<String, CommandRules>> TYPE = new TypeReference<>() {
    };
    public static final CommandSettingsService INSTANCE = new CommandSettingsService(
            Path.of(Properties.COMMAND_SETTINGS), new GroupBindingRepository(), CommandManager::getCommand,
            id -> OfficialUsers.isBlocked(id) || OfficialUsers.isIgnored(id), System::nanoTime);
    private volatile Map<String, CommandRules> rules;
    private final GroupBindingRepository bindings;
    private final Function<String, CommandFeature> lookup;
    private final Predicate<String> blocked;
    private final LongSupplier nanoTime;
    private final Map<String, Registration> registrations = new LinkedHashMap<>();
    private final Map<String, Registration> targets = new ConcurrentHashMap<>();
    private volatile Map<String, Map<String, GroupRule>> groupRules = Map.of();
    private volatile boolean initialized;
    private final Cache<RateKey, Long> cooldowns = CacheBuilder.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS).maximumSize(100_000).build();

    CommandSettingsService(Path file, GroupBindingRepository bindings, Function<String, CommandFeature> lookup,
                           Predicate<String> blocked, LongSupplier nanoTime) {
        this.file = file;
        this.bindings = bindings;
        this.lookup = lookup;
        this.blocked = blocked;
        this.nanoTime = nanoTime;
        this.rules = Map.copyOf(loadFromDisk());
    }

    public enum CooldownScope {USER, GROUP}

    public record GroupRule(boolean enabled, int cooldownSeconds, CooldownScope scope) {
        public GroupRule {
            if (cooldownSeconds < 0 || cooldownSeconds > 3600 || scope == null)
                throw new IllegalArgumentException("冷却时间应为 0–3600 秒，并指定有效的频控范围");
        }
    }

    public record Registration(String key, String displayName, boolean switchable, boolean rateLimitable,
                               Set<String> commandNames, BiPredicate<String, String[]> counts) {
        public Registration {
            if (key == null || key.isBlank() || displayName == null || displayName.isBlank()
                    || commandNames == null || commandNames.isEmpty() || counts == null)
                throw new IllegalArgumentException("群指令登记信息不完整");
            commandNames = Set.copyOf(commandNames);
        }
    }

    public record GroupItem(String key, String displayName, boolean switchable, boolean rateLimitable,
                            boolean enabled, int cooldownSeconds, CooldownScope scope, boolean locked,
                            String lockReason, String startsAt, String endsAt) {
    }

    public record GroupView(List<GroupItem> items, boolean canEdit) {
    }

    private record RateKey(String groupId, String command, String userId, CooldownScope scope, int seconds) {
    }

    /**
     * 显式登记可由群主配置的指令，未登记的指令不读取群规则
     *
     * @param command       主指令名
     * @param displayName   页面显示名称
     * @param switchable    是否开放群开关
     * @param rateLimitable 是否开放频控设置
     * @param counts        判断当前调用是否占用冷却额度，参数依次为实际标签和附属参数
     */
    public synchronized void register(String command, String displayName, boolean switchable, boolean rateLimitable,
                                      BiPredicate<String, String[]> counts) {
        register(new Registration(command, displayName, switchable, rateLimitable, Set.of(requireCommand(command)), counts));
    }

    public synchronized void register(Registration registration) {
        if (initialized) throw new IllegalStateException("群指令配置加载后不能追加登记");
        if (registrations.containsKey(registration.key()))
            throw new IllegalArgumentException("群指令重复登记: " + registration.key());
        for (String name : registration.commandNames()) {
            if (targets.containsKey(name)) throw new IllegalArgumentException("群指令入口重复登记: " + name);
        }
        registrations.put(registration.key(), registration);
        registration.commandNames().forEach(name -> targets.put(name, registration));
    }

    /**
     * 启动阶段加载群指令配置，完成后指令执行仅访问内存
     *
     * @throws SQLException 数据库读取或配置解析失败
     */
    public synchronized void init() throws SQLException {
        var loaded = new LinkedHashMap<String, Map<String, GroupRule>>();
        for (var entry : bindings.loadCommandSettings().entrySet())
            loaded.put(entry.getKey(), parseGroupRules(entry.getValue()));
        groupRules = Map.copyOf(loaded);
        cooldowns.invalidateAll();
        initialized = true;
        log.info("群指令设置已加载，共 {} 个功能，{} 个群配置", registrations.size(), loaded.size());
    }

    private Map<String, GroupRule> parseGroupRules(ObjectNode source) throws SQLException {
        var parsed = new LinkedHashMap<String, GroupRule>();
        try {
            for (String key : registrations.keySet()) {
                if (source.has(key)) parsed.put(key, parseGroupRule(source.get(key)));
            }
        } catch (IllegalArgumentException failure) {
            throw new SQLException("群指令配置无效", failure);
        }
        return Map.copyOf(parsed);
    }

    public synchronized List<Registration> registrations() {
        return List.copyOf(registrations.values());
    }

    public Registration registration(String commandName) {
        return targets.get(commandName);
    }

    public synchronized void forgetGroup(String groupId) {
        if (!groupRules.containsKey(groupId)) return;
        var next = new LinkedHashMap<>(groupRules);
        next.remove(groupId);
        groupRules = Map.copyOf(next);
    }

    public GroupView groupView(String userId, String groupId) throws SQLException {
        var group = authorize(userId, groupId);
        return new GroupView(registrations().stream().map(registration -> {
            GroupRule local = groupRule(groupId, registration.key());
            var advanced = advanced(registration, groupId).orElse(null);
            return new GroupItem(registration.key(), registration.displayName(), registration.switchable(), registration.rateLimitable(),
                    advanced == null && local.enabled(), local.cooldownSeconds(), local.scope(), advanced != null,
                    advanced == null ? null : advanced.reason(), advanced == null ? null : advanced.startsAt(), advanced == null ? null : advanced.endsAt());
        }).toList(), group.available() && !group.restricted() && !blocked.test(userId));
    }

    public synchronized GroupView saveGroup(String userId, String groupId, String command, JsonNode request,
                                            Runnable checkSession) throws SQLException {
        var group = authorize(userId, groupId);
        if (!group.available() || group.restricted() || blocked.test(userId))
            throw new Problem(403, "GROUP_SETTINGS_FORBIDDEN");
        Registration registration = registrations.get(command);
        if (registration == null) throw new Problem(400, "INVALID_GROUP_COMMAND");
        GroupRule rule;
        try {
            rule = parseGroupRule(request);
        } catch (IllegalArgumentException invalid) {
            throw new Problem(400, "INVALID_COMMAND_SETTINGS");
        }
        if (!registration.switchable() && !rule.enabled() || !registration.rateLimitable()
                && (rule.cooldownSeconds() != 0 || rule.scope() != CooldownScope.USER))
            throw new Problem(400, "INVALID_COMMAND_SETTINGS");
        Runnable guard = () -> {
            checkSession.run();
            if (blocked.test(userId)) throw new Problem(403, "GROUP_SETTINGS_FORBIDDEN");
            if (advanced(registration, groupId).isPresent()) throw new Problem(409, "COMMAND_SETTINGS_LOCKED");
        };
        guard.run();
        var result = bindings.setCommand(userId, groupId, command, MAPPER.valueToTree(rule), guard);
        switch (result.result()) {
            case NOT_BOUND -> throw new Problem(403, "GROUP_NOT_BOUND");
            case UNAVAILABLE -> throw new Problem(409, "GROUP_UNAVAILABLE");
            case RESTRICTED -> throw new Problem(403, "GROUP_SETTINGS_FORBIDDEN");
            case SAVED -> {
                var next = new LinkedHashMap<>(groupRules);
                next.put(groupId, parseGroupRules(result.commands()));
                groupRules = Map.copyOf(next);
            }
            default -> throw new IllegalStateException("Unexpected group command write result");
        }
        return groupView(userId, groupId);
    }

    private GroupBindingRepository.Group authorize(String userId, String groupId) throws SQLException {
        if (!initialized) throw new Problem(503, "COMMAND_SETTINGS_UNAVAILABLE");
        if (groupId == null || !groupId.matches("[A-Za-z0-9_-]{1,256}")) throw new Problem(400, "INVALID_GROUP");
        var group = bindings.detail(userId, groupId);
        if (group == null) throw new Problem(403, "GROUP_NOT_BOUND");
        return group;
    }

    private static GroupRule parseGroupRule(JsonNode node) {
        if (node == null || !node.isObject() || node.size() != 3 || !node.path("enabled").isBoolean()
                || !node.path("cooldownSeconds").isInt() || !node.path("scope").isTextual())
            throw new IllegalArgumentException("指令配置字段无效");
        return new GroupRule(node.path("enabled").booleanValue(), node.path("cooldownSeconds").intValue(),
                CooldownScope.valueOf(node.path("scope").textValue()));
    }

    private GroupRule groupRule(String groupId, String command) {
        return groupRules.getOrDefault(groupId, Map.of()).getOrDefault(command, new GroupRule(true, 0, CooldownScope.USER));
    }

    private Optional<DisableDecision> advanced(Registration registration, String groupId) {
        var decision = resolve(registration.key(), groupId);
        for (String name : registration.commandNames()) {
            var additional = resolve(name, groupId);
            if (additional.isPresent() && (decision.isEmpty() || additional.get().scope() == Scope.GLOBAL))
                decision = additional;
        }
        return decision;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(UserRunCommandEvent event) {
        if (!(event.getSender() instanceof QQCommandSender user) || user.getPlatform() != Platform.OFFICIAL_GROUP)
            return;
        String name = event.getCommand() instanceof ScopedCommand scoped ? scoped.getQualifiedName() : event.getCommand().getName();
        Registration registration = targets.get(name);
        if (registration == null) return;
        String denied = check(user.getGroupId(), user.getUserId(), registration, event.getLabel(), event.getArgs(), user.hasPermission());
        if (denied != null) {
            event.setCancelled(true);
            user.sendMessage(denied);
        }
    }

    String check(String groupId, String userId, Registration registration, String label, String[] args, boolean bypass) {
        var advanced = advanced(registration, groupId);
        if (advanced.isPresent()) return advanced.get().reason();
        if (!initialized) return "群指令配置暂不可用，请稍后重试";
        GroupRule rule = groupRule(groupId, registration.key());
        if (registration.switchable() && !rule.enabled()) return "本群已关闭「" + registration.displayName() + "」";
        if (!registration.rateLimitable() || rule.cooldownSeconds() == 0 || bypass || !registration.counts().test(label, args))
            return null;
        long now = nanoTime.getAsLong(), window = TimeUnit.SECONDS.toNanos(rule.cooldownSeconds());
        var key = new RateKey(groupId, registration.key(), rule.scope() == CooldownScope.USER ? userId : "", rule.scope(), rule.cooldownSeconds());
        var remaining = new AtomicLong();
        cooldowns.asMap().compute(key, (ignored, previous) -> {
            if (previous != null && now - previous < window) {
                remaining.set(window - (now - previous));
                return previous;
            }
            return now;
        });
        return remaining.get() == 0 ? null : "「" + registration.displayName() + "」使用过于频繁，请在 "
                + Math.max(1, (long) Math.ceil(remaining.get() / 1_000_000_000.0)) + " 秒后重试";
    }


    public enum Scope {GLOBAL, GROUP}

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
            groups = groups == null ? Map.of() : Map.copyOf(groups);
        }
    }

    public record DisableDecision(String commandName, Scope scope, String groupId, String reason,
                                  String startsAt, String endsAt) {
    }

    /**
     * 全局优先于单群；没有生效规则时返回 empty。
     */
    public Optional<DisableDecision> resolve(String commandName, String groupId) {
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

    public boolean isDisabled(String commandName, String groupId) {
        return resolve(commandName, groupId).isPresent();
    }

    public synchronized Map<String, CommandRules> snapshot() {
        Map<String, CommandRules> copy = new LinkedHashMap<>();
        rules.forEach((name, value) -> copy.put(name,
                new CommandRules(value.global(), new LinkedHashMap<>(value.groups()))));
        return copy;
    }

    public synchronized void setGlobal(String commandName, DisableRule rule) throws IOException {
        String name = requireCommand(commandName);
        validate(rule);
        CommandRules current = rules.getOrDefault(name, new CommandRules(null, Map.of()));
        replaceRule(name, new CommandRules(rule, current.groups()));
    }

    public synchronized void clearGlobal(String commandName) throws IOException {
        String name = normalizeCommand(commandName);
        CommandRules current = rules.get(name);
        if (current == null) return;
        replaceRule(name, current.groups().isEmpty() ? null : new CommandRules(null, current.groups()));
    }

    public synchronized void setGroup(String commandName, String groupId, DisableRule rule) throws IOException {
        setGroups(commandName, groupId == null ? java.util.List.of() : java.util.List.of(groupId), rule);
    }

    /**
     * 为多个群批量写入同一规则，仅落盘一次。
     */
    public synchronized void setGroups(String commandName, Collection<String> groupIds, DisableRule rule) throws IOException {
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
        replaceRule(name, new CommandRules(current.global(), groups));
    }

    public synchronized void clearGroup(String commandName, String groupId) throws IOException {
        String name = normalizeCommand(commandName);
        CommandRules current = rules.get(name);
        if (current == null || groupId == null) return;
        Map<String, DisableRule> groups = new LinkedHashMap<>(current.groups());
        groups.remove(groupId.trim());
        replaceRule(name, current.global() == null && groups.isEmpty() ? null : new CommandRules(current.global(), groups));
    }

    private String requireCommand(String commandName) {
        String name = normalizeCommand(commandName);
        CommandFeature command = lookup.apply(name);
        if (command == null) throw new IllegalArgumentException("指令不存在: " + commandName);
        return commandKey(command);
    }

    private String normalizeCommand(String value) {
        if (value == null) return "";
        String name = value.trim().toLowerCase(Locale.ROOT);
        CommandFeature command = lookup.apply(name);
        return command == null ? name : commandKey(command);
    }

    private static String commandKey(CommandFeature command) {
        return command instanceof ScopedCommand scoped ? scoped.getQualifiedName() : command.getName().toLowerCase(Locale.ROOT);
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
        try {
            return LocalDateTime.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(label + "格式无效");
        }
    }

    private static LocalDateTime parseTime(String value) {
        try {
            return value == null || value.isBlank() ? null : LocalDateTime.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String replyReason(DisableRule rule, String name) {
        return rule.reason() == null ? "指令 /" + name + " 当前已停用" : rule.reason();
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Map<String, CommandRules> loadFromDisk() {
        if (!Files.exists(file)) return new LinkedHashMap<>();
        try {
            Map<String, CommandRules> loaded = MAPPER.readValue(file.toFile(), TYPE);
            if (loaded == null) throw new IOException("指令设置必须为 JSON 对象");
            for (var rule : loaded.values()) {
                if (rule.global() != null) validate(rule.global());
                for (var groupRule : rule.groups().values()) validate(groupRule);
            }
            return new LinkedHashMap<>(loaded);
        } catch (Exception e) {
            throw new IllegalStateException("读取指令高级停用配置失败: " + file, e);
        }
    }

    private void replaceRule(String name, CommandRules value) throws IOException {
        var next = new LinkedHashMap<>(rules);
        if (value == null) next.remove(name);
        else next.put(name, value);
        AtomicFiles.write(file, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(next));
        rules = Map.copyOf(next);
    }
}
