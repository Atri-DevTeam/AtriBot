package top.yzljc.atribot.chat.official.moderation;

/**
* @Author AndyOctopus
* @ClassName AiModerationVerdict
* @Created_at 2026/08/20
* @Project AtriMeow
* @Package top.yzljc.atribot.chat.official.moderation
*/
public record AiModerationVerdict(boolean violation, String reason) {
}
