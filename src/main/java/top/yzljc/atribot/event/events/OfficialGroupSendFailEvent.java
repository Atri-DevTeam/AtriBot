package top.yzljc.atribot.event.events;

import lombok.Getter;
import top.yzljc.atribot.event.Event;
import top.yzljc.atribot.event.impl.ErrorCode;

@Getter
public class OfficialGroupSendFailEvent extends Event {
    private final String groupOpenId;
    private final int errorCode;
    private final String errorMessage;

    public OfficialGroupSendFailEvent(String groupOpenId, int errorCode, String errorMessage) {
        this.groupOpenId = groupOpenId;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public ErrorCode getErrorCodeEnum() {
        return ErrorCode.fromErrorCode(errorCode);
    }
}