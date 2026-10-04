package top.yzljc.atribot.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * @Author YZ_Ljc_
 * @ClassName HashUtils
 * @Created_at 2026/10/04
 * @Project AtriMeow
 * @Package top.yzljc.atribot.utils
 */
public final class HashUtils {
    private HashUtils() {
    }

    /**
     * 计算 UTF-8 编码文本的 SHA-256 摘要
     *
     * @param text 原始文本，不进行空白裁剪或其他规范化
     * @return 64 位小写十六进制摘要
     * @throws NullPointerException text 为 null
     */
    public static String sha256Hex(String text) {
        return sha256Hex(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算原始字节的 SHA-256 摘要，每次调用使用独立的摘要实例
     *
     * @param data 原始字节
     * @return 64 位小写十六进制摘要
     * @throws NullPointerException data 为 null
     */
    public static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
