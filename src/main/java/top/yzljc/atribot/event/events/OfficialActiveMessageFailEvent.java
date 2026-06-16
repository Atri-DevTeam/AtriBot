package top.yzljc.atribot.event.events;

import lombok.Getter;
import top.yzljc.atribot.event.Event;

@Getter
public class OfficialActiveMessageFailEvent extends Event {
    private final String groupOpenId;
    private final int errorCode;
    private final String errorMessage;

    public OfficialActiveMessageFailEvent(String groupOpenId, int errorCode, String errorMessage) {
        this.groupOpenId = groupOpenId;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }
}
