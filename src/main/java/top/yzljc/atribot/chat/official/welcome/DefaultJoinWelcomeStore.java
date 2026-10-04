package top.yzljc.atribot.chat.official.welcome;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import top.yzljc.atribot.auth.official.UnifiedRole;
import top.yzljc.atribot.configuration.Properties;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.function.impl.JoinWelcomeDAO;
import top.yzljc.atribot.platform.qq.QQBot;
import top.yzljc.atribot.utils.AtomicFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * @Author YZ_Ljc_
 * @ClassName DefaultJoinWelcomeStore
 * @Created_at 2026/10/04
 * @Project AtriMeow
 * @Package top.yzljc.atribot.chat.official.welcome
 */
public final class DefaultJoinWelcomeStore {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static DefaultJoinWelcomeStore instance;
    private final Path file;
    private final ObjectNode initialDefaults;
    private ObjectNode settings;

    DefaultJoinWelcomeStore(Path file, ObjectNode initialDefaults) {
        this.file = file;
        this.initialDefaults = validateAll(initialDefaults);
    }

    public static synchronized DefaultJoinWelcomeStore getInstance() throws IOException {
        if (instance == null) {
            instance = new DefaultJoinWelcomeStore(Path.of(Properties.JOIN_WELCOME_DEFAULTS),
                    loadInitialDefaults(QQBot.BOT_NAME, ResourcesProperties.WELCOME_IMG, ResourcesProperties.WELCOME_DEV_IMG));
        }
        return instance;
    }

    /**
     * 获取指定机器人权限身份的默认欢迎配置副本
     *
     * @param role 新成员的机器人权限身份
     * @return 独立配置副本，修改副本不影响已保存配置
     * @throws IOException 配置读取或首次初始化失败
     */
    public synchronized ObjectNode get(UnifiedRole role) throws IOException {
        ensureLoaded();
        return ((ObjectNode) settings.get(Objects.requireNonNull(role, "role").name())).deepCopy();
    }

    /**
     * 保存指定身份的默认欢迎，成功落盘后更新内存配置
     *
     * @param role 新成员的机器人权限身份
     * @param config 欢迎正文和按钮配置
     * @return 保存后的独立配置副本
     * @throws IOException 配置读取或写入失败
     * @throws IllegalArgumentException 欢迎配置不符合编辑器约束
     */
    public synchronized ObjectNode save(UnifiedRole role, JsonNode config) throws IOException {
        Objects.requireNonNull(role, "role");
        ObjectNode validated = JoinWelcomeDAO.validate(config);
        ensureLoaded();
        ObjectNode next = settings.deepCopy();
        next.set(role.name(), validated);
        persist(next);
        settings = next;
        return validated.deepCopy();
    }

    /**
     * 将指定身份恢复为初始模板，保留其他身份的配置
     *
     * @param role 待恢复的机器人权限身份
     * @return 恢复后的独立配置副本
     * @throws IOException 配置读取或写入失败
     */
    public synchronized ObjectNode reset(UnifiedRole role) throws IOException {
        return save(role, initialDefaults.get(Objects.requireNonNull(role, "role").name()));
    }

    private void ensureLoaded() throws IOException {
        if (settings != null) return;
        if (Files.notExists(file)) {
            persist(initialDefaults);
            settings = initialDefaults.deepCopy();
            return;
        }
        try {
            settings = validateAll(MAPPER.readTree(file.toFile()));
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid default join welcome configuration: " + file, e);
        }
    }

    private void persist(ObjectNode value) throws IOException {
        AtomicFiles.write(file, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(value));
    }

    private static ObjectNode validateAll(JsonNode source) {
        if (source == null || !source.isObject() || source.size() != UnifiedRole.values().length) {
            throw new IllegalArgumentException("默认欢迎配置必须包含 USER、ADMIN 和 OWNER");
        }
        ObjectNode validated = MAPPER.createObjectNode();
        for (UnifiedRole role : UnifiedRole.values()) {
            validated.set(role.name(), JoinWelcomeDAO.validate(source.get(role.name())));
        }
        return validated;
    }

    static ObjectNode loadInitialDefaults(String botName, String welcomeImage, String developerImage) throws IOException {
        try (var input = DefaultJoinWelcomeStore.class.getResourceAsStream("/join-welcome-defaults.json")) {
            if (input == null) throw new IOException("Missing resource: join-welcome-defaults.json");
            ObjectNode defaults = validateAll(MAPPER.readTree(input));
            for (UnifiedRole role : UnifiedRole.values()) {
                ObjectNode config = (ObjectNode) defaults.get(role.name());
                config.put("text", config.path("text").asText()
                        .replace("${botName}", botName == null ? "AtriBot" : botName)
                        .replace("${welcomeImage}", welcomeImage)
                        .replace("${developerImage}", developerImage));
            }
            return validateAll(defaults);
        }
    }
}
