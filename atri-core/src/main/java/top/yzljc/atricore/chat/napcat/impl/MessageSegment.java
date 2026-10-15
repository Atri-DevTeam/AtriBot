package top.yzljc.atricore.chat.napcat.impl;

import java.util.Map;

/**
 * OneBot 消息段结构
 *
 * @Author YZ_Ljc_
 * @ClassName MessageSegment
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.chat.napcat.impl
 */
public record MessageSegment(String type, Map<String, Object> data) {
}
