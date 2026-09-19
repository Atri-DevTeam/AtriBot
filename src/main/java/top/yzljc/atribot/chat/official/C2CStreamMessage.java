package top.yzljc.atribot.chat.official;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.event.EventManager;
import top.yzljc.atribot.event.events.OfficialC2CSendFailEvent;
import top.yzljc.atribot.function.tasks.QQChatContentRecord;
import top.yzljc.atribot.database.repo.OfficialSendLogRepository;
import top.yzljc.atribot.platform.qq.TokenManager;
import top.yzljc.atribot.service.request.HttpService;
import top.yzljc.atribot.service.runtime.ThreadManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;

@Slf4j
final class C2CStreamMessage {

    private static final long STREAM_TIMEOUT_MS = 60_000;
    private static final long STREAM_SAFE_TIMEOUT_MS = 55_000;
    private static final long STREAM_FINAL_GUARD_MS = 2_000;
    private static final long STREAM_MIN_EST_SEND_MS = 100;
    private static final int STREAM_STATE_GENERATING = 1;
    private static final int STREAM_STATE_END = 10;

    static final String CONTENT_TYPE_TEXT = "text";
    static final String CONTENT_TYPE_MARKDOWN = "markdown";
    static final String INPUT_MODE_REPLACE = "replace";

    private final String apiBaseUrl;
    private final TokenManager tokenManager;
    private final ObjectMapper objectMapper;
    private final MessageBodyFactory bodyFactory;
    private final Function<String, Integer> msgSeqProvider;
    private final BiFunction<String, RT, CompletableFuture<String>> maintenanceReply;

    C2CStreamMessage(String apiBaseUrl, TokenManager tokenManager, ObjectMapper objectMapper,
                     MessageBodyFactory bodyFactory, Function<String, Integer> msgSeqProvider,
                     BiFunction<String, RT, CompletableFuture<String>> maintenanceReply) {
        this.apiBaseUrl = apiBaseUrl;
        this.tokenManager = tokenManager;
        this.objectMapper = objectMapper;
        this.bodyFactory = bodyFactory;
        this.msgSeqProvider = msgSeqProvider;
        this.maintenanceReply = maintenanceReply;
    }

    String send(String openId, Map<String, Object> request) {
        return await(sendAsync(openId, request), "单聊流式");
    }

    CompletableFuture<String> sendAsync(String openId, Map<String, Object> request) {
        Map<String, Object> effectiveRequest = request;
        if (request != null && Boolean.TRUE.equals(request.get("is_wakeup"))) {
            effectiveRequest = new HashMap<>(request);
            effectiveRequest.remove("msg_id");
            effectiveRequest.remove("event_id");
            effectiveRequest.remove("msg_seq");
            effectiveRequest.remove("message_reference");
        }
        if (!hasStreamReplyTarget(effectiveRequest)) {
            log.error("单聊流式消息发送失败：普通流式消息必须提供 msg_id/event_id，召回消息需设置 is_wakeup, openId: {}", openId);
            return CompletableFuture.completedFuture(null);
        }
        Map<String, Object> pendingRequest = effectiveRequest;
        return ThreadManager.supplyAsync(() -> {
            if (ChatService.isEmergencyPaused()) {
                RT rt = sourceOf(pendingRequest);
                return rt == null ? null : maintenanceReply.apply(openId, rt).join();
            }
            return doSend(openId, pendingRequest);
        });
    }

    CompletableFuture<String> sendBatchAsync(String openId, RT rt,
                                             String contentType, String inputMode, List<String> contents,
                                             Boolean isWakeup) {
        return ThreadManager.supplyAsync(() -> sendBatch(openId, rt, contentType, inputMode, contents, isWakeup));
    }

    List<String> toSnapshots(List<String> deltas) {
        if (deltas == null || deltas.isEmpty()) {
            return List.of();
        }
        List<String> snapshots = new ArrayList<>();
        StringBuilder content = new StringBuilder();
        for (String delta : deltas) {
            if (delta == null || delta.isEmpty()) {
                continue;
            }
            content.append(delta);
            snapshots.add(content.toString());
        }
        return snapshots;
    }

    private String sendBatch(String openId, RT rt,
                             String contentType, String inputMode, List<String> contents,
                             Boolean isWakeup) {
        if (Boolean.TRUE.equals(isWakeup)) {
            rt = null;
        }
        if (ChatService.isEmergencyPaused()) {
            if (rt == null) {
                log.warn("单聊主动流式消息已被应急暂停拦截, openId: {}", openId);
                return null;
            }
            return maintenanceReply.apply(openId, rt).join();
        }
        if (rt == null && !Boolean.TRUE.equals(isWakeup)) {
            log.error("单聊流式消息发送失败：普通流式消息必须提供 msg_id/event_id，召回消息需设置 is_wakeup, openId: {}", openId);
            return null;
        }
        List<String> snapshots = cleanSnapshots(contents);
        if (snapshots.isEmpty()) {
            return null;
        }

        long deadline = System.currentTimeMillis() + Math.min(STREAM_SAFE_TIMEOUT_MS, STREAM_TIMEOUT_MS);
        long avgSendMs = STREAM_MIN_EST_SEND_MS;
        String streamMsgId = null;
        String lastMessageId = null;
        Integer msgSeq = rt instanceof RT.Message message ? msgSeqProvider.apply(message.id()) : null;
        int sentIndex = 0;
        int sourceIndex = 0;

        while (sourceIndex < snapshots.size()) {
            if (ChatService.isEmergencyPaused()) {
                return rt == null ? lastMessageId : maintenanceReply.apply(openId, rt).join();
            }
            boolean finalContent = sourceIndex == snapshots.size() - 1;
            if (!finalContent && sentIndex > 0
                    && System.currentTimeMillis() + avgSendMs + STREAM_FINAL_GUARD_MS >= deadline) {
                sourceIndex = snapshots.size() - 1;
                finalContent = true;
            }

            Map<String, Object> request = streamRequest(
                    inputMode,
                    finalContent ? STREAM_STATE_END : STREAM_STATE_GENERATING,
                    sentIndex,
                    contentType,
                    snapshots.get(sourceIndex),
                    rt,
                    streamMsgId,
                    msgSeq,
                    isWakeup
            );

            long sendStart = System.currentTimeMillis();
            String messageId = doSend(openId, request);
            long sendCost = Math.max(1, System.currentTimeMillis() - sendStart);
            avgSendMs = Math.max(STREAM_MIN_EST_SEND_MS, ((avgSendMs * sentIndex) + sendCost) / (sentIndex + 1));

            if (messageId == null) {
                return lastMessageId;
            }
            if (streamMsgId == null) {
                streamMsgId = messageId;
            }
            lastMessageId = messageId;
            sentIndex++;

            if (finalContent) {
                return lastMessageId;
            }

            sourceIndex = nextSourceIndex(sourceIndex, snapshots.size(), deadline, avgSendMs);
        }

        return lastMessageId;
    }

    private String doSend(String openId, Map<String, Object> request) {
        String url = privateStreamMessageUrl(openId);
        String json;
        try {
            json = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            OfficialSendLogRepository.recordError(null, "单聊流式", "POST", url, null,
                    null, null, "流式消息序列化失败: " + e.getMessage());
            log.error("单聊流式消息序列化失败: ", e);
            return null;
        }

        String traceId = OfficialSendLogRepository.recordSend("单聊流式", "POST", url, json);
        var res = HttpService.postJsonDetailed(url, json,
                "Authorization", "QQBot " + tokenManager.getAccessToken());
        try {
            if (res.status() >= 200 && res.status() < 300 && res.body() != null && !res.body().isBlank()) {
                JsonNode response = objectMapper.readTree(res.body());
                String messageId = extractMessageId(response);
                String messageIdx = response.path("ext_info").path("ref_idx").asText(null);
                String timestamp = response.path("timestamp").asText(null);
                if (messageId != null) {
                    OfficialSendLogRepository.recordResponse(traceId, "单聊流式", "POST", url, json,
                            res.status(), res.body());
                    QQChatContentRecord.recordSentC2CMessage(openId, bodyFactory.streamRequestToMessageBody(request), messageId, messageIdx, timestamp);
                    return messageId;
                }
                OfficialSendLogRepository.recordError(traceId, "单聊流式", "POST", url, json,
                        res.status(), res.body(), "返回无 id");
                log.error("单聊流式消息发送失败, 返回无 id: {}", res.body());
                throw QQMessageSendException.fromResponse(objectMapper, res.body(), "官方接口未返回消息ID");
            } else {
                OfficialSendLogRepository.recordError(traceId, "单聊流式", "POST", url, json,
                        res.status(), res.body(), "HTTP 状态异常或响应为空");
                log.error("单聊流式消息发送失败, status: {}, body: {}", res.status(), res.body());
                callFailEvent(url, res.body());
                throw QQMessageSendException.fromResponse(objectMapper, res.body(), "消息发送失败");
            }
        } catch (JsonProcessingException e) {
            OfficialSendLogRepository.recordError(traceId, "单聊流式", "POST", url, json,
                    res.status(), res.body(), "响应解析失败: " + e.getMessage());
            log.error("单聊流式消息响应解析失败: ", e);
            return null;
        }
    }

    private Map<String, Object> streamRequest(String inputMode, int inputState, int index, String contentType,
                                              String contentRaw, RT rt, String streamMsgId,
                                              Integer msgSeq, Boolean isWakeup) {
        Map<String, Object> request = new HashMap<>();
        request.put("input_mode", inputMode);
        request.put("input_state", inputState);
        request.put("index", index);
        request.put("content_type", contentType);
        request.put("content_raw", contentRaw);
        if (!Boolean.TRUE.equals(isWakeup)) {
            putSource(request, rt);
            if (msgSeq != null) request.put("msg_seq", msgSeq);
        }
        if (streamMsgId != null) request.put("stream_msg_id", streamMsgId);
        if (isWakeup != null) request.put("is_wakeup", isWakeup);
        return request;
    }

    private boolean hasStreamReplyTarget(Map<String, Object> request) {
        return request != null && (Boolean.TRUE.equals(request.get("is_wakeup")) || sourceOf(request) != null);
    }

    private RT sourceOf(Map<String, Object> request) {
        return MessageBodyFactory.sourceOf(stringValue(request.get("msg_id")), stringValue(request.get("event_id")));
    }

    private void putSource(Map<String, Object> request, RT rt) {
        switch (rt) {
            case null -> { }
            case RT.Message message -> request.put("msg_id", message.id());
            case RT.Event event -> request.put("event_id", event.id());
        }
    }

    private String extractMessageId(JsonNode response) {
        if (response == null) {
            return null;
        }
        String id = response.path("id").asText(null);
        return id == null || id.isBlank() ? null : id;
    }

    private int nextSourceIndex(int currentIndex, int size, long deadline, long avgSendMs) {
        int lastIndex = size - 1;
        int remainingSnapshots = lastIndex - currentIndex;
        if (remainingSnapshots <= 1) {
            return lastIndex;
        }

        long remainingMs = deadline - System.currentTimeMillis() - STREAM_FINAL_GUARD_MS;
        int remainingSends = (int) Math.max(1, remainingMs / Math.max(STREAM_MIN_EST_SEND_MS, avgSendMs));
        if (remainingSnapshots <= remainingSends) {
            return currentIndex + 1;
        }

        int step = (int) Math.ceil((double) remainingSnapshots / remainingSends);
        return Math.min(lastIndex, currentIndex + Math.max(1, step));
    }

    private List<String> cleanSnapshots(List<String> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return List.of();
        }
        List<String> cleaned = new ArrayList<>();
        for (String snapshot : snapshots) {
            if (snapshot == null) {
                continue;
            }
            if (cleaned.isEmpty() || !snapshot.equals(cleaned.getLast())) {
                cleaned.add(snapshot);
            }
        }
        return cleaned;
    }

    private String privateStreamMessageUrl(String openId) {
        return apiBaseUrl + "/v2/users/" + openId + "/stream_messages";
    }

    private void callFailEvent(String url, String body) {
        if (body == null) {
            return;
        }
        try {
            JsonNode err = objectMapper.readTree(body);
            int code = err.path("err_code").asInt(0);
            String msg = err.path("message").asText(null);
            String userId = extractUrlPart(url, "/users/", "/stream_messages");
            if (userId != null) {
                EventManager.getInstance().callEvent(new OfficialC2CSendFailEvent(userId, code, msg));
            }
        } catch (Exception ignored) {
        }
    }

    private String extractUrlPart(String url, String startMarker, String endMarker) {
        int start = url.indexOf(startMarker);
        if (start < 0) {
            return null;
        }
        start += startMarker.length();
        int end = url.indexOf(endMarker, start);
        if (end < 0 || end <= start) {
            return null;
        }
        return url.substring(start, end);
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String await(CompletableFuture<String> future, String logType) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("{}消息发送等待被中断", logType, e);
            return null;
        } catch (Exception e) {
            Throwable cause = e.getCause();
            if (cause instanceof QQMessageSendException officialError) {
                throw officialError;
            }
            log.error("{}消息发送任务失败: {}", logType, cause != null ? cause : e);
            return null;
        }
    }
}
