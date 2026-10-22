package top.yzljc.atricore.event.events;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import top.yzljc.atricore.chat.official.management.JoinRequestApproval;
import top.yzljc.atricore.event.Event;
import top.yzljc.atricore.event.impl.RequestSource;
import top.yzljc.atricore.event.impl.VerifyMethod;

import java.util.List;

/**
 * 收到入群申请 {@code GROUP_JOIN_REQUEST} 时触发
 *
 * @Author YZ_Ljc_
 * @ClassName OfficialGroupJoinRequestEvent
 * @Created_at 2026/08/05
 * @Project AtriMeow
 * @Package top.yzljc.atricore.event.events
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
    private final RequestSource applySource;
    private final VerifyMethod method;
    /**
     * 仅在 applyScore 为 {@code INVITED} 时此字段有数据
     */
    @Setter
    private String invitedBy;
    /**
     * 仅在 method 为 {@code VerifyMethod.VERIFY_MESSAGE} 时此字段有数据
     */
    @Setter
    private String verifyMessage;
    /**
     * 仅在 method 为 {@code VerifyMethod.ADMIN_REVIEW_QA} 时此字段有数据
     */
    @Setter
    @Getter
    private List<JoinRequestApproval.ReviewQa> verifyQAList;
    /**
     * 仅在触发自动同意策略后该字段有数据
     */
    @Getter
    @Setter
    private String strategyId;

    /**
     * 拒绝入群申请
     *
     * @return 拒绝成功返回 true，失败返回 false
     */
    public boolean deny() {
        return JoinRequestApproval.declineJoinRequest(this.groupOpenId, this.memberOpenId, this.joinRequestId);
    }

    /**
     * 拒绝入群申请并提供拒绝理由
     *
     * @param reason 拒绝理由
     * @return 拒绝成功返回 true，失败返回 false
     */
    public boolean deny(String reason) {
        return JoinRequestApproval.declineJoinRequest(this.groupOpenId, this.memberOpenId, this.joinRequestId, reason, false);
    }

    /**
     * 拒绝入群申请并提供拒绝理由，同时选择是否屏蔽该用户
     *
     * @param reason 拒绝理由
     * @param block  是否屏蔽该用户
     * @return 拒绝成功返回 true，失败返回 false
     */
    public boolean deny(String reason, boolean block) {
        return JoinRequestApproval.declineJoinRequest(this.groupOpenId, this.memberOpenId, this.joinRequestId, reason, block);
    }

    /**
     * 拒绝入群申请并选择是否屏蔽该用户
     *
     * @param block 是否屏蔽该用户
     * @return 拒绝成功返回 true，失败返回 false
     */
    public boolean deny(boolean block) {
        return JoinRequestApproval.declineJoinRequest(this.groupOpenId, this.memberOpenId, this.joinRequestId, null, block);
    }

    /**
     * 同意入群申请
     *
     * @return 同意成功返回 true，失败返回 false
     */
    public boolean approve() {
        return JoinRequestApproval.approveJoinRequest(this.groupOpenId, this.memberOpenId, this.joinRequestId);
    }

    /**
     * @Description 遵照当前QQ的设计模式，Question只能是一个，所以默认返回第一个
     */
    public String getQuestion() {
        if (this.method == VerifyMethod.VERIFY_MESSAGE) {
            return null;
        }
        return this.verifyQAList.getFirst().question();
    }

    public String getAnswer() {
        if (this.method == VerifyMethod.VERIFY_MESSAGE) {
            return this.verifyMessage;
        }
        return this.verifyQAList.getFirst().answer();
    }
}