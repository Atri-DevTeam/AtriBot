package top.yzljc.atribot.database.repo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import top.yzljc.atribot.database.DatabaseManager;
import top.yzljc.atribot.survey.SurveyDefinition;
import top.yzljc.atribot.survey.SurveyId;
import top.yzljc.atribot.survey.SurveySubmissionLimiter;
import top.yzljc.atribot.survey.SurveyDefinition.*;
import top.yzljc.atribot.webui.ApiCode;

import java.sql.*;
import java.util.*;

/**
 * @Author YZ_Ljc_
 * @ClassName SurveyRepository
 * @Created_at 2026/10/30
 * @Project AtriMeow
 * @Package top.yzljc.atribot.database.repo
 */
public final class SurveyRepository {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final SurveySubmissionLimiter SUBMISSION_LIMITER = new SurveySubmissionLimiter();
    private final Connections connections;

    @FunctionalInterface
    public interface Connections { Connection open() throws SQLException; }

    public record Survey(String id, String title, String description, List<Question> questions, Status status,
                         int revision, long responseCount, long createdAt, long updatedAt, int rewardGold, boolean visible, boolean pinned, boolean repeatable) {}
    public record Summary(String id, String title, String description, Status status, long responseCount,
                          long updatedAt, boolean submitted, int rewardGold, boolean visible, boolean pinned, boolean repeatable) {}
    public record Answer(long id, String accountId, String userOpenId, String username,
                         Map<String, List<String>> answers, long submittedAt, int rewardGold) {}
    public record Page<T>(List<T> items, long total) {}
    public record Detail(Survey survey, Answer submission) {}
    public record ResponseSummary(long id, String userOpenId, String username, long submittedAt, boolean starred) {}
    public record ResponsePage(List<ResponseSummary> items, long total, long snapshot, int page, int pageSize) {}
    public record ResponseDetail(Answer answer, boolean starred, long position, long total, long snapshot) {}

    public SurveyRepository() { this(DatabaseManager::getConnection); }
    public SurveyRepository(Connections connections) { this.connections = connections; }

    public void init() throws SQLException {
        try (var con = connections.open(); var statement = con.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS surveys (
                      id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                      public_id CHAR(12) NOT NULL UNIQUE,
                      title VARCHAR(120) NOT NULL,
                      description TEXT NOT NULL,
                      questions JSON NOT NULL,
                      status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
                      revision INT NOT NULL DEFAULT 1,
                      reward_gold INT NOT NULL DEFAULT 0,
                      visible BOOLEAN NOT NULL DEFAULT TRUE,
                      pinned BOOLEAN NOT NULL DEFAULT FALSE,
                      repeatable BOOLEAN NOT NULL DEFAULT FALSE,
                      response_count BIGINT NOT NULL DEFAULT 0,
                      created_at BIGINT NOT NULL,
                      updated_at BIGINT NOT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS survey_responses (
                      id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                      survey_id BIGINT NOT NULL,
                      account_id VARCHAR(36) NOT NULL,
                      user_open_id VARCHAR(128) NOT NULL,
                      username VARCHAR(255) NOT NULL,
                      answers JSON NOT NULL,
                      submitted_at BIGINT NOT NULL,
                      starred BOOLEAN NOT NULL DEFAULT FALSE,
                      reward_gold INT NOT NULL DEFAULT 0,
                      request_id CHAR(36),
                      UNIQUE KEY uq_survey_request (survey_id, account_id, request_id),
                      KEY idx_survey_account (survey_id, account_id, id),
                      KEY idx_survey_responses (survey_id, id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
        }
    }

    private static boolean duplicateKey(SQLException e) {
        return e.getErrorCode() == 1062 || "23505".equals(e.getSQLState());
    }

    public Page<Summary> list(String accountId, int offset) throws SQLException {
        boolean admin = accountId == null;
        String filter = admin ? "" : " WHERE s.status <> 'DRAFT' AND s.visible=TRUE";
        try (var con = connections.open()) {
            long total;
            try (var ps = con.prepareStatement("SELECT COUNT(*) FROM surveys s" + filter); var rows = ps.executeQuery()) {
                rows.next(); total = rows.getLong(1);
            }
            List<Summary> result = new ArrayList<>();
            try (var ps = con.prepareStatement("SELECT s.public_id,s.title,s.description,s.status,s.response_count,s.updated_at,"
                    + " EXISTS(SELECT 1 FROM survey_responses r WHERE r.survey_id=s.id AND r.account_id=?) AS submitted,s.reward_gold,s.visible,s.pinned,s.repeatable"
                    + " FROM surveys s" + filter + " ORDER BY s.pinned DESC,s.id DESC LIMIT 50 OFFSET ?")) {
                ps.setString(1, accountId); ps.setInt(2, offset);
                try (var rows = ps.executeQuery()) {
                    while (rows.next()) result.add(new Summary(rows.getString(1), rows.getString(2), rows.getString(3),
                            Status.valueOf(rows.getString(4)), rows.getLong(5), rows.getLong(6), rows.getBoolean(7), rows.getInt(8), rows.getBoolean(9), rows.getBoolean(10), rows.getBoolean(11)));
                }
            }
            return new Page<>(result, total);
        }
    }

    public String create(SurveyDefinition definition) throws SQLException {
        return create(definition, 0, true);
    }

    public String create(SurveyDefinition definition, int rewardGold, boolean visible) throws SQLException {
        return create(definition, rewardGold, visible, false);
    }

    public String create(SurveyDefinition definition, int rewardGold, boolean visible, boolean pinned) throws SQLException {
        return create(definition, rewardGold, visible, pinned, false);
    }

    public String create(SurveyDefinition definition, int rewardGold, boolean visible, boolean pinned, boolean repeatable) throws SQLException {
        if (rewardGold < 0) throw new Failure(ApiCode.SURVEY_INVALID);
        definition = definition.validated();
        try (var con = connections.open(); var ps = con.prepareStatement("INSERT INTO surveys"
                + " (public_id,title,description,questions,created_at,updated_at,reward_gold,visible,pinned,repeatable) VALUES (?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(2, definition.title()); ps.setString(3, definition.description()); ps.setString(4, encode(definition.questions()));
            long now = System.currentTimeMillis(); ps.setLong(5, now); ps.setLong(6, now);
            ps.setInt(7, rewardGold); ps.setBoolean(8, visible); ps.setBoolean(9, pinned); ps.setBoolean(10, repeatable);
            for (int attempt = 0; attempt < 5; attempt++) {
                String id = SurveyId.create();
                ps.setString(1, id);
                try { ps.executeUpdate(); return id; }
                catch (SQLException e) { if (!duplicateKey(e) || attempt == 4) throw e; }
            }
            throw new SQLException("无法生成问卷编号");
        }
    }

    public Detail detail(String publicId, String accountId) throws SQLException {
        try (var con = connections.open()) {
            long id = internalId(con, publicId, false);
            var survey = find(con, id, false);
            if (accountId != null && survey.status == Status.DRAFT) throw new Failure(ApiCode.SURVEY_NOT_FOUND);
            return new Detail(survey, accountId == null ? null : answer(con, id, accountId));
        }
    }

    public void update(String publicId, int revision, SurveyDefinition definition) throws SQLException {
        var normalized = definition.validated();
        transact(con -> {
            long id = internalId(con, publicId, true);
            var survey = find(con, id, true);
            if (survey.revision != revision) throw new Failure(ApiCode.SURVEY_CHANGED);
            if (survey.status != Status.DRAFT) normalized.requireSameStructure(survey.questions);
            try (var ps = con.prepareStatement("UPDATE surveys SET title=?,description=?,questions=?,revision=revision+1,updated_at=? WHERE id=?")) {
                ps.setString(1, normalized.title()); ps.setString(2, normalized.description()); ps.setString(3, encode(normalized.questions()));
                ps.setLong(4, System.currentTimeMillis()); ps.setLong(5, id); ps.executeUpdate();
            }
            return null;
        });
    }

    public void status(String publicId, int revision, Status status) throws SQLException {
        if (status == null || status == Status.DRAFT) throw new Failure(ApiCode.SURVEY_INVALID);
        transact(con -> {
            long id = internalId(con, publicId, true);
            var survey = find(con, id, true);
            if (survey.revision != revision) throw new Failure(ApiCode.SURVEY_CHANGED);
            if (survey.status == Status.DRAFT && status != Status.PUBLISHED) throw new Failure(ApiCode.SURVEY_INVALID);
            try (var ps = con.prepareStatement("UPDATE surveys SET status=?,revision=revision+1,updated_at=? WHERE id=?")) {
                ps.setString(1, status.name()); ps.setLong(2, System.currentTimeMillis()); ps.setLong(3, id); ps.executeUpdate();
            }
            return null;
        });
    }

    /**
     * 修改奖励、列表可见性、置顶及重复填写设置，不受问卷收集状态限制，奖励仅适用于后续首次提交
     *
     * @return 更新后的问卷，包含新的版本号
     * @throws SQLException 数据库操作失败
     */
    public Survey settings(String publicId, int revision, int rewardGold, boolean visible, boolean pinned, boolean repeatable) throws SQLException {
        if (rewardGold < 0) throw new Failure(ApiCode.SURVEY_INVALID);
        return transact(con -> {
            long id = internalId(con, publicId, true);
            if (find(con, id, true).revision != revision) throw new Failure(ApiCode.SURVEY_CHANGED);
            try (var ps = con.prepareStatement("UPDATE surveys SET reward_gold=?,visible=?,pinned=?,repeatable=?,revision=revision+1,updated_at=? WHERE id=?")) {
                ps.setInt(1, rewardGold); ps.setBoolean(2, visible); ps.setBoolean(3, pinned); ps.setBoolean(4, repeatable); ps.setLong(5, System.currentTimeMillis()); ps.setLong(6, id);
                ps.executeUpdate();
            }
            return find(con, id, false);
        });
    }

    public void delete(String publicId) throws SQLException {
        transact(con -> {
            long id = internalId(con, publicId, true);
            try (var ps = con.prepareStatement("DELETE FROM survey_responses WHERE survey_id=?")) {
                ps.setLong(1, id); ps.executeUpdate();
            }
            try (var ps = con.prepareStatement("DELETE FROM surveys WHERE id=?")) { ps.setLong(1, id); ps.executeUpdate(); }
            return null;
        });
    }

    /**
     * 提交答卷并发放首次填写奖励，普通问卷按账号去重，重复填写问卷按请求标识去重
     *
     * @param publicId 问卷短编号
     * @param accountId 统一账号 UUID
     * @param userOpenId 当前机器人用户标识
     * @param username 提交时的用户名称
     * @param revision 填写时的问卷版本
     * @param answers 题目标识及答案，文本题使用单元素列表
     * @return 本次或此前已提交的答卷
     * @throws SQLException 数据库操作失败
     */
    public Answer submit(String publicId, String accountId, String userOpenId, String username, int revision,
                         Map<String, List<String>> answers) throws SQLException {
        return submit(publicId, accountId, userOpenId, username, revision, answers, null);
    }

    public Answer submit(String publicId, String accountId, String userOpenId, String username, int revision,
                         Map<String, List<String>> answers, String requestId) throws SQLException {
        if (requestId != null && !requestId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw new Failure(ApiCode.SURVEY_INVALID);
        try (var attempt = SUBMISSION_LIMITER.attempt(publicId, accountId)) {
            var result = transact(con -> {
                long id = internalId(con, publicId, true);
                var survey = find(con, id, true);
                if (survey.status == Status.DRAFT) throw new Failure(ApiCode.SURVEY_NOT_FOUND);
                if (requestId != null) {
                    try (var ps = con.prepareStatement("SELECT * FROM survey_responses WHERE survey_id=? AND account_id=? AND request_id=?")) {
                        ps.setLong(1, id); ps.setString(2, accountId); ps.setString(3, requestId);
                        try (var rows = ps.executeQuery()) { if (rows.next()) return readAnswer(rows); }
                    }
                }
                var existing = answer(con, id, accountId);
                if (existing != null && !survey.repeatable) return existing;
                if (survey.repeatable && requestId == null) throw new Failure(ApiCode.SURVEY_INVALID);
                int reward = existing == null ? survey.rewardGold : 0;
                if (survey.status != Status.PUBLISHED) throw new Failure(ApiCode.SURVEY_CLOSED);
                if (survey.revision != revision) throw new Failure(ApiCode.SURVEY_CHANGED);
                var checked = new SurveyDefinition(survey.title, survey.description, survey.questions).validateAnswers(answers);
                if (survey.repeatable) attempt.reserve();
                try (var ps = con.prepareStatement("INSERT INTO survey_responses"
                        + " (survey_id,account_id,user_open_id,username,answers,submitted_at,reward_gold,request_id) VALUES (?,?,?,?,?,?,?,?)")) {
                    ps.setLong(1, id); ps.setString(2, accountId); ps.setString(3, userOpenId);
                    ps.setString(4, username == null ? "" : username.substring(0, Math.min(username.length(), 255)));
                    ps.setString(5, encode(checked)); ps.setLong(6, System.currentTimeMillis()); ps.setInt(7, reward); ps.setString(8, requestId); ps.executeUpdate();
                }
                if (reward > 0) {
                    LootRepository.addCoins(con, userOpenId, reward);
                    CoinGainLogRepository.recordCoinGain(con, userOpenId, "survey", reward);
                }
                try (var ps = con.prepareStatement("UPDATE surveys SET response_count=response_count+1 WHERE id=?")) {
                    ps.setLong(1, id); ps.executeUpdate();
                }
                return answer(con, id, accountId);
            });
            attempt.commit();
            return result;
        }
    }

    public ResponsePage responses(String publicId, int page, long snapshot) throws SQLException {
        return responses(publicId, page, snapshot, false);
    }

    public ResponsePage responses(String publicId, int page, long snapshot, boolean starredOnly) throws SQLException {
        if (page < 1 || snapshot < 0) throw new Failure(ApiCode.SURVEY_INVALID);
        try (var con = connections.open()) {
            long id = internalId(con, publicId, false);
            find(con, id, false);
            long boundary = responseSnapshot(con, id, snapshot);
            long total = responseCount(con, id, boundary, starredOnly);
            page = (int) Math.min(page, Math.max(1, (total + 19) / 20));
            List<ResponseSummary> result = new ArrayList<>();
            try (var ps = con.prepareStatement("SELECT id,user_open_id,username,submitted_at,starred FROM survey_responses"
                    + " WHERE survey_id=? AND id<=?" + (starredOnly ? " AND starred=TRUE" : "") + " ORDER BY id DESC LIMIT 20 OFFSET ?")) {
                ps.setLong(1, id); ps.setLong(2, boundary); ps.setLong(3, (page - 1L) * 20);
                try (var rows = ps.executeQuery()) {
                    while (rows.next()) result.add(new ResponseSummary(rows.getLong(1), rows.getString(2),
                            rows.getString(3), rows.getLong(4), rows.getBoolean(5)));
                }
            }
            return new ResponsePage(result, total, boundary, page, 20);
        }
    }

    public ResponseDetail responseAt(String publicId, long position, long snapshot) throws SQLException {
        return responseAt(publicId, position, snapshot, false);
    }

    public ResponseDetail responseAt(String publicId, long position, long snapshot, boolean starredOnly) throws SQLException {
        if (position < 1 || snapshot < 0) throw new Failure(ApiCode.SURVEY_INVALID);
        try (var con = connections.open()) {
            long id = internalId(con, publicId, false);
            find(con, id, false);
            long boundary = responseSnapshot(con, id, snapshot);
            long total = responseCount(con, id, boundary, starredOnly);
            if (position > total) throw new Failure(ApiCode.SURVEY_RESPONSE_NOT_FOUND);
            try (var ps = con.prepareStatement("SELECT * FROM survey_responses WHERE survey_id=? AND id<=?"
                    + (starredOnly ? " AND starred=TRUE" : "") + " ORDER BY id DESC LIMIT 1 OFFSET ?")) {
                ps.setLong(1, id); ps.setLong(2, boundary); ps.setLong(3, position - 1);
                try (var rows = ps.executeQuery()) {
                    if (!rows.next()) throw new Failure(ApiCode.SURVEY_RESPONSE_NOT_FOUND);
                    return new ResponseDetail(readAnswer(rows), rows.getBoolean("starred"), position, total, boundary);
                }
            }
        }
    }

    public void starResponse(String publicId, long responseId, boolean starred) throws SQLException {
        transact(con -> {
            long id = internalId(con, publicId, true);
            try (var ps = con.prepareStatement("SELECT id FROM survey_responses WHERE survey_id=? AND id=? FOR UPDATE")) {
                ps.setLong(1, id); ps.setLong(2, responseId);
                try (var rows = ps.executeQuery()) {
                    if (!rows.next()) throw new Failure(ApiCode.SURVEY_RESPONSE_NOT_FOUND);
                }
            }
            try (var ps = con.prepareStatement("UPDATE survey_responses SET starred=? WHERE survey_id=? AND id=?")) {
                ps.setBoolean(1, starred); ps.setLong(2, id); ps.setLong(3, responseId); ps.executeUpdate();
            }
            return null;
        });
    }

    private long responseSnapshot(Connection con, long id, long snapshot) throws SQLException {
        if (snapshot > 0) return snapshot;
        try (var ps = con.prepareStatement("SELECT COALESCE(MAX(id),0) FROM survey_responses WHERE survey_id=?")) {
            ps.setLong(1, id);
            try (var rows = ps.executeQuery()) { rows.next(); return rows.getLong(1); }
        }
    }

    private long responseCount(Connection con, long id, long snapshot, boolean starredOnly) throws SQLException {
        try (var ps = con.prepareStatement("SELECT COUNT(*) FROM survey_responses WHERE survey_id=? AND id<=?" + (starredOnly ? " AND starred=TRUE" : ""))) {
            ps.setLong(1, id); ps.setLong(2, snapshot);
            try (var rows = ps.executeQuery()) { rows.next(); return rows.getLong(1); }
        }
    }

    private long internalId(Connection con, String publicId, boolean lock) throws SQLException {
        if (!SurveyId.isValid(publicId)) throw new Failure(ApiCode.SURVEY_NOT_FOUND);
        try (var ps = con.prepareStatement("SELECT id FROM surveys WHERE public_id=?" + (lock ? " FOR UPDATE" : ""))) {
            ps.setString(1, publicId);
            try (var rows = ps.executeQuery()) {
                if (!rows.next()) throw new Failure(ApiCode.SURVEY_NOT_FOUND);
                return rows.getLong(1);
            }
        }
    }

    private Survey find(Connection con, long id, boolean lock) throws SQLException {
        try (var ps = con.prepareStatement("SELECT * FROM surveys WHERE id=?" + (lock ? " FOR UPDATE" : ""))) {
            ps.setLong(1, id);
            try (var row = ps.executeQuery()) {
                if (!row.next()) throw new Failure(ApiCode.SURVEY_NOT_FOUND);
                return new Survey(row.getString("public_id"), row.getString("title"), row.getString("description"),
                        decode(row.getString("questions"), new TypeReference<List<Question>>() {}),
                        Status.valueOf(row.getString("status")), row.getInt("revision"), row.getLong("response_count"),
                        row.getLong("created_at"), row.getLong("updated_at"), row.getInt("reward_gold"), row.getBoolean("visible"), row.getBoolean("pinned"), row.getBoolean("repeatable"));
            }
        }
    }

    private Answer answer(Connection con, long id, String accountId) throws SQLException {
        try (var ps = con.prepareStatement("SELECT * FROM survey_responses WHERE survey_id=? AND account_id=? ORDER BY id DESC LIMIT 1")) {
            ps.setLong(1, id); ps.setString(2, accountId);
            try (var row = ps.executeQuery()) { return row.next() ? readAnswer(row) : null; }
        }
    }

    private Answer readAnswer(ResultSet row) throws SQLException {
        return new Answer(row.getLong("id"), row.getString("account_id"), row.getString("user_open_id"),
                row.getString("username"), decode(row.getString("answers"), new TypeReference<Map<String, List<String>>>() {}),
                row.getLong("submitted_at"), row.getInt("reward_gold"));
    }

    private static String encode(Object value) throws SQLException {
        try { return JSON.writeValueAsString(value); }
        catch (java.io.IOException e) { throw new SQLException("问卷数据序列化失败", e); }
    }

    private static <T> T decode(String value, TypeReference<T> type) throws SQLException {
        try { return JSON.readValue(value, type); }
        catch (java.io.IOException e) { throw new SQLException("问卷数据读取失败", e); }
    }

    @FunctionalInterface
    private interface Transaction<T> { T run(Connection con) throws SQLException; }

    private <T> T transact(Transaction<T> operation) throws SQLException {
        try (var con = connections.open()) {
            con.setAutoCommit(false);
            try {
                T result = operation.run(con);
                con.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }
}
