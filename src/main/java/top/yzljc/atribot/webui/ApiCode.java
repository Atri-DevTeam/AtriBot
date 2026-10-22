package top.yzljc.atribot.webui;

import lombok.Getter;

/**
 * @Author YZ_Ljc_
 * @ClassName ApiCode
 * @Created_at 2026/10/20
 * @Project AtriMeow
 * @Package top.yzljc.atribot.webui
 */
@Getter
public enum ApiCode {
    SUCCESS(0, "请求成功", 200),
    INVALID_PARAMETER(10001, "请求参数无效", 400),
    AUTHENTICATION_REQUIRED(10002, "身份验证失败", 401),
    ACCESS_DENIED(10003, "无权执行此操作", 403),
    RESOURCE_NOT_FOUND(10004, "请求的资源不存在", 404),
    RATE_LIMITED(10005, "请求过于频繁", 429),
    METHOD_NOT_ALLOWED(10006, "请求方法不支持", 405),
    UNSUPPORTED_MEDIA_TYPE(10007, "请求内容类型不支持", 415),
    RESOURCE_CONFLICT(10008, "资源状态冲突", 409),
    PAYLOAD_TOO_LARGE(10009, "请求内容过大", 413),
    NOT_ACCEPTABLE(10010, "响应内容类型不支持", 406),
    CUSTOM_ERROR(19999, "请求失败", 400),
    IMAGE_PROCESSING_FAILED(40005, "图片处理失败", 500),
    IMAGE_DELETE_FAILED(40006, "图片删除失败", 500),
    LOOT_ITEM_NOT_FOUND(60001, "奖励物品不存在", 404),
    QQ_API_REQUEST_FAILED(70001, "QQ 平台接口请求失败", 502),
    QQ_RESPONSE_INVALID(70002, "QQ 平台响应格式无效", 502),
    WELCOME_SEND_FAILED(71001, "加群欢迎发送失败", 502),
    PUSH_TASK_NOT_FOUND(72001, "推送任务不存在", 404),
    STATISTICS_QUERY_FAILED(73001, "统计查询失败", 500),
    AUTHORIZATION_DISABLED(80001, "站点授权未开放", 503),
    AUTHORIZATION_INVALID_CLIENT(80002, "授权来源站点无效", 403),
    AUTHORIZATION_INVALID_SIGNATURE(80003, "授权请求签名无效", 403),
    AUTHORIZATION_INVALID_REQUEST(80004, "授权请求参数无效", 400),
    AUTHORIZATION_REQUEST_EXPIRED(80005, "授权请求已过期", 400),
    AUTHORIZATION_REQUEST_REPLAYED(80006, "授权请求已使用", 400),
    AUTHORIZATION_BUSY(80007, "站点授权请求过多", 429),
    AUTHORIZATION_EXPIRED(80008, "站点授权已失效", 400),
    AUTHORIZATION_INVALID_IDENTITY(80009, "授权身份无效", 403),
    AUTHORIZATION_INVALID_GRANT(80010, "授权码或校验凭证无效", 400),
    ACCOUNT_RESTRICTED(80011, "账号已受限", 403),
    AUTHORIZATION_FAILED(80012, "站点授权处理失败", 500),
    ACCOUNT_NOT_REGISTERED(80013, "尚未注册或绑定机器人账号，请私聊机器人发送 /dash 完成注册绑定后再登录", 403),
    INTERNAL_ERROR(90001, "服务器内部错误", 500),
    SERVICE_UNAVAILABLE(90002, "服务暂不可用", 503),
    UPSTREAM_FAILED(90003, "上游服务请求失败", 502),
    UPSTREAM_TIMEOUT(90004, "上游服务请求超时", 504);

    private final int code;
    private final String message;
    private final int httpStatus;

    ApiCode(int code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

}
