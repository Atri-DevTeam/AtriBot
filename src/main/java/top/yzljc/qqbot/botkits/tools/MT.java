package top.yzljc.qqbot.botkits.tools;

import top.yzljc.qqbot.botkits.message.MessageSender;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class MT {
    private static final LinkedList<Map<String,Object>> replayContent = new LinkedList<>();
    private static final Map<String,Object> replyMsg = new HashMap<>();
    private static final Map<String,Object> replyText = new HashMap<>();
    private static final Map<String,Object> replyAt = new HashMap<>();

    public static void replyMessage(long userId, long groupId,long messageId,String text){
        replyMessage(userId,groupId,messageId,false,text);
    }

    public static void replyMessage(long userId,long groupId,long messageId,boolean whetherAt,String text){
        if (whetherAt){
            replyAt.put("qq",userId);
            replayContent.add(Map.of("type","at","data",replyAt));
        }

        replyMsg.put("id",messageId);
        replayContent.add(Map.of("type", "reply","data",replyMsg));

        replyText.put("text",text);
        replayContent.add(Map.of("type","text","data",replyText));

        MessageSender.sendGroupData(groupId,replayContent);

        replyAt.clear();
        replyMsg.clear();
        replyText.clear();
        replayContent.clear();
    }
}
