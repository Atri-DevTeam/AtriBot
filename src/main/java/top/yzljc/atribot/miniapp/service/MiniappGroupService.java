package top.yzljc.atribot.miniapp.service;

import top.yzljc.atricore.core.CoreConfig;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.chat.official.welcome.DefaultJoinWelcomeStore;
import top.yzljc.atricore.auth.official.UnifiedRole;
import top.yzljc.atribot.function.command.PushTaskCommand;
import top.yzljc.atribot.function.tasks.pushtask.PushTask;
import top.yzljc.atribot.database.repo.GroupBindingRepository;
import top.yzljc.atribot.miniapp.MiniappSessions;
import top.yzljc.atricore.platform.PlatformRole;

import java.security.SecureRandom;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.Predicate;

/**
 * @Author YZ_Ljc_
 * @ClassName MiniappGroupService
 * @Created_at 2026/09/20
 * @Project AtriMeow
 * @Package top.yzljc.atribot.miniapp.service
 */
public final class MiniappGroupService {
    public static final MiniappGroupService INSTANCE = new MiniappGroupService(new GroupBindingRepository(), Clock.systemUTC(),
            () -> CoreConfig.getInstance().getSettings().getCommandPrefix());
    private static final long TTL = 10 * 60_000L;
    private final GroupBindingRepository repository;
    private final Clock clock;
    private final Supplier<String> prefix;
    private final Supplier<List<PushTask>> tasks;
    private final Predicate<String> blocked;
    private final WelcomeDefaults defaults;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Challenge> pending = new HashMap<>();
    private final Map<String, Integer> attempts = new HashMap<>();

    public record Challenge(String groupId, String code, String command, long expiresAt) {
    }

    public record Overview(List<GroupBindingRepository.Binding> groups, Challenge pending) {
    }

    public record WelcomeButton(String label, String style) {}
    public record Welcome(boolean enabled, boolean custom, boolean canEdit, String text, String buttonSize,
                          List<List<WelcomeButton>> keyboard) {}
    public record Function(String key, String name, boolean enabled, boolean needActiveMessage) {}
    public record Functions(List<Function> items, boolean canEdit, boolean proactive) {}

    @FunctionalInterface
    interface WelcomeDefaults {
        ObjectNode load() throws IOException;
    }

    public Welcome welcome(MiniappSessions.Identity identity, String groupId) throws SQLException, IOException {
        validateGroupId(groupId);
        var stored = repository.welcome(identity.userId(), groupId);
        if (stored == null) throw new Problem(404, "GROUP_NOT_BOUND");
        boolean enabled = stored.enabled() != null ? stored.enabled() : tasks.get().stream()
                .anyMatch(task -> task.getFunctionId().equals("member_add_welcome") && task.isDefaultEnabled());
        var config = stored.config();
        boolean custom = config != null && (!config.path("text").asText("").isBlank()
                || config.path("keyboard").isArray() && !config.path("keyboard").isEmpty());
        if (!custom) {
            config = defaults.load();
        }
        List<List<WelcomeButton>> keyboard = new ArrayList<>();
        if (config.path("keyboard").isArray()) for (var row : config.path("keyboard")) {
            if (!row.isArray()) continue;
            List<WelcomeButton> buttons = new ArrayList<>();
            for (var button : row) if (button.isObject()) buttons.add(new WelcomeButton(
                    button.path("display_text").asText("未命名按钮"), button.path("style").asText("BLUE")));
            if (!buttons.isEmpty()) keyboard.add(List.copyOf(buttons));
        }
        return new Welcome(enabled, custom, stored.canEdit() && !blocked.test(identity.userId()), config.path("text").asText(""),
                config.path("button_size").asText("UNDEFINED"), List.copyOf(keyboard));
    }

    public Functions functions(MiniappSessions.Identity identity, String groupId) throws SQLException {
        validateGroupId(groupId);
        var stored = repository.functions(identity.userId(), groupId);
        if (stored == null) throw new Problem(403, "GROUP_NOT_BOUND");
        var items = tasks.get().stream().filter(MiniappGroupService::publicGroupTask)
                .filter(task -> !task.getFunctionId().equals("member_add_welcome"))
                .map(task -> {
                    JsonNode enabled = stored.functions().path(task.getFunctionId()).path("enabled");
                    return new Function(task.getFunctionId(), task.getDisplayName(),
                            enabled.isBoolean() ? enabled.booleanValue() : task.isDefaultEnabled(), task.isNeedActiveMessage());
                }).toList();
        return new Functions(items, stored.available() && !stored.restricted() && !blocked.test(identity.userId()), stored.proactive());
    }

    public void setFunction(MiniappSessions.Identity identity, String groupId, String functionId,
                            boolean enabled, Runnable checkSession) throws SQLException {
        if ("member_add_welcome".equals(functionId)) throw new Problem(400, "INVALID_GROUP_FUNCTION");
        updateFunction(identity, groupId, functionId, enabled, checkSession);
    }

    public void setWelcomeEnabled(MiniappSessions.Identity identity, String groupId, boolean enabled,
                                  Runnable checkSession) throws SQLException {
        updateFunction(identity, groupId, "member_add_welcome", enabled, checkSession);
    }

    private void updateFunction(MiniappSessions.Identity identity, String groupId, String functionId,
                                boolean enabled, Runnable checkSession) throws SQLException {
        validateGroupId(groupId);
        if (blocked.test(identity.userId())) throw new Problem(403, "GROUP_SETTINGS_FORBIDDEN");
        var task = tasks.get().stream().filter(MiniappGroupService::publicGroupTask)
                .filter(item -> item.getFunctionId().equals(functionId)).findFirst()
                .orElseThrow(() -> new Problem(400, "INVALID_GROUP_FUNCTION"));
        checkSession.run();
        var result = repository.setFunction(identity.userId(), groupId, functionId, enabled, task.isNeedActiveMessage(), () -> {
            checkSession.run();
            if (blocked.test(identity.userId())) throw new Problem(403, "GROUP_SETTINGS_FORBIDDEN");
        });
        switch (result) {
            case NOT_BOUND -> throw new Problem(403, "GROUP_NOT_BOUND");
            case UNAVAILABLE -> throw new Problem(409, "GROUP_UNAVAILABLE");
            case RESTRICTED -> throw new Problem(403, "GROUP_SETTINGS_FORBIDDEN");
            case ACTIVE_REQUIRED -> throw new Problem(409, "GROUP_ACTIVE_REQUIRED");
            case SAVED -> { }
        }
    }

    private static boolean publicGroupTask(PushTask task) {
        return task.isGroupEnable() && !task.isPrivateFunction();
    }

    public enum Verification {SUCCESS, NO_PENDING, EXPIRED, NOT_OWNER, WRONG_GROUP, WRONG_CODE, TOO_MANY_ATTEMPTS, SAVE_FAILED}

    public static final class Problem extends RuntimeException {
        public final int status;
        public final String code;

        public Problem(int status, String code) {
            super(code);
            this.status = status;
            this.code = code;
        }
    }

    public MiniappGroupService(GroupBindingRepository repository, Clock clock, Supplier<String> prefix) {
        this(repository, clock, prefix, PushTaskCommand::getTasks,
                id -> OfficialUsers.isBlocked(id) || OfficialUsers.isIgnored(id),
                () -> DefaultJoinWelcomeStore.getInstance().get(UnifiedRole.USER));
    }

    MiniappGroupService(GroupBindingRepository repository, Clock clock, Supplier<String> prefix,
                        Supplier<List<PushTask>> tasks, Predicate<String> blocked, WelcomeDefaults defaults) {
        this.repository = repository;
        this.clock = clock;
        this.prefix = prefix;
        this.tasks = tasks;
        this.blocked = blocked;
        this.defaults = defaults;
    }

    public Overview load(MiniappSessions.Identity identity) throws SQLException {
        return new Overview(repository.list(identity.userId()), pending(identity.userId()));
    }

    public GroupBindingRepository.Group detail(MiniappSessions.Identity identity, String groupId) throws SQLException {
        validateGroupId(groupId);
        var group = repository.detail(identity.userId(), groupId);
        if (group == null) throw new Problem(404, "GROUP_NOT_BOUND");
        return group;
    }

    public synchronized void unbind(MiniappSessions.Identity identity, String groupId) throws SQLException {
        validateGroupId(groupId);
        repository.unbind(identity.userId(), groupId);
        Challenge challenge = pending.get(identity.userId());
        if (challenge != null && challenge.groupId().equals(groupId)) {
            pending.remove(identity.userId());
            attempts.remove(identity.userId());
        }
    }

    private static void validateGroupId(String groupId) {
        if (groupId == null || !groupId.matches("[A-Za-z0-9_-]{1,256}")) throw new Problem(400, "INVALID_GROUP");
    }

    private synchronized Challenge pending(String userId) {
        cleanup();
        return pending.get(userId);
    }

    public synchronized Challenge start(MiniappSessions.Identity identity, String input) throws SQLException {
        if (input == null || !input.trim().matches("[A-Za-z0-9_-]{1,256}")) throw new Problem(400, "INVALID_GROUP");
        cleanup();
        Challenge old = pending.get(identity.userId());
        String groupId = repository.resolve(input.trim());
        if (groupId == null) throw new Problem(404, "GROUP_NOT_FOUND");
        if (old != null && old.groupId().equals(groupId)) return old;
        if (old != null && old.expiresAt() - TTL + 30_000 > clock.millis()) throw new Problem(429, "TRY_LATER");
        if (pending.size() >= 4096) throw new Problem(429, "TRY_LATER");
        byte[] bytes = new byte[6];
        random.nextBytes(bytes);
        String code = HexFormat.of().withUpperCase().formatHex(bytes);
        Challenge challenge = new Challenge(groupId, code, prefix.get() + "群绑定 " + code, clock.millis() + TTL);
        pending.put(identity.userId(), challenge);
        attempts.remove(identity.userId());
        return challenge;
    }

    public synchronized Verification verify(String userId, String groupId, String code, PlatformRole role) {
        if (role != PlatformRole.OWNER) return Verification.NOT_OWNER;
        Challenge challenge = pending.get(userId);
        if (challenge == null) return Verification.NO_PENDING;
        if (challenge.expiresAt() <= clock.millis()) {
            cleanup();
            return Verification.EXPIRED;
        }
        if (!challenge.groupId().equals(groupId)) return Verification.WRONG_GROUP;
        if (attempts.getOrDefault(userId, 0) >= 5) return Verification.TOO_MANY_ATTEMPTS;
        if (code == null || !challenge.code().equalsIgnoreCase(code.trim())) {
            attempts.merge(userId, 1, Integer::sum);
            return Verification.WRONG_CODE;
        }
        try {
            repository.bind(userId, groupId, Instant.ofEpochMilli(clock.millis()).toString());
        } catch (SQLException e) {
            org.slf4j.LoggerFactory.getLogger(MiniappGroupService.class).warn("保存群绑定失败", e);
            return Verification.SAVE_FAILED;
        }
        pending.remove(userId);
        attempts.remove(userId);
        return Verification.SUCCESS;
    }

    private void cleanup() {
        pending.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= clock.millis());
        attempts.keySet().retainAll(pending.keySet());
    }
}
