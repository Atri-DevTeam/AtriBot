package top.yzljc.atribot.function.reminder;

import java.time.Instant;
import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName ReminderStore
 * @Created_at 2026/09/30
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.reminder
 */
public interface ReminderStore {
    enum Code { READY, SAVED, LIMITED, FULL, BUSY, NOT_FOUND, CONFLICT, NEED_TIME, AMBIGUOUS, REJECTED, ERROR }

    record Result(Code code, long taskId) {}

    @FunctionalInterface
    interface Delivery {
        boolean send(String userId, String refIdx, String fixedText) throws Exception;
    }

    void init() throws Exception;

    default Result begin(String userId, String requestId, Long taskId, Instant now) throws Exception {
        return begin(userId, requestId, taskId, now, false);
    }

    Result begin(String userId, String requestId, Long taskId, Instant now, boolean unlimited) throws Exception;

    default Result save(String userId, String requestId, String refIdx, ReminderSchedule schedule, Instant now) throws Exception {
        return save(userId, requestId, refIdx, schedule, now, false);
    }

    Result save(String userId, String requestId, String refIdx, ReminderSchedule schedule, Instant now,
                boolean unlimited) throws Exception;

    void reject(String userId, String requestId, Code code) throws Exception;

    default List<Long> list(String userId) throws Exception {
        return list(userId, 1);
    }

    /** 以每页 10 项返回，额外取第 11 项供调用方判断是否有下一页。 */
    List<Long> list(String userId, int page) throws Exception;

    boolean stop(String userId, long taskId, boolean delete) throws Exception;

    void dispatchDue(Instant now, Delivery delivery) throws Exception;
}
