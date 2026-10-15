package top.yzljc.atricore.event;

import lombok.Getter;

/**
 * 事件处理顺序，按 LOWEST 至 MONITOR 依次执行
 *
 * @Author YZ_Ljc_
 * @ClassName EventPriority
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.event
 */
@Getter
public enum EventPriority {
    LOWEST(0),
    LOW(1),
    NORMAL(2),
    HIGH(3),
    HIGHEST(4),
    MONITOR(5);

    private final int slot;

    EventPriority(int slot) {
        this.slot = slot;
    }

}
