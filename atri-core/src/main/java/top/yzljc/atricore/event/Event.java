package top.yzljc.atricore.event;

/**
 * 事件基类，事件管理器按具体运行时类型匹配监听器
 *
 * @Author YZ_Ljc_
 * @ClassName Event
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.event
 */
public abstract class Event {
    public String getEventName() {
        return getClass().getSimpleName();
    }
}
