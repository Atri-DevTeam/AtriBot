package top.yzljc.atribot.event.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @Author YZ_Ljc_
 * @ClassName ErrorCode
 * @Created_at 2026/07/27
 * @Project AtriMeow
 * @Package top.yzljc.atribot.event.impl
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {
    MESSAGE_TYPE_CONTENT_MISMATCH(22006, "消息类型与内容不匹配"),
    INPUT_TYPE_ERROR(50059, "输入类型错误"),
    NO_ARK_TEMPLATE_PERMISSION(304004, "无权限使用该ARK模板"),
    NO_MARKDOWN_TEMPLATE_PERMISSION_304036(304036, "无Markdown模板权限"),
    INVALID_MESSAGE_CONTENT(304061, "消息内容无效"),
    SUBSCRIBE_BUTTON_LIMIT_REACHED(304062, "订阅按钮数量达到上限"),
    SUBSCRIBE_MESSAGE_UNAUTHORIZED(304064, "订阅消息未授权"),
    INVALID_FILE_INFO(304080, "文件信息无效"),
    MESSAGE_ID_EXPIRED_CANNOT_REPLY(304103, "消息ID已过期，不能回复"),
    KEYBOARD_STYLE_PARAM_ERROR(305007, "键盘样式参数错误"),
    INVALID_USER_OPENID(306009, "用户openid无效"),
    GET_BOT_INFO_FAILED(340067, "获取机器人信息失败"),
    INVALID_MESSAGE_TYPE(340069, "消息类型无效"),
    MEDIA_TRANSFER_FAILED(40034004, "富媒体信息转存失败"),
    REPLY_MSG_ID_EXPIRED(40034005, "回复消息msg_id已过期"),
    MESSAGE_CONTENT_VIOLATION(40034006, "消息内容违规"),
    MARKDOWN_PARAM_EMPTY(40034008, "markdown参数有空值"),
    MARKDOWN_PARAM_HAS_NEWLINE(40034009, "markdown参数有换行符"),
    TEMPLATE_PARAM_HAS_MARKDOWN_SYNTAX(40034010, "模版参数中不能含有markdown语法"),
    INVALID_MARKDOWN_CONTENT(40034011, "无效的markdown内容"),
    INVALID_OR_UNAUTHORIZED_MSG_ID(40034024, "请求参数msg_id无效或越权"),
    INVALID_EVENT_ID(40034025, "请求参数event_id无效"),
    EVENT_ID_EXPIRED(40034026, "请求参数event_id已过期"),
    EVENT_NOT_SUPPORT_REPLY(40034027, "该事件不支持回复消息"),
    INLINE_KEYBOARD_ROW_COLUMN_LIMIT(40034029, "内联键盘行/列超限"),
    ACTIVE_MESSAGE_RATE_LIMIT(40034100, "主动消息发送超过频控限制"),
    BOT_NOT_GROUP_MEMBER_40034101(40034101, "机器人非群成员"),
    NO_ACTIVE_MESSAGE_PERMISSION(40034105, "主动消息发送失败，无权限"),
    UNSUPPORTED_COMMAND_TYPE(40034106, "消息不支持该指令类型"),
    COMMAND_PARAM_TOO_LONG(40034108, "指令参数长度超限"),
    COMMAND_PARAM_PARSE_FAILED(40034109, "指令参数解析失败"),
    RECALL_INTERVAL_LIMIT_REACHED(40034122, "召回消息已达区间上限"),
    UNSUPPORTED_RECALL_MESSAGE(40034123, "不支持召回消息"),
    MARKDOWN_MESSAGE_PARAM_ERROR(40034124, "markdown消息参数错误"),
    NO_MARKDOWN_TEMPLATE_PERMISSION_40034127(40034127, "无markdown模板权限"),
    PASSIVE_REPLY_TIME_OR_COUNT_LIMIT(40034128, "被动回复时间或次数超限"),
    BOT_MUTED(40054002, "机器人被禁言"),
    BOT_NOT_GROUP_MEMBER_40054003(40054003, "机器人不是群成员"),
    NO_FRIEND(40054004, "无好友关系"),
    REPEATED_MESSAGE(40054005, "消息被去重"),
    VERIFY_FRIEND_RELATION_FAILED(40054006, "验证好友关系失败"),
    MESSAGE_TOO_LONG(40054007, "消息长度超限"),
    URL_NOT_ALLOWED(40054010, "不允许发送URL"),
    USER_REJECT_MESSAGE(40054013, "用户拒收消息"),
    BOT_OFFLINE(40054016, "机器人已下线"),
    MESSAGE_TOO_LONG_OR_ABNORMAL(40054018, "消息过长或异常"),
    MESSAGE_SEND_EXCEPTION_50055001(50055001, "消息发送异常，请稍后重试"),
    MESSAGE_SEND_EXCEPTION_50055002(50055002, "消息发送异常，请稍后重试"),
    ARK_MESSAGE_SEND_EXCEPTION(50055006, "ARK消息发送异常，请稍后重试"),
    PREFIX_CONTENT_CANNOT_MODIFY(40007, "已下发内容前缀不可修改"),
    INTERNAL_SERVER_ERROR(50001, "服务内部错误"),
    RATE_LIMIT(50002, "频率限制"),
    INVALID_REQUEST_PARAM_40061001(40061001, "请求参数无效"),
    INVALID_MSG_ID_PARAM(40061002, "请求参数msgid无效"),
    NO_OPERATION_PERMISSION(40062003, "无操作权限"),
    RECALL_TIME_LIMIT_EXCEEDED(40064004, "已超出消息撤回时限"),
    RECALL_MESSAGE_FAILED(50065001, "消息撤回失败，请稍后重试");

    private final int errorCode;
    private final String message;

    public static String getErrorMessage(int code) {
        for (ErrorCode errorCode : ErrorCode.values()) {
            if (errorCode.getErrorCode() == code) {
                return errorCode.getMessage();
            }
        }
        return "未录入的错误码";
    }

    public static ErrorCode fromErrorCode(int code) {
        for (ErrorCode errorCode : ErrorCode.values()) {
            if (errorCode.getErrorCode() == code) {
                return errorCode;
            }
        }
        return null;
    }
}
