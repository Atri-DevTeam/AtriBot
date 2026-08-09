package top.yzljc.atribot.event.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import top.yzljc.atribot.event.Event;
import top.yzljc.atribot.event.impl.GroupJoinRequestSource;

/**
 * @Author YZ_Ljc_
 * @ClassName OfficialGroupJoinRequestEvent
 * @Created_at 2026/08/05
 * @Project AtriMeow
 * @Package top.yzljc.atribot.event.events
 */
@Getter
@RequiredArgsConstructor
public class OfficialGroupJoinRequestEvent extends Event {

    private final String eventId;
    private final String groupOpenId;
    private final String memberOpenId;
    private final String username;
    private final String joinRequestId;
    private final String applyTime;
    private final GroupJoinRequestSource applySource;
    private final String verifyMethod;
    private final String verifyMessage;
    /** 仅在 applyScore 为 {@code INVITED} 时此字段有数据 */
    @Setter
    private String invitedBy;

    public boolean deny() {
        // TODO: 拒绝加群
        return false;
    }

    public boolean approve() {
        // TODO: 同意加群
        return false;
    }

    public String getVerifyQuestion() {
        if (this.verifyMessage == null || this.verifyMessage.isEmpty() || !this.verifyMessage.startsWith("问题：")) {
            return null;
        }
        String[] parts = this.verifyMessage.split("\n答案：");
        return parts[0].replace("问题：", "").trim();
    }

    public String getVerifyAnswer() {
        if (this.verifyMessage == null || this.verifyMessage.isEmpty()) {
            return null;
        }
        if (!this.verifyMessage.startsWith("问题：")) {
            return this.verifyMessage;
        }
        if (this.verifyMessage.contains("\n答案：")) {
            String[] parts = this.verifyMessage.split("\n答案：");
            return parts[1].trim();
        } else {
            return null;
        }
    }
}