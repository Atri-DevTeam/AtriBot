package top.yzljc.atribot.webui.repo;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.database.DatabaseManager;
import top.yzljc.atribot.service.runtime.ThreadManager;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** 异步扫描并清理已经不在好友登记中的用户所留下的消息记录。 */
@Slf4j
public final class OrphanedFriendRecordCleanup {

    private static final String C2C_TABLE = "official_c2c_record";
    private static final int DELETE_BATCH_SIZE = 5_000;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static volatile CleanupStatus status = new CleanupStatus(
            "idle", "等待开始", false, 0,
            0, 0, 0, 0, 0, 0,
            List.of(), null, null, null);

    private OrphanedFriendRecordCleanup() {
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
            progress.totalUsers = countDistinctUsers();
            progress.publishScan();

            List<Candidate> candidates = scanCandidates(progress);
            progress.orphanedUsers = candidates.size();
            progress.orphanedRecords = candidates.stream().mapToLong(Candidate::records).sum();

            if (!candidates.isEmpty()) {
                progress.state = "archiving";
                progress.phase = "归档统计快照";
                progress.progress = 65;
                progress.publish();
                ChatStatsSnapshotRepo.archiveCurrentSnapshot();

                progress.state = "deleting";
                progress.phase = "删除无效好友记录";
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
            log.info("无效官方好友记录清理完成: scannedUsers={}, orphanedUsers={}, deletedRecords={}",
                    progress.scannedUsers, progress.orphanedUsers, progress.deletedRecords);
        } catch (Exception e) {
            log.error("无效官方好友记录清理失败", e);
            progress.fail(e.getMessage() == null ? "后台清理失败" : e.getMessage());
        }
    }

    private static long countDistinctUsers() throws SQLException {
        String sql = "SELECT COUNT(DISTINCT union_openId) FROM `" + C2C_TABLE + "` " +
                "WHERE union_openId IS NOT NULL AND TRIM(union_openId) <> ''";
        try (var conn = DatabaseManager.getConnection(); var stmt = conn.prepareStatement(sql); var rs = stmt.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        }
    }

    private static List<Candidate> scanCandidates(Progress progress) throws SQLException {
        String sql = "SELECT r.union_openId, COUNT(*) AS record_count, MAX(r.id) AS last_id, " +
                "EXISTS(SELECT 1 FROM official_users u WHERE u.user_openId = r.union_openId) AS registered " +
                "FROM `" + C2C_TABLE + "` r " +
                "WHERE union_openId IS NOT NULL AND TRIM(union_openId) <> '' " +
                "GROUP BY union_openId ORDER BY union_openId";
        List<Candidate> candidates = new ArrayList<>();
        try (var conn = DatabaseManager.getConnection(); var stmt = conn.prepareStatement(sql); var rs = stmt.executeQuery()) {
            while (rs.next()) {
                String userOpenId = rs.getString("union_openId");
                if (!OfficialUsers.isCached(userOpenId) && !rs.getBoolean("registered")) {
                    candidates.add(new Candidate(userOpenId, rs.getLong("record_count"), rs.getLong("last_id")));
                }
                progress.scannedUsers++;
                progress.publishScan();
            }
        }
        return candidates;
    }

    private static void deleteCandidates(List<Candidate> candidates, Progress progress) throws SQLException {
        // 以数据库登记和扫描时的最大记录 ID 限定删除范围，保留重新加好友及扫描后新增的消息。
        String sql = "DELETE FROM `" + C2C_TABLE + "` WHERE union_openId = ? AND id <= ? " +
                "AND NOT EXISTS (SELECT 1 FROM official_users WHERE user_openId = ?) LIMIT ?";
        try (var conn = DatabaseManager.getConnection(); var stmt = conn.prepareStatement(sql)) {
            for (Candidate candidate : candidates) {
                long deletedForUser = 0;
                while (!OfficialUsers.isCached(candidate.userOpenId())) {
                    stmt.setString(1, candidate.userOpenId());
                    stmt.setLong(2, candidate.lastId());
                    stmt.setString(3, candidate.userOpenId());
                    stmt.setInt(4, DELETE_BATCH_SIZE);
                    int deleted = stmt.executeUpdate();
                    deletedForUser += deleted;
                    progress.deletedRecords += deleted;
                    progress.publishDelete();
                    if (deleted < DELETE_BATCH_SIZE) break;
                }
                progress.processedUsers++;
                if (deletedForUser > 0) progress.deletedUserIds.add(candidate.userOpenId());
                progress.publishDelete();
            }
        }
    }

    private static String now() {
        return LocalDateTime.now().format(TIME_FORMAT);
    }

    private record Candidate(String userOpenId, long records, long lastId) {
    }

    public record CleanupStatus(String state, String phase, boolean running, int progress,
                                long totalUsers, long scannedUsers, long orphanedUsers,
                                long orphanedRecords, long processedUsers, long deletedRecords,
                                List<String> deletedUserIds, String error,
                                String startedAt, String finishedAt) {
    }

    private static final class Progress {
        private String state = "idle";
        private String phase = "等待开始";
        private boolean running;
        private int progress;
        private long totalUsers;
        private long scannedUsers;
        private long orphanedUsers;
        private long orphanedRecords;
        private long processedUsers;
        private long deletedRecords;
        private final List<String> deletedUserIds = new ArrayList<>();
        private String error;
        private String startedAt;
        private String finishedAt;

        private void publishScan() {
            progress = totalUsers == 0 ? 60 : (int) Math.min(60, scannedUsers * 60 / totalUsers);
            publish();
        }

        private void publishDelete() {
            double recordRatio = orphanedRecords == 0 ? 1D : (double) deletedRecords / orphanedRecords;
            double userRatio = orphanedUsers == 0 ? 1D : (double) processedUsers / orphanedUsers;
            progress = 66 + (int) Math.min(33, Math.max(recordRatio, userRatio) * 33);
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
                    totalUsers, scannedUsers, orphanedUsers, orphanedRecords,
                    processedUsers, deletedRecords, List.copyOf(deletedUserIds),
                    error, startedAt, finishedAt);
        }
    }
}
