package top.yzljc.qqbot.botkits.tools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MM {
    public static List<Map<String, Object>> parse(String rawMessage) {
        List<Map<String, Object>> resultList = new ArrayList<>();

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
                    resultList.add(buildTextNode(text));
                }
                break;
            }

            if (cqStart > cursor) {
                String text = rawMessage.substring(cursor, cqStart);
                if (!text.isEmpty()) {
                    resultList.add(buildTextNode(text));
                }
            }

            int cqEnd = -1;
            int bracketLevel = 0;

            for (int i = cqStart; i < length; i++) {
                char c = rawMessage.charAt(i);
                if (c == '[') {
                    bracketLevel++;
                } else if (c == ']') {
                    bracketLevel--;
                    if (bracketLevel == 0) {
                        cqEnd = i;
                        break;
                    }
                }
            }
            if (cqEnd == -1) {
                String text = rawMessage.substring(cursor);
                resultList.add(buildTextNode(text));
                break;
            }

            String content = rawMessage.substring(cqStart + 4, cqEnd);

            parseCqContent(content, resultList);
            cursor = cqEnd + 1;
        }

        return resultList;
    }

    private static Map<String, Object> buildTextNode(String text) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("type", "text");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("text", text);
        root.put("data", data);
        return root;
    }

    private static void parseCqContent(String content, List<Map<String, Object>> resultList) {
        int firstComma = content.indexOf(',');

        String type;
        String paramsPart = "";

        if (firstComma == -1) {
            type = content;
        } else {
            type = content.substring(0, firstComma);
            paramsPart = content.substring(firstComma + 1);
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("type", type);

        Map<String, Object> data = new LinkedHashMap<>();

        if (!paramsPart.isEmpty()) {
            String[] pairs = paramsPart.split(",");

            for (String pair : pairs) {
                int eqIdx = pair.indexOf('=');
                if (eqIdx > 0) {
                    String key = pair.substring(0, eqIdx).trim();
                    String val = pair.substring(eqIdx + 1);

                    if ("url".equals(key)) {
                        data.put(key, val);
                    }
                }
            }
        }

        root.put("data", data);
        resultList.add(root);
    }
}