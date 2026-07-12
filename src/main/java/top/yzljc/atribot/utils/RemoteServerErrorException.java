package top.yzljc.atribot.utils;

/**
 * @Author YZ_Ljc_
 * @ClassName RemoteServerErrorException
 * @Created_at 2026/07/13
 * @Project AtriMeow
 * @Package top.yzljc.atribot.utils
 */
public class RemoteServerErrorException extends Exception {
    public RemoteServerErrorException(String message) {
        super("远程服务器连接错误: " + message);
    }

    public RemoteServerErrorException(String message, Throwable cause) {
        super("远程服务器连接错误: " + message, cause);
    }
}