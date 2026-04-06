package top.yzljc.qqbot.botservice.tools;

import top.yzljc.qqbot.chat.MessageSegment;

import java.util.*;

public class StructRawMessage {

    public static LinkedList<MessageSegment> parse(String rawMessage) {
        LinkedList<MessageSegment> resultList = new LinkedList<>();

        if (rawMessage == null || rawMessage.isEmpty()) {
            return resultList;
        }

        int length = rawMessage.length();
        int cursor = 0;

        while (cursor < length) {
            int cqStart = rawMessage.indexOf("[CQ:", cursor);

            if (cqStart == -1) {
                String text = rawMessage.substring(cursor);
                if (!text.isEmpty()) {
                    resultList.add(new MessageSegment("text", Map.of("text", text)));
                }
                break;
            }

            if (cqStart > cursor) {
                String text = rawMessage.substring(cursor, cqStart);
                if (!text.isEmpty()) {
                    resultList.add(new MessageSegment("text", Map.of("text", text)));
                }
            }

            int cqEnd = -1;
            int bracketLevel = 0;

            for (int i = cqStart; i < length; i++) {
                char c = rawMessage.charAt(i);
                if (c == '[') bracketLevel++;
                else if (c == ']') {
                    bracketLevel--;
                    if (bracketLevel == 0) {
                        cqEnd = i;
                        break;
                    }
                }
            }

            if (cqEnd == -1) {
                resultList.add(new MessageSegment("text",
                        Map.of("text", rawMessage.substring(cursor))));
                break;
            }

            String content = rawMessage.substring(cqStart + 4, cqEnd);
            parseCqContent(content, resultList);

            cursor = cqEnd + 1;
        }

        return resultList;
    }

    private static void parseCqContent(String content, List<MessageSegment> resultList) {
        int firstComma = content.indexOf(',');

        String type;
        String paramsPart = "";

        if (firstComma == -1) {
            type = content;
        } else {
            type = content.substring(0, firstComma);
            paramsPart = content.substring(firstComma + 1);
        }

        Map<String, Object> data = new LinkedHashMap<>();

        if (!paramsPart.isEmpty()) {
            // 使用正则分割，防止 URL 中可能自带的普通逗号被误切
            String[] pairs = paramsPart.split(",(?=[a-zA-Z0-9_]+=)");

            for (String pair : pairs) {
                int eqIdx = pair.indexOf('=');
                if (eqIdx > 0) {
                    String key = pair.substring(0, eqIdx).trim();
                    String val = pair.substring(eqIdx + 1);
                    data.put(key, val);
                }
            }
        }

        normalize(type, data);
        resultList.add(new MessageSegment(type, data));
    }

    private static void normalize(String type, Map<String, Object> data) {
        switch (type) {
            case "image", "video" -> {
                String url = (String) data.get("url");
                String file = (String) data.get("file");
                data.clear();

                if (url != null && !url.isEmpty()) {
                    data.put("url", url);
                } else if (file != null && !file.isEmpty()) {
                    data.put("url", file);
                }
            }
            case "at" -> {
                if (data.containsKey("qq")) {
                    data.put("user_id", data.remove("qq"));
                }
            }
            case "reply" -> {
                if (data.containsKey("id")) {
                    data.put("message_id", data.remove("id"));
                }
            }
        }
    }
}