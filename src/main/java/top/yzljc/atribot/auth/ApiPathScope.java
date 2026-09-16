package top.yzljc.atribot.auth;

/**
 * API 授权范围：精确路径或末尾 /*（包含该目录本身及所有后代）。
 *
 * @Author YZ_Ljc_
 * @ClassName ApiPathScope
 * @Created_at 2026/09/13
 * @Project AtriMeow
 * @Package top.yzljc.atribot.auth
 */
public final class ApiPathScope {
    private ApiPathScope() {}

    public static String validate(String pattern) {
        if (pattern == null) throw new IllegalArgumentException("API 路径不能为空");
        String path = pattern.endsWith("/*") ? pattern.substring(0, pattern.length() - 2) : pattern;
        if (path.isEmpty() || !isCanonical(path) || path.contains("*") || path.endsWith("/")) {
            throw new IllegalArgumentException("API 范围须为精确路径或目录/*，且不能授权根目录");
        }
        return pattern;
    }

    public static boolean matches(String pattern, String path) {
        if (!isCanonical(path)) return false;
        if (!pattern.endsWith("/*")) return pattern.equals(path);
        String prefix = pattern.substring(0, pattern.length() - 2);
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    // 不对有歧义的路径做“修复”，避免鉴权和路由使用不同的路径解释。
    public static boolean isCanonical(String path) {
        return path != null && path.startsWith("/") && !path.contains("//")
                && !path.contains("\\") && !path.contains("%") && !path.contains(";")
                && !path.contains("?") && !path.contains("#") && !path.contains("/./")
                && !path.contains("/../") && !path.endsWith("/.") && !path.endsWith("/..")
                && path.chars().noneMatch(c -> Character.isISOControl(c) || Character.isWhitespace(c));
    }
}
