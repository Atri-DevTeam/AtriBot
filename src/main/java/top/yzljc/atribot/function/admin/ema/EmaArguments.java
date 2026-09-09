package top.yzljc.atribot.function.admin.ema;

import top.yzljc.sakuraba_ema.guild.impl.ChannelCliOptions;

import java.time.Duration;
import java.util.*;

/**
 * @Author YZ_Ljc_
 * @ClassName EmaArguments
 * @Created_at 2026/09/08
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.admin.ema
 * @Description /ema 的位置参数与可选项，只处理现有指令传入的 args
 */
record EmaArguments(Map<String, Object> values, ChannelCliOptions options) {
    enum Kind { TEXT, INTEGER, BOOLEAN, LIST, DURATION }

    record Parameter(String name, String label, Kind kind, boolean optional, boolean remainder,
                     String description, List<String> choices) {
        String usage() {
            if (optional) return "[--" + name + (kind == Kind.BOOLEAN ? "" : " <" + label + ">") + "]";
            return "<" + label + (remainder ? "..." : "") + ">";
        }
    }

    String text(String key) { return (String) values.get(key); }
    Integer number(String key) { return (Integer) values.get(key); }
    Boolean bool(String key) { return (Boolean) values.get(key); }
    Duration duration(String key) { return (Duration) values.get(key); }
    @SuppressWarnings("unchecked")
    List<String> list(String key) { return (List<String>) values.get(key); }

    static EmaArguments parse(EmaRoutes.Route route, String[] raw) {
        List<String> tokens = tokenize(String.join(" ", raw));
        Map<String, Object> values = new HashMap<>();
        boolean yes = false;
        boolean dryRun = false;
        int offset = 0;
        // 可选项放在位置参数前，正文开始之后的 --yes 等文字不会被当作执行选项。
        while (offset < tokens.size() && tokens.get(offset).startsWith("--")) {
            String token = tokens.get(offset++);
            if (token.equals("--")) break;
            if (token.equals("--yes")) { yes = true; continue; }
            if (token.equals("--dry-run")) { dryRun = true; continue; }
            String[] pair = token.substring(2).split("=", 2);
            Parameter parameter = route.parameters().stream()
                    .filter(p -> p.optional() && p.name().equals(pair[0]))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("未知选项：" + pair[0]));
            if (values.containsKey(parameter.name())) throw new IllegalArgumentException("选项重复：" + pair[0]);
            String value;
            if (pair.length == 2) value = pair[1];
            else if (parameter.kind() == Kind.BOOLEAN) value = "true";
            else {
                if (offset == tokens.size() || tokens.get(offset).startsWith("--")) {
                    throw new IllegalArgumentException("--" + pair[0] + " 缺少值");
                }
                value = tokens.get(offset++);
            }
            values.put(parameter.name(), convert(parameter, value));
        }
        if (!route.supportsOptions() && (yes || dryRun)) {
            throw new IllegalArgumentException("此方法不支持 --yes / --dry-run");
        }
        for (Parameter parameter : route.parameters()) {
            if (parameter.optional()) continue;
            if (offset == tokens.size()) throw new IllegalArgumentException("缺少参数：" + parameter.label());
            String value;
            if (parameter.remainder()) {
                value = String.join(" ", tokens.subList(offset, tokens.size()));
                offset = tokens.size();
                value = decodeText(value);
            } else {
                value = tokens.get(offset++);
            }
            values.put(parameter.name(), convert(parameter, value));
        }
        if (offset < tokens.size()) throw new IllegalArgumentException("参数过多；可选项应放在频道 ID 等位置参数之前");
        return new EmaArguments(Map.copyOf(values), new ChannelCliOptions(yes, dryRun));
    }

    private static Object convert(Parameter parameter, String value) {
        if (value.isBlank()) throw new IllegalArgumentException(parameter.label() + " 不能为空");
        if (!parameter.choices().isEmpty() && !parameter.choices().contains(value)) {
            throw new IllegalArgumentException(parameter.label() + " 可选值：" + String.join("、", parameter.choices()));
        }
        return switch (parameter.kind()) {
            case TEXT -> value;
            case BOOLEAN -> {
                if (!value.equals("true") && !value.equals("false")) {
                    throw new IllegalArgumentException(parameter.label() + " 只能是 true 或 false");
                }
                yield Boolean.valueOf(value);
            }
            case INTEGER -> {
                try {
                    int number = Integer.parseInt(value);
                    if (number < 0 || (Set.of("ref", "count", "num", "page-num").contains(parameter.name()) && number == 0)) {
                        throw new NumberFormatException();
                    }
                    yield number;
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(parameter.label() + " 必须是范围内的非负整数（编号、数量至少为 1）");
                }
            }
            case LIST -> {
                List<String> items = Arrays.asList(value.split("[,，]", -1));
                if (items.stream().anyMatch(String::isBlank)) {
                    throw new IllegalArgumentException(parameter.label() + " 用逗号分隔，不能有空项");
                }
                yield items.stream().map(String::trim).toList();
            }
            case DURATION -> {
                var matcher = java.util.regex.Pattern.compile("([0-9]+)([smhd]?)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(value);
                if (!matcher.matches()) throw new IllegalArgumentException("时长示例：30s、10m、2h、1d；不带单位时按秒");
                long multiplier = matcher.group(2).equalsIgnoreCase("m") ? 60
                        : matcher.group(2).equalsIgnoreCase("h") ? 3600
                        : matcher.group(2).equalsIgnoreCase("d") ? 86400 : 1;
                try {
                    long seconds = Math.multiplyExact(Long.parseLong(matcher.group(1)), multiplier);
                    if (seconds < 1) throw new ArithmeticException();
                    // 还需能转换成绝对到期时间。
                    java.time.Instant.now().plusSeconds(seconds);
                    yield Duration.ofSeconds(seconds);
                } catch (RuntimeException e) {
                    throw new IllegalArgumentException("禁言时长超出范围，且必须至少为一秒");
                }
            }
        };
    }

    /** 引号只用于参数分组；Windows 路径中的反斜杠保留。 */
    private static List<String> tokenize(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        char quote = 0;
        boolean started = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                } else if (c == '\\' && i + 1 < input.length() && input.charAt(i + 1) == quote) {
                    token.append(input.charAt(++i));
                } else token.append(c);
                started = true;
            } else if ((c == '"' || c == '\'') && (token.isEmpty() || token.charAt(token.length() - 1) == '=')) {
                quote = c;
                started = true;
            } else if (Character.isWhitespace(c)) {
                if (started) { tokens.add(token.toString()); token.setLength(0); started = false; }
            } else {
                token.append(c);
                started = true;
            }
        }
        if (quote != 0) throw new IllegalArgumentException("参数引号没有闭合");
        if (started) tokens.add(token.toString());
        return tokens;
    }

    /** 仅正文解析转义，不改变 ID、翻页令牌与文件路径。 */
    private static String decodeText(String text) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                char next = text.charAt(i + 1);
                switch (next) {
                    case 'n' -> { result.append('\n'); i++; continue; }
                    case 't' -> { result.append('\t'); i++; continue; }
                    case 's' -> { result.append(' '); i++; continue; }
                    case '\\' -> { result.append('\\'); i++; continue; }
                    default -> { }
                }
            }
            result.append(c);
        }
        return result.toString();
    }
}

