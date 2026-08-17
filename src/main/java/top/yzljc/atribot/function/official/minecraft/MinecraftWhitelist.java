package top.yzljc.atribot.function.official.minecraft;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.configuration.Properties;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Slf4j
public final class MinecraftWhitelist implements CommandExecutor {

    private static final Path DATA_FILE = Path.of(Properties.MINECRAFT_NAME_WHITELIST);
    private static final Pattern VALID_USERNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final TypeReference<List<NameApplication>> LIST_TYPE = new TypeReference<>() {};

    private static List<NameApplication> applications;
    /**
     * 判断玩家名是否已经审核通过。玩家名比较不区分大小写。
     */
    public static synchronized boolean isNameWhitelisted(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        ensureLoaded();
        String key = username.trim().toLowerCase(Locale.ROOT);
        return applications.stream().anyMatch(item -> item.approvedAt() != null
                && item.username().toLowerCase(Locale.ROOT).equals(key));
    }

    /**
     * 提交一个待审核玩家名，供指令或其他业务入口调用。
     */
    public static synchronized NameApplication submit(String userId, String username) {
        String applicant = requireUserId(userId);
        String name = requireUsername(username);
        ensureLoaded();
        if (findIndex(name) >= 0) {
            throw new IllegalArgumentException("该玩家名已经提交过申请");
        }
        NameApplication application = new NameApplication(applicant, name, Instant.now().toString(), null);
        applications.add(application);
        save();
        return application;
    }

    public static synchronized List<NameApplication> list() {
        ensureLoaded();
        return List.copyOf(applications);
    }

    public static synchronized NameApplication approve(String username) {
        String name = requireUsername(username);
        ensureLoaded();
        int index = findIndex(name);
        if (index < 0) {
            throw new IllegalArgumentException("玩家名申请不存在");
        }
        NameApplication current = applications.get(index);
        if (current.approvedAt() != null) {
            return current;
        }
        NameApplication approved = new NameApplication(
                current.userId(), current.username(), current.appliedAt(), Instant.now().toString());
        applications.set(index, approved);
        save();
        return approved;
    }

    /** 拒绝待审核申请或移除已通过的白名单记录。 */
    public static synchronized boolean remove(String username) {
        String name = requireUsername(username);
        ensureLoaded();
        int index = findIndex(name);
        if (index < 0) {
            return false;
        }
        applications.remove(index);
        save();
        return true;
    }

    private static void ensureLoaded() {
        if (applications != null) {
            return;
        }
        if (!Files.exists(DATA_FILE)) {
            applications = new ArrayList<>();
            return;
        }
        try {
            List<NameApplication> loaded = MAPPER.readValue(DATA_FILE.toFile(), LIST_TYPE);
            applications = loaded == null ? new ArrayList<>() : new ArrayList<>(loaded);
            validateStoredData(applications);
            log.info("已加载 Minecraft 玩家名审核记录，共 {} 条", applications.size());
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("读取 Minecraft 玩家名审核数据失败: " + DATA_FILE, e);
        }
    }

    private static void validateStoredData(List<NameApplication> loaded) {
        for (NameApplication item : loaded) {
            if (item == null || item.userId() == null || item.userId().isBlank()
                    || item.username() == null || !VALID_USERNAME.matcher(item.username()).matches()
                    || item.appliedAt() == null || item.appliedAt().isBlank()) {
                throw new IllegalArgumentException("玩家名审核数据中存在无效记录");
            }
        }
        long distinctNames = loaded.stream()
                .map(item -> item.username().toLowerCase(Locale.ROOT))
                .distinct()
                .count();
        if (distinctNames != loaded.size()) {
            throw new IllegalArgumentException("玩家名审核数据中存在重复玩家名");
        }
    }

    private static void save() {
        Path absoluteFile = DATA_FILE.toAbsolutePath();
        Path parent = absoluteFile.getParent();
        Path temporary = null;
        try {
            Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, "whitelist_name-", ".tmp");
            MAPPER.writeValue(temporary.toFile(), applications);
            try {
                Files.move(temporary, absoluteFile,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, absoluteFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("保存 Minecraft 玩家名审核数据失败", e);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // 最终文件已经写入时，清理临时文件失败不影响业务结果。
                }
            }
        }
    }

    private static int findIndex(String username) {
        for (int i = 0; i < applications.size(); i++) {
            if (applications.get(i).username().equalsIgnoreCase(username)) {
                return i;
            }
        }
        return -1;
    }

    private static String requireUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("申请人 user_id 不能为空");
        }
        return userId.trim();
    }

    private static String requireUsername(String username) {
        String value = username == null ? "" : username.trim();
        if (!VALID_USERNAME.matcher(value).matches()) {
            throw new IllegalArgumentException("Minecraft 玩家名必须为 3-16 位字母、数字或下划线");
        }
        return value;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof QQCommandSender user) {
            if (args.length != 1) {
                user.sendMessage("参数内容不正确！");
                return true;
            }

            String username = args[0];
            submit(sender.getUserId(), username);
            user.sendMessage("提交成功，我们不会通知结果，若长时间无变化则未通过。");
            return true;
        }

        return true;
    }

    public record NameApplication(
            @JsonProperty("user_id") String userId,
            @JsonProperty("username") String username,
            @JsonProperty("applied_at") String appliedAt,
            @JsonProperty("approved_at") String approvedAt
    ) {
    }
}
