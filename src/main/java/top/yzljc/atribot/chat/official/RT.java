package top.yzljc.atribot.chat.official;

/**
 * @Author YZ_Ljc_
 * @ClassName RT
 * @Created_at 2026/09/16
 * @Project AtriBot
 * @Package top.yzljc.atribot.chat.official
 */
public sealed interface RT {

    /**
     * 获取触发本次被动回复的消息或事件 ID
     *
     * @return 消息 ID 或事件 ID
     */
    String id();

    /**
     * 创建消息驱动的被动回复来源
     *
     * @param msgId 收到的消息 ID，对应请求中的 msg_id
     * @return 消息回复来源，底层按该消息 ID 分配回复序号
     * @throws IllegalArgumentException 消息 ID 为 null 或空白时抛出
     */
    static RT message(String msgId) {
        return new Message(msgId);
    }

    /**
     * 创建事件驱动的被动回复来源
     *
     * @param eventId 事件推送 ID，对应请求中的 event_id
     * @return 事件回复来源，不自动分配消息回复序号
     * @throws IllegalArgumentException 事件 ID 为 null 或空白时抛出
     */
    static RT event(String eventId) {
        return new Event(eventId);
    }

    /**
     * 消息驱动的被动回复来源
     *
     * @param id 收到的消息 ID，对应请求中的 msg_id
     */
    record Message(String id) implements RT {
        public Message {
            requireId(id);
        }
    }

    /**
     * 事件驱动的被动回复来源
     *
     * @param id 事件推送 ID，对应请求中的 event_id
     */
    record Event(String id) implements RT {
        public Event {
            requireId(id);
        }
    }

    private static void requireId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("被动消息中msg_id和event_id不能同时为空");
        }
    }
}
