package top.yzljc.qqbot.event;

import java.util.Map;

public record MessageSegment(String type, Map<String, Object> data) {

}
