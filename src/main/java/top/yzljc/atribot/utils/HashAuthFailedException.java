package top.yzljc.atribot.utils;

/**
 * @Author YZ_Ljc_
 * @ClassName HashAuthFailedException
 * @Created_at 2026/07/12
 * @Project AtriMeow
 * @Package top.yzljc.atribot.utils
 */
public class HashAuthFailedException extends Exception {
    public HashAuthFailedException(String message) {
        super("图源校验失败: " + message);
    }
}