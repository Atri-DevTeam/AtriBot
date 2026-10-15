package top.yzljc.atricore.event.events;

import lombok.Getter;
import top.yzljc.atricore.event.Cancellable;
import top.yzljc.atricore.event.Event;
import top.yzljc.atricore.platform.Platform;

/**
 * QQ 官方消息请求发出前同步触发，用于发送审核
 *
 * @Author YZ_Ljc_
 * @ClassName OfficialMessageSendEvent
 * @Created_at 2026/09/23
 * @Project AtriMeow
 * @Package top.yzljc.atricore.event.events
 * @Description 官机消息发送前同步派发的审核事件，监听器取消后不再发送本次消息
 */
@Getter
public class OfficialMessageSendEvent extends Event implements Cancellable {
    private final Platform platform;
    private final String targetId;
    private final String url;
    private final String requestBody;
    private final boolean stream;
    private boolean cancelled;

    /**
     * @param platform 官机平台，区分群聊、单聊、频道消息和频道私信
     * @param targetId 发送目标，依次对应群 openId、用户 openId、子频道 ID 或私信频道 ID
     * @param url 待发送请求的完整 URL
     * @param requestBody 待发送的 JSON 快照，包含回复来源、消息序号及各类消息内容
     * @param stream 是否为单聊流式消息；每次流式更新发送前分别触发事件
     */
    public OfficialMessageSendEvent(Platform platform, String targetId, String url, String requestBody,
                                    boolean stream) {
        this.platform = platform;
        this.targetId = targetId;
        this.url = url;
        this.requestBody = requestBody;
        this.stream = stream;
    }

    /**
     * @param cancelled 是否取消本次发送，默认为 false
     */
    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
