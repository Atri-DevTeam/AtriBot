package top.yzljc.atricore.event.events;

import lombok.Getter;
import top.yzljc.atricore.event.Event;
import top.yzljc.atricore.event.impl.ErrorCode;

/**
 * 群消息发送接口返回无主动消息权限错误时触发
 *
 * @Author YZ_Ljc_
 * @ClassName OfficialGroupSendFailEvent
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.event.events
 */
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