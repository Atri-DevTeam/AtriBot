package top.yzljc.atribot.utils;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 注释与排版完全保留的 config.yml 更新器：逐行定位 dotted-path 的 {@code key: value} 行，
 * 只替换值部分，未涉及的行（含注释、空行、列表缩进、引号风格、行尾注释）逐字节原样保留。
 * <p>
 * 不用 SnakeYAML Node 级 compose/serialize（Bukkit 1.18.1+ 的机制）：该方式虽保留注释，
 * 但 snakeyaml 2.6 的 Emitter 会把嵌套列表缩进和嵌套注释缩进重排（列表 "-" 顶到父键同列），
 * 对手写排版的配置文件仍会造成大面积无关 diff。本类只服务于「更新已存在标量键」这一契约
 * （键不存在即整体失败不落盘），行级编辑在该契约下是严格保真的。
 */
public final class CommentPreservingYaml {

    /** key 行：缩进 + 键 + 冒号 + 可选行内值；键不参与行内注释/列表项匹配 */
    private static final Pattern KEY_LINE = Pattern.compile("^(?<indent>\\s*)(?<key>[^\\s#:-][^:]*?):(?:\\s+(?<value>\\S.*?))?(?<trailing>\\s*)$");

    private CommentPreservingYaml() {
    }

    /**
     * 更新 yml 中若干 dotted-path 键的值，仅改动目标行的值部分。
     *
     * @throws IOException 任一键不存在、目标值不是单行标量，或文件不可读（此时一律不写盘）
     */
    public static void update(Path file, Map<String, Object> changes) throws IOException {
        String original = Files.readString(file, StandardCharsets.UTF_8);
        String eol = original.contains("\r\n") ? "\r\n" : "\n";
        List<String> lines = new ArrayList<>(Arrays.asList(original.split("\r\n|\n", -1)));

        // 缩进栈：栈内是 (indent, key)，随行进出
        List<int[]> indentStack = new ArrayList<>();
        List<String> pathStack = new ArrayList<>();
        Map<String, Object> remaining = new LinkedHashMap<>(changes);

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("-")) {
                continue;
            }
            Matcher matcher = KEY_LINE.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            int indent = matcher.group("indent").length();
            while (!indentStack.isEmpty() && indentStack.getLast()[0] >= indent) {
                indentStack.removeLast();
                pathStack.removeLast();
            }
            indentStack.add(new int[]{indent});
            pathStack.add(matcher.group("key"));
            String path = String.join(".", pathStack);

            if (!remaining.containsKey(path)) {
                continue;
            }
            Object rawValue = remaining.remove(path);
            String oldValue = matcher.group("value");
            if (oldValue == null || oldValue.isEmpty()) {
                throw new IOException("配置项 " + path + " 的值不是单行标量，拒绝更新: " + line.trim());
            }
            // 值部分与行尾注释分离，并沿用旧行的引号风格
            int commentAt = findTrailingComment(oldValue);
            String valueCore = (commentAt >= 0 ? oldValue.substring(0, commentAt) : oldValue).trim();
            String trailing = commentAt >= 0 ? " " + oldValue.substring(commentAt) : "";
            lines.set(i, matcher.group("indent") + matcher.group("key") + ": "
                    + serialize(rawValue, valueCore) + trailing);
        }

        if (!remaining.isEmpty()) {
            throw new IOException("配置文件中不存在以下配置项: " + String.join(", ", remaining.keySet()));
        }
        Files.writeString(file, String.join(eol, lines), StandardCharsets.UTF_8);
    }

    /** 按新值序列化；字符串且旧行带引号时沿用原引号风格，其余交给 snakeyaml 按需加引号 */
    private static String serialize(Object newValue, String oldValueCore) throws IOException {
        if (newValue instanceof String s) {
            if (s.contains("\n") || s.contains("\r")) {
                throw new IOException("配置值不允许包含换行: " + s);
            }
            if (oldValueCore.length() >= 2 && oldValueCore.startsWith("\"") && oldValueCore.endsWith("\"")) {
                return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
            }
            if (oldValueCore.length() >= 2 && oldValueCore.startsWith("'") && oldValueCore.endsWith("'")) {
                return "'" + s.replace("'", "''") + "'";
            }
        }
        return new Yaml().dump(newValue).trim();
    }

    /** 返回值部分中行尾注释 " #" 的起始下标（引号内的 # 忽略），没有则返回 -1 */
    private static int findTrailingComment(String value) {
        char quote = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == '#' && i > 0 && Character.isWhitespace(value.charAt(i - 1))) {
                return i;
            }
        }
        return -1;
    }
}
