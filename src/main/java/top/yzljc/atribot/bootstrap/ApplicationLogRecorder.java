package top.yzljc.atribot.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atricore.core.LogRecorder;
import top.yzljc.atricore.chat.official.MessageBody;
import top.yzljc.atricore.chat.napcat.NapcatDebugGroup;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.database.repo.OfficialSendLogRepository;
import top.yzljc.atribot.database.repo.EventLogRepository;
import top.yzljc.atribot.function.tasks.QQChatContentRecord;
import top.yzljc.atribot.function.admin.DebugCommand;
import top.yzljc.atribot.utils.tools.Alert;
import top.yzljc.atribot.utils.tools.RM;
import top.yzljc.atribot.utils.debug.NapcatPacket;
import top.yzljc.atribot.utils.statistic.BotRuntimeData;
import top.yzljc.atribot.utils.debug.MessageChainDebug;
import top.yzljc.atricore.command.CommandSender;
import top.yzljc.atricore.utils.debug.MessageTrace;

/**
 * @Author YZ_Ljc_
 * @ClassName ApplicationLogRecorder
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atribot.bootstrap
 */
public final class ApplicationLogRecorder extends LogRecorder {
    private static final Logger log = LoggerFactory.getLogger(ApplicationLogRecorder.class);

    @Override
    public boolean isMessageTraceEnabled(CommandSender sender, String commandName) {
        return MessageChainDebug.isEnabled(sender, commandName);
    }

    @Override
    public void recordMessageTrace(CommandSender sender, MessageTrace.Report report) {
        MessageChainDebug.publish(sender, report);
    }

    @Override
    public String recordSend(String scene, String method, String url, String requestJson) {
        return OfficialSendLogRepository.recordSend(scene, method, url, requestJson);
    }

    @Override
    public void recordResponse(String traceId, String scene, String method, String url, String requestJson, Integer responseStatus, String responseBody) {
        OfficialSendLogRepository.recordResponse(traceId, scene, method, url, requestJson, responseStatus, responseBody);
    }

    @Override
    public void recordError(String traceId, String scene, String method, String url, String requestJson, Integer responseStatus, String responseBody, String errorMessage) {
        OfficialSendLogRepository.recordError(traceId, scene, method, url, requestJson, responseStatus, responseBody, errorMessage);
    }

    @Override
    public void recordEvent(String eventType, String eventId, Integer seq, String rawData) {
        EventLogRepository.record(eventType, eventId, seq, rawData);
    }

    @Override
    public void recordSentGroupMessage(String groupId, MessageBody request, String messageId, String refIdx, String timestamp) {
        QQChatContentRecord.recordSentGroupMessage(groupId, request, messageId, refIdx, timestamp);
    }

    @Override
    public void recordSentC2CMessage(String userId, MessageBody request, String messageId, String refIdx, String timestamp) {
        QQChatContentRecord.recordSentC2CMessage(userId, request, messageId, refIdx, timestamp);
    }

    @Override
    public void alert(String message) {
        Alert.notify(message);
    }

    @Override
    public void napcatPacket(JsonNode data) {
        NapcatPacket.process(data);
    }

    @Override
    public void recordLastMsg(String groupId, String messageId) {
        RM.recordLastMsg(groupId, messageId);
    }

    @Override
    public void callGroupMessageSend(String groupId) {
        BotRuntimeData.callGroupMessageSend(groupId);
    }

    @Override
    public void callPrivateMessageSend() {
        BotRuntimeData.callPrivateMessageSend();
    }

    @Override
    public void callRecallMessage() {
        BotRuntimeData.callRecallMessage();
    }

    @Override
    public void callCommandExecuted() {
        BotRuntimeData.callCommandExecuted();
    }

    @Override
    public void qqEventDispatched(String eventType, JsonNode eventData) {
        if (DebugCommand.isQQDebugEnabled.get()) {
            if ("GROUP_MESSAGE_CREATE".equals(eventType)
                    && eventData.path("author").path("union_openid").asText("")
                    .equalsIgnoreCase("68FA9563EC62B0F43E9BE5B3023B860F")) return;
            log.debug(eventData.toString());
            if (DebugCommand.type == DebugCommand.DebugDisplayType.DEBUG_GROUP) {
                NapcatDebugGroup.sendAsync("事件类型: " + eventType + "\n事件数据: " + eventData);
            }
        }
        if (Config.getInstance().getEnv().equals("dev")) log.debug("{}\n{}", eventType, eventData);
    }
}
