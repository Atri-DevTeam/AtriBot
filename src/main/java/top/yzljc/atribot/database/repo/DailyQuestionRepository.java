package top.yzljc.atribot.database.repo;

import top.yzljc.atribot.database.DatabaseManager;
import top.yzljc.atribot.function.games.question.QuestionBank;
import top.yzljc.atribot.function.games.question.QuestionBank.Question;

import java.io.IOException;
import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** 每日题目快照及不可修改的用户答案，群聊与私聊共用作答记录 */
public final class DailyQuestionRepository {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private final UserGameDataRepository.Connections connections;
    private final Clock clock;
    private final List<Question> bank;
    private volatile Day cachedDay;

    public record Day(LocalDate date, Question question) {
        public String key() { return date + ":" + question.id(); }
    }

    public enum Result { VIEW, SUBMITTED, ALREADY_ANSWERED, EXPIRED }

    /** 未作答或按钮过期时不返回票数 */
    public record View(Day day, String answer, long countA, long countB, Result result) {}

    public DailyQuestionRepository() throws IOException {
        this(DatabaseManager::getConnection, Clock.system(ZONE), QuestionBank.load());
    }

    public DailyQuestionRepository(UserGameDataRepository.Connections connections, Clock clock, List<Question> bank) {
        if (bank.isEmpty()) throw new IllegalArgumentException("题库不能为空");
        this.connections = connections;
        this.clock = clock.withZone(ZONE);
        this.bank = List.copyOf(bank);
    }

    public void init() throws SQLException {
        try (var con = connections.open(); var statement = con.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS daily_question (
                      question_date DATE NOT NULL PRIMARY KEY,
                      question_id VARCHAR(64) NOT NULL,
                      category VARCHAR(128) NOT NULL,
                      question_text TEXT NOT NULL,
                      option_a TEXT NOT NULL,
                      option_b TEXT NOT NULL,
                      KEY idx_daily_question_id (question_id)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS daily_question_answer (
                      question_date DATE NOT NULL,
                      user_id VARCHAR(255) NOT NULL,
                      answer CHAR(1) NOT NULL,
                      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      PRIMARY KEY (question_date, user_id),
                      KEY idx_daily_question_count (question_date, answer)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
        }
    }

    /**
     * 查看或提交当天答案，以日期与用户的唯一键保证并发提交只生效一次
     *
     * @param userId 官机用户 OpenID
     * @param answer A、B 或 null，null 表示仅查看
     * @param expectedKey 按钮携带的题目标识，手动输入指令时为 null
     */
    public View visit(String userId, String answer, String expectedKey) throws SQLException {
        if (userId == null || userId.isBlank() || userId.length() > 255) throw new IllegalArgumentException("无效用户标识");
        if (answer != null && !answer.equals("A") && !answer.equals("B")) throw new IllegalArgumentException("无效答案");
        try (var con = connections.open()) {
            Day day = today(con, LocalDate.now(clock));
            if (expectedKey != null && !expectedKey.equals(day.key())) return new View(day, null, 0, 0, Result.EXPIRED);
            Result result = Result.VIEW;
            if (answer != null) {
                try (var ps = con.prepareStatement("INSERT INTO daily_question_answer (question_date, user_id, answer) VALUES (?, ?, ?)")) {
                    ps.setDate(1, Date.valueOf(day.date()));
                    ps.setString(2, userId);
                    ps.setString(3, answer);
                    ps.executeUpdate();
                    result = Result.SUBMITTED;
                } catch (SQLException failure) {
                    if (failure.getErrorCode() != 1062 && !"23505".equals(failure.getSQLState())) throw failure;
                    result = Result.ALREADY_ANSWERED;
                }
            }
            String selected = null;
            try (var ps = con.prepareStatement("SELECT answer FROM daily_question_answer WHERE question_date = ? AND user_id = ?")) {
                ps.setDate(1, Date.valueOf(day.date()));
                ps.setString(2, userId);
                try (var rows = ps.executeQuery()) {
                    if (rows.next()) selected = rows.getString(1);
                }
            }
            long a = 0, b = 0;
            if (selected != null) {
                try (var ps = con.prepareStatement("SELECT answer, COUNT(*) FROM daily_question_answer WHERE question_date = ? GROUP BY answer")) {
                    ps.setDate(1, Date.valueOf(day.date()));
                    try (var rows = ps.executeQuery()) {
                        while (rows.next()) {
                            if ("A".equals(rows.getString(1))) a = rows.getLong(2);
                            else if ("B".equals(rows.getString(1))) b = rows.getLong(2);
                        }
                    }
                }
            }
            return new View(day, selected, a, b, result);
        }
    }

    private synchronized Day today(Connection con, LocalDate date) throws SQLException {
        if (cachedDay != null && cachedDay.date().equals(date)) return cachedDay;
        Day day = readDay(con, date);
        if (day == null) {
            var used = new HashMap<String, LocalDate>();
            try (var ps = con.prepareStatement("SELECT question_id, MAX(question_date) FROM daily_question GROUP BY question_id");
                 var rows = ps.executeQuery()) {
                while (rows.next()) used.put(rows.getString(1), rows.getDate(2).toLocalDate());
            }
            // 优先使用未出现的题目，题库用完后从最久未出现的题目开始轮换
            var earliest = bank.stream().map(q -> used.getOrDefault(q.id(), LocalDate.MIN)).min(LocalDate::compareTo).orElseThrow();
            var candidates = bank.stream().filter(q -> used.getOrDefault(q.id(), LocalDate.MIN).equals(earliest)).toList();
            var q = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
            try (var ps = con.prepareStatement("""
                    INSERT INTO daily_question (question_date, question_id, category, question_text, option_a, option_b)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE question_date = question_date
                    """)) {
                ps.setDate(1, Date.valueOf(date));
                ps.setString(2, q.id());
                ps.setString(3, q.category());
                ps.setString(4, q.text());
                ps.setString(5, q.optionA());
                ps.setString(6, q.optionB());
                ps.executeUpdate();
            }
            day = readDay(con, date);
        }
        if (day == null) throw new SQLException("每日题目保存失败");
        cachedDay = day;
        return day;
    }

    private Day readDay(Connection con, LocalDate date) throws SQLException {
        try (var ps = con.prepareStatement("SELECT question_id, category, question_text, option_a, option_b FROM daily_question WHERE question_date = ?")) {
            ps.setDate(1, Date.valueOf(date));
            try (var rows = ps.executeQuery()) {
                return rows.next() ? new Day(date, new Question(rows.getString(1), rows.getString(2), rows.getString(3),
                        rows.getString(4), rows.getString(5))) : null;
            }
        }
    }
}
