package top.yzljc.atribot.function.background;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @Author YZ_Ljc_
 * @ClassName BackgroundSettings
 * @Created_at 2026/10/11
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.background
 */
public record BackgroundSettings(String activeId, Submission submission) {
    public static final BackgroundSettings EMPTY = new BackgroundSettings(null, null);

    public Set<String> retainedIds() {
        Set<String> ids = new LinkedHashSet<>();
        if (activeId != null) ids.add(activeId);
        if (submission != null) ids.add(submission.id());
        return ids;
    }

    public BackgroundSettings submit(Submission next) {
        if (submission != null && "PENDING".equals(submission.status()))
            throw new IllegalArgumentException("已有背景等待审核，请等待审核后再提交");
        return new BackgroundSettings(activeId, next);
    }

    public BackgroundSettings review(String id, String status, String remark, String reviewer) {
        if (submission == null || !submission.id().equals(id))
            throw new IllegalArgumentException("申请已被替换，请刷新列表");
        if (!Set.of("PENDING", "REVIEWED", "DENIED").contains(status))
            throw new IllegalArgumentException("审核状态无效");
        String active = "REVIEWED".equals(status) ? id : id.equals(activeId) ? null : activeId;
        Submission next = new Submission(id, status, submission.uploaderName(), submission.groupId(), submission.platform(),
                submission.width(), submission.height(), submission.size(), submission.createdAt(),
                System.currentTimeMillis(), reviewer, remark);
        return new BackgroundSettings(active, next);
    }

    public BackgroundSettings delete(String id) {
        if (submission == null || !submission.id().equals(id))
            throw new IllegalArgumentException("申请已被替换，请刷新列表");
        return new BackgroundSettings(id.equals(activeId) ? null : activeId, null);
    }

    public record Submission(String id, String status, String uploaderName, String groupId, String platform,
                             int width, int height, long size, long createdAt, long reviewedAt,
                             String reviewer, String remark) {}
}
