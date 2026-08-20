package top.yzljc.atribot.chat.official.moderation;

import lombok.Data;

/**
* @Author AndyOctopus
* @ClassName ModerationAction
* @Created_at 2026/08/20
* @Project AtriMeow
* @Package top.yzljc.atribot.chat.official.moderation
*/
@Data
public class ModerationAction {
    private boolean remind = true;
    private String remindMessage = "你的消息违规了哦";
    private boolean recall = true;
    private boolean mute = false;
    private long muteSeconds = 0;
    private boolean notifyDebugGroup = false;
}
