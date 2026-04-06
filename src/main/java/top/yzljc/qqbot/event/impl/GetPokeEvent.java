package top.yzljc.qqbot.event.impl;

import lombok.Getter;
import top.yzljc.qqbot.chat.SendPoke;
import top.yzljc.qqbot.event.Event;

/**
 * @Author YZ_Ljc_
 * @ClassName GetPokeEvent
 * @Created_at 2026/04/04
 * @Project AtriBot
 * @Package top.yzljc.qqbot.event.impl
 */
@Getter
public class GetPokeEvent extends Event {
    private final long time;
    private final long selfId;
    private final long targetId;
    private final long userId;
    private final long groupId;

    public GetPokeEvent(long time, long selfId, long targetId, long userId, long groupId) {
        this.time = time;
        this.selfId = selfId;
        this.targetId = targetId;
        this.userId = userId;
        this.groupId = groupId;
    }

    public void pokeBack() {
        if (this.userId == this.selfId) {
            return;
        }
        SendPoke.poke(this.userId, this.groupId);
    }

    public void poke(long reTargetedId) {
        SendPoke.poke(reTargetedId, this.groupId);
    }
}