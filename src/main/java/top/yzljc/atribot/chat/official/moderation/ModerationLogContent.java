package top.yzljc.atribot.chat.official.moderation;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.atribot.event.events.OfficialGroupJoinRequestEvent;
import top.yzljc.atribot.platform.qq.QQMessage;

final class ModerationLogContent {
    private ModerationLogContent() {}

    static String message(QQMessage message) {
        var result = new StringBuilder(text(message.getContent()));
        appendJson(result, "附件", message.getAttachments());
        appendJson(result, "卡片", message.getArk());
        return result.toString();
    }

    static String joinRequest(OfficialGroupJoinRequestEvent event) {
        var result = new StringBuilder();
        if (event.getVerifyMessage() != null) result.append(event.getVerifyMessage());
        if (event.getVerifyQAList() != null) {
            for (var qa : event.getVerifyQAList()) {
                if (qa == null) continue;
                if (!result.isEmpty()) result.append("\n\n");
                result.append("问题：").append(text(qa.question()))
                        .append("\n回答：").append(text(qa.answer()));
            }
        }
        return result.toString();
    }

    private static void appendJson(StringBuilder result, String label, JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode() || node.isEmpty()) return;
        if (!result.isEmpty()) result.append("\n\n");
        result.append(label).append("：\n").append(node.toPrettyString());
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
