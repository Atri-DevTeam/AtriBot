package top.yzljc.atribot.function.napcat.personal;

import com.fasterxml.jackson.databind.node.ArrayNode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.NapcatGroupMessageEvent;
import top.yzljc.atribot.event.events.NapcatRecallMessageEvent;
import top.yzljc.atribot.event.events.OfficialGroupMessageCreateEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName AutoSendPtt
 * @Created_at 2026/07/07
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.napcat
 */
@Slf4j
public class AutoSendPtt implements Listener {

    // md5为键
    private static final Map<String, PttRecord> recordedPtt = new HashMap<>();

//    @EventHandler
//    public void onGroupChat(NapcatGroupMessageEvent event) {
//        if (!Config.getInstance().getNapcatMessageSpyGroups().contains(event.getGroupId())) return;
//        var data = event.getMessage().getAttachments();
//        if (data.isArray()) {
//            ArrayNode messageArray = (ArrayNode) data;
//            if (messageArray.size() != 1) return;
//            var message = messageArray.get(0);
//            if (message.path("type").asText().equals("record")) {
//                var d = PostRequest.getSimplePostResult(RequestType.FETCH_PTT_TEXT, "message_id", event.getMessage().getMessageId());
//                log.debug(d.toString());
//                if (d == null) return;
//                var t = d.path("data").path("text").asText(null);
//                if (t != null) {
//                    var text = event.getUser().getUsername() + "说: " + t;
//                    GroupMessage.chatMessage(event.getGroupId(), text);
//                }
//            }
//        }
//    }

    @EventHandler
    public void onGroupChat(NapcatGroupMessageEvent event) {
        if (!Config.getInstance().getNapcatMessageSpyGroups().contains(event.getGroupId())) return;
        var data = event.getMessage().getAttachments();
        if (data.isArray()) {
            ArrayNode messageArray = (ArrayNode) data;
            if (messageArray.size() != 1) return;
            var message = messageArray.get(0);
            if (message.path("type").asText().equals("record")) {
                var md5 = message.path("data").path("file").asText(null);
                if (md5 != null) {
                    recordedPtt.put(md5, new PttRecord(event.getMessage().getMessageId(), null));
//                    log.debug("Recorded PTT message with md5: {}", md5);
                }
            }
        }
    }

    @EventHandler
    public void onGroupMessage(OfficialGroupMessageCreateEvent event) {
        if (!OfficialGroups.isWhitelist(event.getGroupId())) return;
        var d = event.getMessage().getAttachments();
        if (d == null || d.isMissingNode() || d.isEmpty()) return;
        if (d.isArray()) {
            var data = (ArrayNode) d;
            if (data.size() != 1) return;
            var a = data.get(0);
            var v = a.path("content_type").asText(null);
            if (v != null) {
                var t = a.path("asr_refer_text").asText(null);
                var md5 = a.path("filename").asText("-");
                if (t != null) {
                    var text = event.getUser().getUsername() + "说: " + t;
                    log.info("转文字-> {}", text);
                    String napcatGroupId = String.valueOf(OfficialGroups.getRealGroupId(event.getGroupId()));
                    if (napcatGroupId != null && !napcatGroupId.equals("0")) {
                        var mid = GroupMessage.chatMessage(napcatGroupId, text);
                        if (mid != null && recordedPtt.containsKey(md5)) {
                            var record = recordedPtt.get(md5);
                            record.setPttMessageId(mid);
//                            log.debug("Updated PTT record for md5 {}: rawMessageId={}, pttMessageId={}", md5, record.getRawMessageId(), record.getPttMessageId());
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onGroupRecallMessage(NapcatRecallMessageEvent event) {
        if (!Config.getInstance().getNapcatMessageSpyGroups().contains(event.getGroupId())) return;
        var mid = event.getMessageId();
        recordedPtt.values().stream()
                .filter(e -> e.rawMessageId.equals(mid))
                .findFirst()
                .ifPresent(e -> {
                    var pttMid = e.pttMessageId;
                    if (pttMid != null) {
                        GroupMessage.recallMessage(pttMid);
//                        log.debug("Recalled PTT message with id: {}", pttMid);
                    }
                });
    }

    @Data
    private class PttRecord {
        private String rawMessageId;
        private String pttMessageId;

        public PttRecord(String rawMessageId, String pttMessageId) {
            this.rawMessageId = rawMessageId;
            this.pttMessageId = pttMessageId;
        }
    }
}