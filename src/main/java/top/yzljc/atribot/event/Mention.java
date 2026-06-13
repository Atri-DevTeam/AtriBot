package top.yzljc.atribot.event;

/**
 * @Author YZ_Ljc_
 * @ClassName Mention
 * @Created_at 2026/06/08
 * @Project AtriBot
 * @Package top.yzljc.atribot.event
 */
public record Mention(long userUin, String userNtId, String content) {
}