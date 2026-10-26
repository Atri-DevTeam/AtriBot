package top.yzljc.atribot.database;

import java.sql.Timestamp;

/**
 * 工单追加记录，操作者身份仅供管理端查看
 *
 * @Author YZ_Ljc_
 * @ClassName FeedbackEntry
 * @Created_at 2026/10/26
 * @Project AtriMeow
 * @Package top.yzljc.atribot.database
 */
public record FeedbackEntry(long id, String feedbackId, String type, String content,
                            String actorId, String actorName, Timestamp createTime,
                            String platform, String groupId, boolean silent, String actorAvatarUrl) {
}
