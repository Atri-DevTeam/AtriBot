package top.yzljc.atricore.event.events;

import lombok.Getter;
import top.yzljc.atricore.chat.napcat.SendPoke;
import top.yzljc.atricore.event.Event;

import java.util.Objects;

/**
 * 收到 {@code notice_type = notify}、{@code sub_type = poke} 的戳一戳通知时触发，入口排除机器人自身发起的操作
 *
 * @Author YZ_Ljc_
 * @ClassName NapcatPokedEvent
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.event.events
 */
@Getter
public class NapcatPokedEvent extends Event {
    private final String time;
    private final String selfId;
    private final String targetId;
    private final String userId;
    private final String groupId;

    public NapcatPokedEvent(String time, String selfId, String targetId, String userId, String groupId) {
        this.time = time;
        this.selfId = selfId;
        this.targetId = targetId;
        this.userId = userId;
        this.groupId = groupId;
    }


    public void pokeBack() {
        if (Objects.equals(this.userId, this.selfId)) {
            return;
        }
        SendPoke.poke(this.userId, this.groupId);
    }

    public void poke(String reTargetedId) {
        SendPoke.poke(reTargetedId, this.groupId);
    }
}