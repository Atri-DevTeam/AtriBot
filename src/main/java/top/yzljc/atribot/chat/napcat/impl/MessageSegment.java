package top.yzljc.atribot.chat.napcat.impl;

import java.util.Map;

public record MessageSegment(String type, Map<String, Object> data) {
}
