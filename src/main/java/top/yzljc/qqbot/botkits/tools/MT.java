package top.yzljc.qqbot.botkits.tools;

import top.yzljc.qqbot.botkits.message.MessageSender;

import java.util.*;

public class MT {
    private static final LinkedList<Map<String,Object>> replayContent = new LinkedList<>();
    private static final Map<String,Object> replyMsg = new HashMap<>();
    private static final Map<String,Object> replyText = new HashMap<>();
    private static final Map<String,Object> replyAt = new HashMap<>();
    private static final String FAKE_UIN = "3614865692";
    private static final String FAKE_NAME = "YZ_Ljc_";

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

    public static void atUser(long userId,long groupId,String text){
        replyAt.put("qq",userId);
        replayContent.add(Map.of("type","at","data",replyAt));

        replyText.put("text",text);
        replayContent.add(Map.of("type","text","data",replyText));

        MessageSender.sendGroupData(groupId,replayContent);

        replyAt.clear();
        replyText.clear();
        replayContent.clear();
    }

    public static Map<String, Object> createTextNode(String text) {
        return createTextNode(text, FAKE_UIN, FAKE_NAME);
    }

    public static Map<String, Object> createTextNode(String text,String uin, String name) {
        Map<String, Object> msgData = new HashMap<>();
        msgData.put("type", "node");
        Map<String, Object> data = new HashMap<>();
        data.put("uin", uin);
        data.put("name", name);

        List<Map<String, Object>> contentList = new ArrayList<>();
        Map<String, Object> textItem = new HashMap<>();
        textItem.put("type", "text");
        Map<String, Object> textData = new HashMap<>();
        textData.put("text", text);
        textItem.put("data", textData);
        contentList.add(textItem);

        data.put("content", contentList);
        msgData.put("data", data);
        return msgData;
    }

    public static Map<String, Object> createImageNode(String url) {
        return createImageNode(url, FAKE_UIN, FAKE_NAME);
    }

    public static Map<String, Object> createImageNode(String url, String uin, String name) {
        Map<String, Object> node = new HashMap<>();
        node.put("type", "node");
        Map<String, Object> data = new HashMap<>();
        data.put("uin", FAKE_UIN);
        data.put("name", FAKE_NAME);

        List<Map<String, Object>> contentList = new ArrayList<>();
        Map<String, Object> imgItem = new HashMap<>();
        imgItem.put("type", "image");
        Map<String, Object> imgData = new HashMap<>();
        imgData.put("file", url);
        imgItem.put("data", imgData);
        contentList.add(imgItem);

        data.put("content", contentList);
        node.put("data", data);
        return node;
    }
}
