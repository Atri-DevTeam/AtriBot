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
