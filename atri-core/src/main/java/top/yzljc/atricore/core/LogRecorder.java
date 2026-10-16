package top.yzljc.atricore.core;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.atricore.chat.official.MessageBody;

/**
 * 运行服务提供的记录与通知扩展点，默认不执行操作
 * <p>回调在调用线程执行，可能并发进入；实现应自行保证线程安全，并控制耗时及异常传播
 *
 * @Author YZ_Ljc_
 * @ClassName LogRecorder
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.core
 */
public class LogRecorder {
    /**
     * 记录接口请求的开始状态
     *
     * @param scene       请求场景
     * @param method      HTTP 方法
     * @param url         请求地址
     * @param requestJson 请求 JSON，没有请求体时可为 null
     * @return 运行服务生成的关联标识，不记录时返回 null；此值不是平台 trace_id
     */
    public String recordSend(String scene, String method, String url, String requestJson) {
        return null;
    }

    /**
     * 记录接口响应
     *
     * @param traceId        recordSend 返回的关联标识，可为 null
     * @param scene          请求场景
     * @param method         HTTP 方法
     * @param url            请求地址
     * @param requestJson    请求 JSON，没有请求体时可为 null
     * @param responseStatus HTTP 状态码，未取得响应时可能为 null 或 0
     * @param responseBody   原始响应内容，可为 null
     */
    public void recordResponse(String traceId, String scene, String method, String url,
                               String requestJson, Integer responseStatus, String responseBody) {
    }

    /**
     * 记录请求或响应处理失败
     *
     * @param traceId        请求关联标识，创建记录前失败时可为 null
     * @param scene          请求场景
     * @param method         HTTP 方法
     * @param url            请求地址
     * @param requestJson    请求 JSON，没有请求体时可为 null
     * @param responseStatus HTTP 状态码，未取得响应时可能为 null 或 0
     * @param responseBody   原始响应内容，可为 null
     * @param errorMessage   失败原因
     */
    public void recordError(String traceId, String scene, String method, String url,
                            String requestJson, Integer responseStatus, String responseBody, String errorMessage) {
    }

    /**
     * 记录 QQ 平台上报的事件包
     *
     * @param eventType 平台事件名
     * @param eventId   平台事件 ID，未提供时可为 null
     * @param seq       平台事件序号，未提供时可为 null
     * @param rawData   原始事件 JSON
     */
    public void recordEvent(String eventType, String eventId, Integer seq, String rawData) {
    }

    /**
     * 记录已成功发送的 QQ 消息
     *
     * @param groupId   QQ 群 OpenID
     * @param request   发送使用的消息体，可能含仅供记录的附件数据
     * @param messageId 返回的消息 ID
     * @param refIdx    返回的引用索引，未提供时可为 null
     * @param timestamp 返回的原始时间戳，未提供时可为 null
     */
    public void recordSentGroupMessage(String groupId, MessageBody request, String messageId, String refIdx, String timestamp) {
    }

    /**
     * 记录已成功发送的 QQ 消息
     *
     * @param userId    QQ 用户 OpenID
     * @param request   发送使用的消息体，可能含仅供记录的附件数据
     * @param messageId 返回的消息 ID
     * @param refIdx    返回的引用索引，未提供时可为 null
     * @param timestamp 返回的原始时间戳，未提供时可为 null
     */
    public void recordSentC2CMessage(String userId, MessageBody request, String messageId, String refIdx, String timestamp) {
    }

    /**
     * 提交框架运行通知，具体通知方式由运行服务实现
     *
     * @param message 通知内容
     */
    public void alert(String message) {
    }

    /**
     * 观察 Napcat 上报数据
     *
     * @param data 上报的 JSON 数据
     */
    public void napcatPacket(JsonNode data) {
    }

    /**
     * 观察 QQ 事件分发入口完成后的数据
     *
     * @param type 平台事件名
     * @param data 事件的 d 字段
     */
    public void qqEventDispatched(String type, JsonNode data) {
    }

    /**
     * 供运行服务记录最近消息标识的扩展点，当前 core 未主动调用
     *
     * @param groupId   群标识
     * @param messageId 消息 ID
     */
    public void recordLastMsg(String groupId, String messageId) {
    }

    /**
     * 记录 Napcat 群消息发送调用，在请求前触发，不表示发送成功
     *
     * @param groupId 群号码
     */
    public void callGroupMessageSend(String groupId) {
    }

    /**
     * 记录 Napcat 私聊消息发送调用，在请求前触发，不表示发送成功
     *
     */
    public void callPrivateMessageSend() {
    }

    /**
     * 记录 Napcat 撤回调用，在请求前触发，不表示撤回成功
     *
     */
    public void callRecallMessage() {
    }

    /**
     * 记录一次平台消息进入指令分发的计数回调，不保证命中指令或执行成功
     *
     */
    public void callCommandExecuted() {
    }
}
