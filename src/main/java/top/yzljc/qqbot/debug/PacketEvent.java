package top.yzljc.qqbot.debug;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;
import top.yzljc.qqbot.messages.MessageSender;

import java.util.List;

/**
 * 全局数据包调试监听器
 * 用于将接收到的所有原始数据转发到调试群
 * 这个玩意只用测试数据用，平时用不到，瞎几把打印数据会死人的
 */
public class PacketEvent {
    // 调试模式开关（默认关闭）
    private static volatile boolean isDebugEnabled = false;
    // 过滤的目标群号 (null表示监听全局，有值表示只监听特定群)
    private static volatile Long targetFilterGroupId = null;

    static Settings settings = Config.getInstance();
    private static final List<Long> admins = settings.getAdminUids();
    private static final long botUid = settings.getBotUid();
    private static final long debugGroupId = settings.getDebugGroupId();
    /**
     * 处理入口，建议放在 MessageProcessor.processMessage 的第一行
     * @param json 原始数据包
     */
    public static void process(JsonNode json) {
        // 1. 优先检查开关控制指令 (无论当前是否开启调试，都要能接收指令)
        if (checkToggleCommand(json)) {
            return;
        }

        // 2. 如果调试模式开启，则处理转发逻辑
        if (isDebugEnabled) {
            // 如果设置了群号过滤，且当前数据包的群号不匹配，则跳过
            if (targetFilterGroupId != null) {
                long currentGroupId = json.path("group_id").asLong(0);
                if (currentGroupId != targetFilterGroupId) {
                    return;
                }
            }
            // 执行转发
            forwardDebugPacket(json);
        }
    }

    /**
     * 检查是否为开启/关闭调试的指令
     * 支持格式：
     * /debug       -> 开启全局监听 / 关闭监听
     * /debug 12345 -> 开启指定群(12345)监听
     */
    private static boolean checkToggleCommand(JsonNode json) {
        // 必须是消息类型
        if (!"message".equals(json.path("post_type").asText())) {
            return false;
        }

        // 检查发送者是否为管理员
        long userId = json.path("user_id").asLong();
        if (admins.contains(userId)) {
            return false;
        }

        // 获取指令内容并分割
        String rawMsg = json.path("raw_message").asText().trim();
        String[] parts = rawMsg.split("\\s+");

        if (parts.length > 0 && "/debug".equalsIgnoreCase(parts[0])) {
            long fromGroupId = json.path("group_id").asLong();
            String statusMsg;

            // 情况1: /debug <群号> (指定监听)
            if (parts.length == 2) {
                try {
                    long targetGid = Long.parseLong(parts[1]);
                    isDebugEnabled = true;
                    targetFilterGroupId = targetGid;
                    statusMsg = "[System] Debug 模式已开启 (过滤模式)！\n只监听来自群 " + targetGid + " 的数据包。\n数据将转发至群 " + debugGroupId;
                } catch (NumberFormatException e) {
                    statusMsg = "[System] 群号格式错误，请使用纯数字。";
                }
            }
            // 情况2: /debug (全局开关切换)
            else {
                // 切换状态
                isDebugEnabled = !isDebugEnabled;
                // 只要切换全局开关，就重置过滤条件
                targetFilterGroupId = null;

                if (isDebugEnabled) {
                    statusMsg = "[System] Debug 模式已开启 (全局模式)！\n所有收到的原始数据包将转发至群 " + debugGroupId;
                } else {
                    statusMsg = "[System] Debug 模式已关闭。";
                }
            }

            System.out.println(statusMsg);

            // 发送反馈消息
            if (fromGroupId > 0) {
                MessageSender.sendGroupMessage(fromGroupId, statusMsg);
            } else {
                System.out.println("无效的群号，无法发送状态提示！");
            }
            return true;
        }
        return false;
    }
    /**
     * 转发数据包到调试群
     */
    private static void forwardDebugPacket(JsonNode json) {
        try {
            // 将 JSON 转为格式化的字符串，方便查看
            String jsonString = json.toPrettyString();
            // 发送
            long userId = json.path("user_id").asLong(0);

            if (userId == botUid) {
                return;
            }

            MessageSender.sendGroupMessage(debugGroupId, jsonString);

        } catch (Exception e) {
            System.err.println("[PacketEvent] Forwarding failed: " + e.getMessage());
        }
    }
}