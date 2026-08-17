package top.yzljc.atribot.webui.repo;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.database.DatabaseManager;
import top.yzljc.atribot.service.runtime.ThreadManager;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** 异步扫描并清理机器人已经退出的群所留下的消息记录。 */
@Slf4j
public final class OrphanedGroupRecordCleanup {

    private static final String GROUP_TABLE = "official_group_record";
    private static final int DELETE_BATCH_SIZE = 5_000;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static volatile CleanupStatus status = new CleanupStatus(
            "idle", "等待开始", false, 0,
            0, 0, 0, 0, 0, 0,
            List.of(), null, null, null);

    private OrphanedGroupRecordCleanup() {
    }

    public static CleanupStatus getStatus() {
        return status;
    }

    public static synchronized CleanupStatus start() {
        if (status.running()) return status;

        Progress progress = new Progress();
        progress.startedAt = now();
        progress.state = "scanning";
        progress.phase = "扫描消息表";
        progress.running = true;
        progress.publish();
        try {
            ThreadManager.execute(() -> run(progress));
        } catch (RuntimeException e) {
            progress.fail("无法启动后台任务: " + e.getMessage());
        }
        return status;
    }

    private static void run(Progress progress) {
        try {
            progress.totalGroups = countDistinctGroups();
            progress.publishScan();

            List<Candidate> candidates = scanCandidates(progress);
            progress.orphanedGroups = candidates.size();
            progress.orphanedRecords = candidates.stream().mapToLong(Candidate::records).sum();

            if (!candidates.isEmpty()) {
                progress.state = "archiving";
                progress.phase = "归档统计快照";
                progress.progress = 65;
                progress.publish();
                ChatStatsSnapshotRepo.archiveCurrentSnapshot();

                progress.state = "deleting";
                progress.phase = "删除无效群记录";
                progress.progress = 66;
                progress.publish();
                deleteCandidates(candidates, progress);
            }

            progress.running = false;
            progress.state = "completed";
            progress.phase = "清理完成";
            progress.progress = 100;
            progress.finishedAt = now();
            progress.publish();
            log.info("无效官方群记录清理完成: scannedGroups={}, orphanedGroups={}, deletedRecords={}",
                    progress.scannedGroups, progress.orphanedGroups, progress.deletedRecords);
        } catch (Exception e) {
            log.error("无效官方群记录清理失败", e);
            progress.fail(e.getMessage() == null ? "后台清理失败" : e.getMessage());
        }
    }

    private static long countDistinctGroups() throws SQLException {
        String sql = "SELECT COUNT(DISTINCT group_openId) FROM `" + GROUP_TABLE + "` " +
                "WHERE group_openId IS NOT NULL AND TRIM(group_openId) <> ''";
        try (var conn = DatabaseManager.getConnection(); var stmt = conn.prepareStatement(sql); var rs = stmt.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    private static List<Candidate> scanCandidates(Progress progress) throws SQLException {
        String sql = "SELECT group_openId, COUNT(*) AS record_count FROM `" + GROUP_TABLE + "` " +
                "WHERE group_openId IS NOT NULL AND TRIM(group_openId) <> '' " +
                "GROUP BY group_openId ORDER BY group_openId";
        List<Candidate> candidates = new ArrayList<>();
        try (var conn = DatabaseManager.getConnection(); var stmt = conn.prepareStatement(sql); var rs = stmt.executeQuery()) {
            while (rs.next()) {
                String groupOpenId = rs.getString("group_openId");
                if (!OfficialGroups.isCached(groupOpenId)) {
                    candidates.add(new Candidate(groupOpenId, rs.getLong("record_count")));
                }
                progress.scannedGroups++;
                progress.publishScan();
            }
        }
        return candidates;
    }

    private static void deleteCandidates(List<Candidate> candidates, Progress progress) throws SQLException {
        String sql = "DELETE FROM `" + GROUP_TABLE + "` WHERE group_openId = ? LIMIT ?";
        try (var conn = DatabaseManager.getConnection(); var stmt = conn.prepareStatement(sql)) {
            for (Candidate candidate : candidates) {
                long deletedForGroup = 0;
                while (!OfficialGroups.isCached(candidate.groupOpenId())) {
                    stmt.setString(1, candidate.groupOpenId());
                    stmt.setInt(2, DELETE_BATCH_SIZE);
                    int deleted = stmt.executeUpdate();
                    deletedForGroup += deleted;
                    progress.deletedRecords += deleted;
                    progress.publishDelete();
                    if (deleted < DELETE_BATCH_SIZE) break;
                }
                progress.processedGroups++;
                if (deletedForGroup > 0) progress.deletedGroupIds.add(candidate.groupOpenId());
                progress.publishDelete();
            }
        }
    }

    private static String now() {
        return LocalDateTime.now().format(TIME_FORMAT);
    }

    private record Candidate(String groupOpenId, long records) {
    }

    public record CleanupStatus(String state, String phase, boolean running, int progress,
                                long totalGroups, long scannedGroups, long orphanedGroups,
                                long orphanedRecords, long processedGroups, long deletedRecords,
                                List<String> deletedGroupIds, String error,
                                String startedAt, String finishedAt) {
    }

    private static final class Progress {
        private String state = "idle";
        private String phase = "等待开始";
        private boolean running;
        private int progress;
        private long totalGroups;
        private long scannedGroups;
        private long orphanedGroups;
        private long orphanedRecords;
        private long processedGroups;
        private long deletedRecords;
        private final List<String> deletedGroupIds = new ArrayList<>();
        private String error;
        private String startedAt;
        private String finishedAt;

        private void publishScan() {
            progress = totalGroups == 0 ? 60 : (int) Math.min(60, scannedGroups * 60 / totalGroups);
            publish();
        }

        private void publishDelete() {
            double recordRatio = orphanedRecords == 0 ? 1D : (double) deletedRecords / orphanedRecords;
            double groupRatio = orphanedGroups == 0 ? 1D : (double) processedGroups / orphanedGroups;
            progress = 66 + (int) Math.min(33, Math.max(recordRatio, groupRatio) * 33);
            publish();
        }

        private void fail(String message) {
            running = false;
            state = "failed";
            phase = "清理失败";
            error = message;
            finishedAt = now();
            publish();
        }

        private void publish() {
            status = new CleanupStatus(state, phase, running, progress,
                    totalGroups, scannedGroups, orphanedGroups, orphanedRecords,
                    processedGroups, deletedRecords, List.copyOf(deletedGroupIds),
                    error, startedAt, finishedAt);
        }
    }
}
