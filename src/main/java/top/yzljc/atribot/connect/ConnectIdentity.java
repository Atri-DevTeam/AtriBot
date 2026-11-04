package top.yzljc.atribot.connect;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Connect 查询身份，三个标识仅允许提供一个 */
public record ConnectIdentity(String key, String value) {
    private static final Set<String> KEYS = Set.of("b_id", "c_id", "u_id");

    public ConnectIdentity {
        if (!KEYS.contains(key) || value == null || value.isBlank() || value.length() > 128
                || value.chars().anyMatch(Character::isWhitespace) || value.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("用户标识无效");
        if (key.equals("u_id")) {
            if (!UUID.fromString(value).toString().equalsIgnoreCase(value)) throw new IllegalArgumentException("UUID 无效");
            value = value.toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static ConnectIdentity parse(Map<String, List<String>> params) {
        ConnectIdentity identity = null;
        for (var entry : params.entrySet()) {
            if ((!KEYS.contains(entry.getKey()) && !entry.getKey().equals("ticket")) || entry.getValue().size() != 1)
                throw new IllegalArgumentException("查询参数无效");
            if (!KEYS.contains(entry.getKey())) continue;
            if (identity != null) throw new IllegalArgumentException("用户标识只能提供一个");
            identity = new ConnectIdentity(entry.getKey(), entry.getValue().getFirst());
        }
        if (identity == null) throw new IllegalArgumentException("缺少用户标识");
        return identity;
    }

    String column() {
        return switch (key) {
            case "b_id" -> "qq_user_open_id";
            case "c_id" -> "qq_connect_open_id";
            case "u_id" -> "uuid";
            default -> throw new IllegalStateException();
        };
    }
}
