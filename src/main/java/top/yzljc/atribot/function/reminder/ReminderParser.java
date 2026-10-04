package top.yzljc.atribot.function.reminder;

import java.time.Instant;

/**
 * @Author YZ_Ljc_
 * @ClassName ReminderParser
 * @Created_at 2026/09/30
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.reminder
 */
@FunctionalInterface
public interface ReminderParser {
    /**
     * 解析提醒时间，事项内容由后续消息提供
     *
     * @param input 时间或周期描述
     * @param now 请求接收时间，用于解析相对时间
     * @return 时间解析结果
     * @throws Exception 解析服务不可用或响应无效
     */
    Result parse(String input, Instant now) throws Exception;

    enum Status { OK, NEED_TIME, AMBIGUOUS, REJECTED, ERROR }

    record Result(Status status, ReminderSchedule schedule) {
        public Result {
            if (status == null || (status == Status.OK) != (schedule != null)) {
                throw new IllegalArgumentException("Invalid parser result");
            }
        }
    }
}
