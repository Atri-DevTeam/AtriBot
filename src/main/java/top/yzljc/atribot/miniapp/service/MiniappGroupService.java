package top.yzljc.atribot.miniapp.service;

import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.function.command.PushTaskCommand;
import top.yzljc.atribot.database.repo.GroupBindingRepository;
import top.yzljc.atribot.miniapp.MiniappSessions;
import top.yzljc.atribot.platform.PlatformRole;

import java.security.SecureRandom;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * @Author YZ_Ljc_
 * @ClassName MiniappGroupService
 * @Created_at 2026/09/20
 * @Project AtriMeow
 * @Package top.yzljc.atribot.miniapp.service
 */
public final class MiniappGroupService {
    public static final MiniappGroupService INSTANCE = new MiniappGroupService(new GroupBindingRepository(), Clock.systemUTC(),
            () -> Config.getInstance().getCommandPrefix());
    private static final long TTL = 10 * 60_000L;
    private final GroupBindingRepository repository;
    private final Clock clock;
    private final Supplier<String> prefix;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Challenge> pending = new HashMap<>();
    private final Map<String, Integer> attempts = new HashMap<>();

    public record Challenge(String groupId, String code, String command, long expiresAt) {
    }

    public record Overview(List<GroupBindingRepository.Binding> groups, Challenge pending) {
    }

    public record WelcomeButton(String label, String style) {}
    public record Welcome(boolean enabled, boolean custom, String text, String buttonSize,
                          List<List<WelcomeButton>> keyboard) {}

    public Welcome welcome(MiniappSessions.Identity identity, String groupId) throws SQLException {
        validateGroupId(groupId);
        var stored = repository.welcome(identity.userId(), groupId);
        if (stored == null) throw new Problem(404, "GROUP_NOT_BOUND");
        boolean enabled = stored.enabled() != null ? stored.enabled() : PushTaskCommand.isFunctionDefaultEnabled("member_add_welcome");
        var config = stored.config();
        boolean custom = config != null && (!config.path("text").asText("").isBlank()
                || config.path("keyboard").isArray() && !config.path("keyboard").isEmpty());
        if (!custom) {
            // The ordinary new-member default from GroupJoinWelcome; role-specific greetings are event-dependent.
            String text = "欢迎新人喵~\n\n" + Markdown.img(ResourcesProperties.WELCOME_IMG, 1858, 846) + "\n\n> 关闭欢迎提示";
            return new Welcome(enabled, false, text, "SMALL", List.of(List.of(
                    new WelcomeButton("打卡", "BLUE"), new WelcomeButton("帮助", "BLUE"), new WelcomeButton("自定义欢迎", "BLUE"))));
        }
        List<List<WelcomeButton>> keyboard = new ArrayList<>();
        if (config.path("keyboard").isArray()) for (var row : config.path("keyboard")) {
            if (!row.isArray()) continue;
            List<WelcomeButton> buttons = new ArrayList<>();
            for (var button : row) if (button.isObject()) buttons.add(new WelcomeButton(
                    button.path("display_text").asText("未命名按钮"), button.path("style").asText("BLUE")));
            if (!buttons.isEmpty()) keyboard.add(List.copyOf(buttons));
        }
        return new Welcome(enabled, true, config.path("text").asText(""),
                config.path("button_size").asText("UNDEFINED"), List.copyOf(keyboard));
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
        this.repository = repository;
        this.clock = clock;
        this.prefix = prefix;
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
