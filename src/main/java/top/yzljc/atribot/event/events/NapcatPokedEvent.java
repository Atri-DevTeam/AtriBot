package top.yzljc.atribot.event.events;

import lombok.Getter;
import top.yzljc.atribot.chat.napcat.SendPoke;
import top.yzljc.atribot.event.Event;

import java.util.Objects;

/**
 * @Author YZ_Ljc_
 * @ClassName GetPokeEvent
 * @Created_at 2026/04/04
 * @Project AtriBot
 * @Package top.yzljc.qqbot.event.impl
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