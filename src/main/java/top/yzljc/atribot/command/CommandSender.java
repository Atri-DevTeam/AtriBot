package top.yzljc.atribot.command;

/**
 * @Author YZ_Ljc_
 * @ClassName CommandSender
 * @Created_at 2026/08/09
 * @Project AtriMeow
 * @Package top.yzljc.atribot.command
 */
public interface CommandSender {

    String getUserId();

    String getUsername();

    boolean hasPermission();

    boolean hasPermission(String permission);

    String sendMessage(String text);
}