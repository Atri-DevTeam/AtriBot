package top.yzljc.atribot.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    private static final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Path ERROR_DIR = Path.of("errorlogs");

    /**
     * 记录一次异常，返回 traceId
     * @param className 发生异常的类名（可用 getClass().getSimpleName() 或硬编码）
     * @param e         捕获到的异常
     * @return traceId（UUID 字符串）
     */
    public static String report(String className, Exception e) {
        String traceId = UUID.randomUUID().toString();
        String timestamp = LocalDateTime.now().format(TIME_FMT);

        ObjectNode root = mapper.createObjectNode();
        root.put("traceId", traceId);
        root.put("className", className);
        root.put("timestamp", timestamp);

        ObjectNode exNode = root.putObject("exception");
        exNode.put("type", e.getClass().getName());
        exNode.put("message", e.getMessage() != null ? e.getMessage() : "(无消息)");

        ArrayNode stackArray = exNode.putArray("stackTrace");
        for (StackTraceElement ste : e.getStackTrace()) {
            stackArray.add(ste.toString());
        }

        // 如果有 cause，也记录下来
        Throwable cause = e.getCause();
        if (cause != null) {
            ObjectNode causeNode = root.putObject("cause");
            causeNode.put("type", cause.getClass().getName());
            causeNode.put("message", cause.getMessage() != null ? cause.getMessage() : "(无消息)");
            ArrayNode causeStack = causeNode.putArray("stackTrace");
            for (StackTraceElement ste : cause.getStackTrace()) {
                causeStack.add(ste.toString());
            }
        }

        try {
            ensureDir();
            Path file = ERROR_DIR.resolve(traceId + ".json");
            mapper.writeValue(file.toFile(), root);
            log.info("错误报告已保存: {} (类: {}, 异常: {})", file, className, e.getClass().getSimpleName());
        } catch (IOException ioe) {
            log.error("保存错误报告失败 (traceId={})", traceId, ioe);
        }

        return traceId;
    }

    /**
     * 便捷重载：自动取 e 的堆栈中第一个调用者作为类名。
     */
    public static String report(Exception e) {
        String className = "Unknown";
        StackTraceElement[] stack = e.getStackTrace();
        if (stack.length > 0) {
            className = stack[0].getClassName();
        }
        return report(className, e);
    }

    private static void ensureDir() throws IOException {
        if (!Files.exists(ERROR_DIR)) {
            Files.createDirectories(ERROR_DIR);
        }
    }
}
