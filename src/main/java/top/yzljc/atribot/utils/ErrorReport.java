package top.yzljc.atribot.utils;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.database.ErrorReportDTO;
import top.yzljc.atribot.database.repo.ErrorReportRepository;
import top.yzljc.atribot.utils.tools.Alert;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @Author YZ_Ljc_
 * @ClassName ErrorReport
 * @Created_at 2026/06/24
 * @Project AtriMeow
 * @Package top.yzljc.atribot.utils
 */
@Slf4j
public class ErrorReport {

    /**
     * 单条错误最多保留的堆栈行数，防止超长堆栈撑爆 MEDIUMTEXT
     */
    private static final int MAX_STACK_LINES = 200;

    /**
     * 记录一次异常，返回 traceId
     *
     * @param className 发生异常的类名（可用 getClass().getSimpleName() 或硬编码）
     * @param e         捕获到的异常
     * @return traceId（UUID 字符串）
     */
    public static String report(String className, Exception e) {
        String traceId = UUID.randomUUID().toString();

        ErrorReportDTO dto = new ErrorReportDTO();
        dto.setTraceId(traceId);
        dto.setClassName(className);
        dto.setExceptionType(e.getClass().getName());
        dto.setExceptionMessage(e.getMessage() != null ? e.getMessage() : "(无消息)");
        dto.setStackTrace(toStackLines(e.getStackTrace()));
        dto.setCreateTime(new Timestamp(System.currentTimeMillis()));

        // 如果有 cause，也记录下来
        Throwable cause = e.getCause();
        if (cause != null) {
            dto.setCauseType(cause.getClass().getName());
            dto.setCauseMessage(cause.getMessage() != null ? cause.getMessage() : "(无消息)");
            dto.setCauseStackTrace(toStackLines(cause.getStackTrace()));
        }

        if (ErrorReportRepository.insert(dto)) {
            log.info("错误报告已入库: traceId={} (类: {}, 异常: {})", traceId, className, e.getClass().getSimpleName());
        } else {
            // 入库失败时至少把完整堆栈留在日志里，避免现场丢失
            log.error("错误报告入库失败，原始异常如下 (traceId={}, 类: {})", traceId, className, e);
        }

        Alert.notify("发生异常: " + e.getClass().getSimpleName() + " (traceId=" + traceId + ")");
        return traceId;
    }

    public static String report(Exception e) {
        String className = "Unknown";
        StackTraceElement[] stack = e.getStackTrace();
        if (stack.length > 0) {
            className = stack[0].getClassName();
        }
        return report(className, e);
    }

    private static List<String> toStackLines(StackTraceElement[] stack) {
        List<String> lines = new ArrayList<>();
        int limit = Math.min(stack.length, MAX_STACK_LINES);
        for (int i = 0; i < limit; i++) {
            lines.add(stack[i].toString());
        }
        if (stack.length > MAX_STACK_LINES) {
            lines.add("... 省略 " + (stack.length - MAX_STACK_LINES) + " 行");
        }
        return lines;
    }
}
