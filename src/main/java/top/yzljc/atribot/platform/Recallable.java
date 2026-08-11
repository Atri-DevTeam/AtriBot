package top.yzljc.atribot.platform;

/**
 * @Author YZ_Ljc_
 * @ClassName Recallable
 * @Created_at 2026/08/09
 * @Project AtriMeow
 * @Package top.yzljc.atribot.platform
 */
public interface Recallable {

    /** @param id 请根据不同平台的撤回方法的参数约定自行适配 */
    boolean recall(String id, String messageId);

    default boolean recall(String messageId) {
        return false;
    }
}