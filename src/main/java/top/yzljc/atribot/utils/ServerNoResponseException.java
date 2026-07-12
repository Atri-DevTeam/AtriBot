package top.yzljc.atribot.utils;

/**
 * @Author YZ_Ljc_
 * @ClassName ServerNoResponseException
 * @Created_at 2026/07/12
 * @Project AtriMeow
 * @Package top.yzljc.atribot.utils
 */
public class ServerNoResponseException extends RuntimeException {
    public ServerNoResponseException() {
        super("ugc源服务器未返回内容");
    }
}
