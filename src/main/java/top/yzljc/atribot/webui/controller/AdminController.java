package top.yzljc.atribot.webui.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import io.javalin.http.Context;
import lombok.Data;
import top.yzljc.atribot.database.ErrorReportDTO;
import top.yzljc.atribot.database.EventLogDTO;
import top.yzljc.atribot.database.FeedbackDTO;
import top.yzljc.atribot.database.OfficialSendLogDTO;
import top.yzljc.atribot.database.repo.*;
import top.yzljc.atribot.function.official.Feedback;
import top.yzljc.atribot.function.official.PushTaskCommand;
import top.yzljc.atribot.function.official.pushtask.PushTask;
import top.yzljc.atribot.function.official.pushtask.PushTaskGlobalSettings;
import top.yzljc.atribot.function.official.pushtask.PushTaskGlobalSettings.DisableScope;
import top.yzljc.atribot.service.runtime.ThreadManager;
import top.yzljc.atribot.webui.Result;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static top.yzljc.atribot.webui.WebUiSupport.formatFeedbackTime;
import static top.yzljc.atribot.webui.WebUiSupport.isBlank;
import static top.yzljc.atribot.webui.WebUiSupport.parseLong;
import static top.yzljc.atribot.webui.WebUiSupport.parseInt;

/** 功能设置 + 反馈 + 错误报告 + 发送日志 + 事件日志 */
public class AdminController {

    // ============ 功能设置 ============

    public static void listFunctionSettings(Context ctx) {
        List<FunctionSettingDTO> items = new ArrayList<>();
        for (PushTask task : PushTaskCommand.getTasks()) {
            var rule = PushTaskGlobalSettings.getRule(task.getFunctionId());
            DisableScope scope = rule == null ? defaultScope(task) : rule.scope();
            String functionName = rule != null && !isBlank(rule.functionName()) ? rule.functionName() : task.getDisplayName();
            items.add(new FunctionSettingDTO(
                    task.getFunctionId(),
                    functionName,
                    task.isGroupEnable(),
                    task.isC2cEnable(),
                    scope.name(),
                    rule != null ? rule.startTime() : null,
                    rule != null ? rule.endTime() : null,
                    rule != null && rule.enabled(),
                    PushTaskGlobalSettings.isActiveNow(rule)
            ));
        }
        ctx.json(Result.success(items));
    }

    public static void saveFunctionSetting(Context ctx) {
        String functionId = ctx.pathParam("functionId");
        PushTask task = findPushTask(functionId);
        if (task == null) {
            ctx.json(Result.fail(404, "未找到对应的推送任务"));
            return;
        }

        FunctionSettingUpdateDTO dto = ctx.bodyAsClass(FunctionSettingUpdateDTO.class);
        DisableScope scope = parseDisableScope(dto == null ? null : dto.getScope(), defaultScope(task));
        if (scope == DisableScope.GROUP && !task.isGroupEnable()) {
            ctx.json(Result.fail(400, "该功能不支持群聊场景"));
            return;
        }
        if (scope == DisableScope.C2C && !task.isC2cEnable()) {
            ctx.json(Result.fail(400, "该功能不支持私聊场景"));
            return;
        }
        if (scope == DisableScope.BOTH && (!task.isGroupEnable() || !task.isC2cEnable())) {
            ctx.json(Result.fail(400, "该功能不支持同时禁用群聊和私聊"));
            return;
        }

        var rule = PushTaskGlobalSettings.saveRule(new PushTaskGlobalSettings.DisableRule(
                task.getFunctionId(),
                !isBlank(dto == null ? null : dto.getFunctionName()) ? dto.getFunctionName() : task.getDisplayName(),
                scope,
                dto == null ? null : dto.getStartTime(),
                dto == null ? null : dto.getEndTime(),
                dto == null || dto.isEnabled()
        ));
        ctx.json(Result.success(new FunctionSettingDTO(
                task.getFunctionId(),
                rule.functionName(),
                task.isGroupEnable(),
                task.isC2cEnable(),
                rule.scope().name(),
                rule.startTime(),
                rule.endTime(),
                rule.enabled(),
                PushTaskGlobalSettings.isActiveNow(rule)
        )));
    }

    public static void deleteFunctionSetting(Context ctx) {
        String functionId = ctx.pathParam("functionId");
        PushTaskGlobalSettings.removeRule(functionId);
        ctx.json(Result.success("ok"));
    }

    private static PushTask findPushTask(String functionId) {
        if (isBlank(functionId)) {
            return null;
        }
        for (PushTask task : PushTaskCommand.getTasks()) {
            if (task.getFunctionId().equals(functionId)) {
                return task;
            }
        }
        return null;
    }

    private static DisableScope defaultScope(PushTask task) {
        if (task.isGroupEnable() && task.isC2cEnable()) {
            return DisableScope.BOTH;
        }
        return task.isGroupEnable() ? DisableScope.GROUP : DisableScope.C2C;
    }

    private static DisableScope parseDisableScope(String value, DisableScope fallback) {
        if (isBlank(value)) {
            return fallback;
        }
        try {
            return DisableScope.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    public record FunctionSettingDTO(String functionId, String functionName, boolean groupEnable,
                                     boolean c2cEnable, String scope, String startTime, String endTime,
                                     boolean enabled, boolean activeNow) {
    }

    @Data
    public static class FunctionSettingUpdateDTO {
        private String functionName;
        private String scope;
        private String startTime;
        private String endTime;
        private boolean enabled = true;
    }

    // ============ 反馈管理 ============

    private static final DateTimeFormatter FEEDBACK_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static void listFeedback(Context ctx) {
        int page = parseInt(ctx.queryParam("page"), 1);
        int pageSize = parseInt(ctx.queryParam("pageSize"), 20);
        String filter = ctx.queryParam("filter"); // "unreplied" | "replied" | "all"

        List<FeedbackDTO> list;
        int total;

        if ("replied".equals(filter)) {
            total = FeedbackRepository.countReplied();
            list = FeedbackRepository.findRepliedPaginated(page, pageSize);
        } else if ("all".equals(filter)) {
            total = FeedbackRepository.countAll();
            list = FeedbackRepository.findAllPaginated(page, pageSize);
        } else {
            total = FeedbackRepository.countUnreplied();
            list = FeedbackRepository.findUnrepliedPaginated(page, pageSize);
        }

        List<FeedbackItemDTO> items = list.stream().map(fb -> new FeedbackItemDTO(
                fb.getId(),
                fb.getPlatform(),
                fb.getUserId(),
                fb.getUsername(),
                fb.getGroupId(),
                fb.getSubmitContent(),
                fb.getCreateTime() != null ? fb.getCreateTime().toLocalDateTime().format(FEEDBACK_TIME_FMT) : null,
                fb.isRead(),
                fb.getReplyContent(),
                fb.getReplyTime() != null ? fb.getReplyTime().toLocalDateTime().format(FEEDBACK_TIME_FMT) : null,
                fb.isHidden()
        )).toList();

        ctx.json(Result.success(new FeedbackListResult(items, total, page, pageSize)));
    }

    public static void countFeedback(Context ctx) {
        int unreplied = FeedbackRepository.countUnreplied();
        int replied = FeedbackRepository.countReplied();
        int all = FeedbackRepository.countAll();
        ctx.json(Result.success(new FeedbackCountDTO(unreplied, replied, all)));
    }

    public static void replyFeedback(Context ctx) {
        ReplyFeedbackDTO dto = ctx.bodyAsClass(ReplyFeedbackDTO.class);
        if (isBlank(dto.getId()) || isBlank(dto.getReplyContent())) {
            ctx.json(Result.fail(400, "id 和 replyContent 不能为空"));
            return;
        }
        FeedbackDTO target = FeedbackRepository.findById(dto.getId());
        if (target != null && target.getPlatform() != null
                && target.getPlatform().toUpperCase(java.util.Locale.ROOT).startsWith("NAPCAT")) {
            ctx.json(Result.fail(400, "NapCat 来源反馈已停止回复接入"));
            return;
        }
        boolean success = FeedbackRepository.reply(dto.getId(), dto.getReplyContent(), dto.isHidden());
        if (success) {
            // 主动推送涉及网络，别阻塞 WebUI 请求线程
            ThreadManager.execute(() -> Feedback.dispatchReply(dto.getId()));
            ctx.json(Result.success("ok"));
        } else {
            ctx.json(Result.fail(500, "回复失败，可能该反馈不存在"));
        }
    }

    public record FeedbackItemDTO(String id, String platform, String userId, String username,
                                   String groupId, String submitContent, String createTime,
                                   @JsonProperty("isRead") boolean isRead,
                                   String replyContent, String replyTime,
                                   @JsonProperty("isHidden") boolean isHidden) {}

    public record FeedbackListResult(List<FeedbackItemDTO> items, int total, int page, int pageSize) {}

    public record FeedbackCountDTO(int unreplied, int replied, int all) {}

    @Data
    public static class ReplyFeedbackDTO {
        private String id;
        private String replyContent;
        @JsonProperty("isHidden")
        private boolean isHidden;
    }

    // ============ 错误报告 ============

    public static void listErrorReports(Context ctx) {
        int page = parseInt(ctx.queryParam("page"), 1);
        int pageSize = parseInt(ctx.queryParam("pageSize"), 20);
        if (pageSize > 200) {
            pageSize = 200;
        }
        String keyword = ctx.queryParam("keyword");
        String exceptionType = ctx.queryParam("exceptionType");

        int total = ErrorReportRepository.count(keyword, exceptionType);
        List<ErrorItemDTO> items = ErrorReportRepository.findPaginated(page, pageSize, keyword, exceptionType)
                .stream()
                .map(AdminController::toErrorItem)
                .toList();

        ctx.json(Result.success(new ErrorListResult(items, total, page, pageSize)));
    }

    /**
     * 按 traceId 查询单条错误详情，含完整堆栈
     */
    public static void getErrorReport(Context ctx) {
        String traceId = ctx.pathParam("traceId");
        if (isBlank(traceId)) {
            ctx.json(Result.fail(400, "traceId 不能为空"));
            return;
        }

        ErrorReportDTO report = ErrorReportRepository.findByTraceId(traceId.trim());
        if (report == null) {
            ctx.json(Result.fail(404, "未找到该 traceId 对应的错误报告"));
            return;
        }

        ctx.json(Result.success(new ErrorDetailDTO(
                report.getTraceId(),
                report.getClassName(),
                report.getExceptionType(),
                report.getExceptionMessage(),
                report.getStackTrace() != null ? report.getStackTrace() : List.of(),
                report.getCauseType(),
                report.getCauseMessage(),
                report.getCauseStackTrace() != null ? report.getCauseStackTrace() : List.of(),
                formatFeedbackTime(report.getCreateTime())
        )));
    }

    public static void errorReportStats(Context ctx) {
        ctx.json(Result.success(new ErrorStatsDTO(
                ErrorReportRepository.count(null, null),
                ErrorReportRepository.countSince(24),
                ErrorReportRepository.countSince(24 * 7),
                ErrorReportRepository.topExceptionTypes(8)
        )));
    }

    private static ErrorItemDTO toErrorItem(ErrorReportDTO report) {
        return new ErrorItemDTO(
                report.getTraceId(),
                report.getClassName(),
                report.getExceptionType(),
                report.getExceptionMessage(),
                report.getCauseType(),
                report.getCauseMessage(),
                formatFeedbackTime(report.getCreateTime())
        );
    }

    public record ErrorItemDTO(String traceId, String className, String exceptionType, String exceptionMessage,
                               String causeType, String causeMessage, String createTime) {}

    public record ErrorDetailDTO(String traceId, String className, String exceptionType, String exceptionMessage,
                                 List<String> stackTrace, String causeType, String causeMessage,
                                 List<String> causeStackTrace, String createTime) {}

    public record ErrorListResult(List<ErrorItemDTO> items, int total, int page, int pageSize) {}

    public record ErrorStatsDTO(int total, int last24h, int last7d, Map<String, Integer> topExceptionTypes) {}

    // ============ 发送日志 ============

    public static void listOfficialSendLogs(Context ctx) {
        int page = parseInt(ctx.queryParam("page"), 1);
        int pageSize = parseInt(ctx.queryParam("pageSize"), 20);
        if (pageSize > 200) {
            pageSize = 200;
        }
        String type = normalizeSendLogType(ctx.queryParam("type"));
        String keyword = ctx.queryParam("keyword");

        int total = OfficialSendLogRepository.count(type, keyword);
        List<SendLogItemDTO> items = OfficialSendLogRepository.findPaginated(page, pageSize, type, keyword)
                .stream()
                .map(AdminController::toSendLogItem)
                .toList();
        ctx.json(Result.success(new SendLogListResult(items, total, page, pageSize)));
    }

    public static void getOfficialSendLog(Context ctx) {
        long id = parseLong(ctx.pathParam("id"), -1L);
        if (id <= 0) {
            ctx.json(Result.fail(400, "日志 id 无效"));
            return;
        }
        OfficialSendLogDTO log = OfficialSendLogRepository.findById(id);
        if (log == null) {
            ctx.json(Result.fail(404, "未找到该发送日志"));
            return;
        }
        ctx.json(Result.success(toSendLogDetail(log)));
    }

    public static void officialSendLogStats(Context ctx) {
        var stats = OfficialSendLogRepository.stats();
        ctx.json(Result.success(new SendLogStatsDTO(stats.all(), stats.send(), stats.response(), stats.error())));
    }

    private static String normalizeSendLogType(String raw) {
        if (isBlank(raw) || "ALL".equalsIgnoreCase(raw)) {
            return null;
        }
        String type = raw.trim().toUpperCase();
        return switch (type) {
            case OfficialSendLogRepository.TYPE_SEND,
                 OfficialSendLogRepository.TYPE_RESPONSE,
                 OfficialSendLogRepository.TYPE_ERROR -> type;
            default -> null;
        };
    }

    private static SendLogItemDTO toSendLogItem(OfficialSendLogDTO log) {
        return new SendLogItemDTO(
                log.getId(),
                log.getTraceId(),
                log.getEntryType(),
                log.getScene(),
                log.getMethod(),
                log.getUrl(),
                log.getRequestJson(),
                log.getResponseStatus(),
                log.getResponseBody(),
                log.getErrorCode(),
                log.getErrorReason(),
                log.getErrorMessage(),
                formatFeedbackTime(log.getCreateTime())
        );
    }

    private static SendLogDetailDTO toSendLogDetail(OfficialSendLogDTO log) {
        return new SendLogDetailDTO(
                log.getId(),
                log.getTraceId(),
                log.getEntryType(),
                log.getScene(),
                log.getMethod(),
                log.getUrl(),
                log.getRequestJson(),
                log.getResponseStatus(),
                log.getResponseBody(),
                log.getErrorCode(),
                log.getErrorReason(),
                log.getErrorMessage(),
                formatFeedbackTime(log.getCreateTime())
        );
    }

    public record SendLogItemDTO(long id, String traceId, String entryType, String scene, String method, String url,
                                 String requestJson, Integer responseStatus, String responseBody,
                                 Integer errorCode, String errorReason, String errorMessage,
                                 String createTime) {}

    public record SendLogDetailDTO(long id, String traceId, String entryType, String scene, String method, String url,
                                   String requestJson, Integer responseStatus, String responseBody,
                                   Integer errorCode, String errorReason, String errorMessage,
                                   String createTime) {}

    public record SendLogListResult(List<SendLogItemDTO> items, int total, int page, int pageSize) {}

    public record SendLogStatsDTO(int all, int send, int response, int error) {}

    // ============ 原始事件记录 ============

    public static void listRawEventLogs(Context ctx) {
        int page = parseInt(ctx.queryParam("page"), 1);
        int pageSize = parseInt(ctx.queryParam("pageSize"), 20);
        if (pageSize > 200) {
            pageSize = 200;
        }
        String type = ctx.queryParam("type");
        String keyword = ctx.queryParam("keyword");

        int total = EventLogRepository.count(type, keyword);
        List<EventLogDTO> items = EventLogRepository.findPaginated(page, pageSize, type, keyword);
        List<RawEventLogItemDTO> dtos = items.stream()
                .map(log -> new RawEventLogItemDTO(
                        log.getId(),
                        log.getEventType(),
                        log.getEventId(),
                        log.getSeq(),
                        log.getRawData(),
                        formatFeedbackTime(log.getCreateTime())
                ))
                .toList();
        ctx.json(Result.success(new RawEventLogListResult(dtos, total, page, pageSize)));
    }

    public static void getRawEventLog(Context ctx) {
        long id = parseLong(ctx.pathParam("id"), -1L);
        if (id <= 0) {
            ctx.json(Result.fail(400, "事件 id 无效"));
            return;
        }
        EventLogDTO log = EventLogRepository.findById(id);
        if (log == null) {
            ctx.json(Result.fail(404, "未找到该事件记录"));
            return;
        }
        ctx.json(Result.success(new RawEventLogDetailDTO(
                log.getId(),
                log.getEventType(),
                log.getEventId(),
                log.getSeq(),
                log.getRawData(),
                formatFeedbackTime(log.getCreateTime())
        )));
    }

    public static void rawEventLogStats(Context ctx) {
        var stats = EventLogRepository.stats();
        ctx.json(Result.success(new RawEventLogStatsDTO(
                stats.all(),
                stats.today(),
                stats.last24h(),
                stats.types().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                EventLogRepository.EventTypeCount::eventType,
                                EventLogRepository.EventTypeCount::count))
        )));
    }

    /**
     * 清除记录：body 中 start/end 为 epoch 毫秒（可为空），type 为事件类型（可为空）。
     * 全部为空时清空全部记录。
     */
    public static void clearRawEventLogs(Context ctx) {
        JsonNode body = ctx.bodyAsClass(JsonNode.class);
        Long start = body != null && body.hasNonNull("start") ? body.path("start").asLong() : null;
        Long end = body != null && body.hasNonNull("end") ? body.path("end").asLong() : null;
        String type = body != null ? body.path("type").asText(null) : null;
        if (isBlank(type)) {
            type = null;
        }
        if (start != null && end != null && start > end) {
            ctx.json(Result.fail(400, "开始时间不能晚于结束时间"));
            return;
        }
        int deleted = EventLogRepository.deleteByRange(start, end, type);
        ctx.json(Result.success(new ClearEventLogsResult(deleted)));
    }

    public record RawEventLogItemDTO(long id, String eventType, String eventId, Integer seq, String rawData, String createTime) {}

    public record RawEventLogDetailDTO(long id, String eventType, String eventId, Integer seq, String rawData, String createTime) {}

    public record RawEventLogListResult(List<RawEventLogItemDTO> items, int total, int page, int pageSize) {}

    public record RawEventLogStatsDTO(int all, int today, int last24h, Map<String, Integer> topTypes) {}

    public record ClearEventLogsResult(int deleted) {}
}
