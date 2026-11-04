package top.yzljc.atribot.database.repo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.yzljc.atribot.function.games.question.QuestionBank;
import top.yzljc.atribot.function.games.question.QuestionBank.Question;

import java.sql.DriverManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class DailyQuestionRepositoryTest {
    private UserGameDataRepository.Connections connections;
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-03T15:59:00Z"), ZoneOffset.UTC);
    private final List<Question> bank = List.of(
            new Question("one", "日常", "窗边摆什么？", "绿植", "摆件"),
            new Question("two", "日常", "书签选什么形状？", "树叶", "羽毛"));
    private DailyQuestionRepository repository;

    @BeforeEach
    void setup() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000";
        connections = () -> DriverManager.getConnection(url);
        repository = new DailyQuestionRepository(connections, clock, bank);
        repository.init();
    }

    @Test
    void answersAreImmutableAndResultsRequireParticipation() throws Exception {
        var initial = repository.visit("alice", null, null);
        assertNull(initial.answer());
        var submitted = repository.visit("alice", "A", initial.day().key());
        assertEquals(DailyQuestionRepository.Result.SUBMITTED, submitted.result());
        assertEquals(1, submitted.countA());
        var duplicate = repository.visit("alice", "B", null);
        assertEquals(DailyQuestionRepository.Result.ALREADY_ANSWERED, duplicate.result());
        assertEquals("A", duplicate.answer());
        assertEquals(0, duplicate.countB());
        var unseen = repository.visit("bob", null, null);
        assertNull(unseen.answer());
        assertEquals(0, unseen.countA());
        var bob = repository.visit("bob", "B", null);
        assertEquals(1, bob.countA());
        assertEquals(1, bob.countB());
    }

    @Test
    void restartKeepsSnapshotAndNewDayRejectsOldButtons() throws Exception {
        var old = repository.visit("alice", "A", null);
        var restarted = new DailyQuestionRepository(connections, clock,
                List.of(new Question("changed", "日常", "新题目", "左边", "右边")));
        var restored = restarted.visit("alice", null, null);
        assertEquals(old.day(), restored.day());
        assertEquals("A", restored.answer());
        var tomorrow = new DailyQuestionRepository(connections,
                Clock.fixed(Instant.parse("2026-11-03T16:00:01Z"), ZoneOffset.UTC), bank);
        var expired = tomorrow.visit("alice", "B", old.day().key());
        assertEquals(DailyQuestionRepository.Result.EXPIRED, expired.result());
        assertEquals(old.day().date().plusDays(1), expired.day().date());
        assertNotEquals(old.day().question().id(), expired.day().question().id());
        assertNull(tomorrow.visit("alice", null, null).answer());
        assertEquals(DailyQuestionRepository.Result.SUBMITTED, tomorrow.visit("alice", "B", null).result());
    }

    @Test
    void concurrentSubmissionsAcrossInstancesCountOnlyOnce() throws Exception {
        var other = new DailyQuestionRepository(connections, clock, bank);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<DailyQuestionRepository.View>> futures = new ArrayList<>();
            for (int i = 0; i < 24; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    start.await();
                    return (index % 2 == 0 ? repository : other).visit("same-user", index % 2 == 0 ? "A" : "B", null);
                }));
            }
            start.countDown();
            int submitted = 0;
            var day = (DailyQuestionRepository.Day) null;
            for (var future : futures) {
                var view = future.get(15, TimeUnit.SECONDS);
                if (view.result() == DailyQuestionRepository.Result.SUBMITTED) submitted++;
                if (day == null) day = view.day();
                assertEquals(day, view.day());
            }
            assertEquals(1, submitted);
        }
        var view = repository.visit("same-user", null, null);
        assertEquals(1, view.countA() + view.countB());
    }

    @Test
    void packagedBankContainsAllQuestions() throws Exception {
        var questions = QuestionBank.load();
        assertEquals(1000, questions.size());
        assertEquals(1000, questions.stream().map(Question::id).distinct().count());
    }
}
