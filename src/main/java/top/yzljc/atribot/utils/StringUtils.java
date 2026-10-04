package top.yzljc.atribot.utils;

/**
 * @Author YZ_Ljc_
 * @ClassName StringUtils
 * @Created_at 2026/10/04
 * @Project AtriMeow
 * @Package top.yzljc.atribot.utils
 */
public final class StringUtils {
    private StringUtils() {
    }

    /**
     * 按 {@link String#isBlank()} 的规则判断空白，兼容 null
     *
     * @param value 待判断的字符串
     * @return 为 null、空字符串或全部由空白字符组成时返回 true
     */
    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 在空白判断之外，将 trim 后等于 null 的文本也视为空值，忽略大小写
     *
     * @param value 待判断的字符串
     * @return 为空白或 null 文本时返回 true
     */
    public static boolean isBlankOrNullLiteral(String value) {
        return isBlank(value) || "null".equalsIgnoreCase(value.trim());
    }

    /**
     * 按顺序返回首个非空白字符串，不裁剪返回值
     *
     * @param values 候选字符串，元素可为 null
     * @return 首个非空白字符串，没有符合条件的值时返回 null
     * @throws NullPointerException values 数组为 null
     */
    public static String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 空白字符串转为 null，其余字符串按 {@link String#trim()} 的规则裁剪
     *
     * @param value 待处理的字符串，可为 null
     * @return 空白时返回 null，否则返回 trim 后的字符串
     */
    public static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
