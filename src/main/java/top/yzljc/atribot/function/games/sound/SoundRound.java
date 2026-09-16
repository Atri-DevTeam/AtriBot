package top.yzljc.atribot.function.games.sound;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.net.URI;

/**
 * @Author YZ_Ljc_
 * @ClassName SoundRound
 * @Created_at 2026/09/15
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.games.sound
 */
public final class SoundRound {
    public static final long MAX_DURATION_MS = 120_000;
    public enum Vote { WRONG, CORRECT, EXPIRED }
    public record Result(Map<String, Integer> answers, String winner) {}
    public record Answer(Vote status, Result result) {}
    public final String id = UUID.randomUUID().toString().replace("-", "");
    public final SoundCatalog.Question question;
    public final String audioUrl;
    private final Map<String, Integer> answers = new HashMap<>();
    private long deadline;
    private boolean closed;

    public SoundRound(SoundCatalog.Question question, String audioUrl) {
        URI uri = URI.create(audioUrl);
        if (uri.getPath() == null || !uri.getPath().endsWith("/" + question.audio().path()))
            throw new IllegalArgumentException("Sound round URL does not match the question audio");
        this.question = question;
        this.audioUrl = audioUrl;
    }

    public synchronized void start(long now, long duration) {
        if (duration <= 0) throw new IllegalArgumentException("Round duration must be positive");
        if (!closed && deadline == 0) deadline = now + Math.min(duration, MAX_DURATION_MS);
    }

    public synchronized Answer answer(String token, String user, int answer, long now) {
        if (closed || deadline == 0 || now >= deadline || !id.equals(token) || answer < 0 || answer > 3)
            return new Answer(Vote.EXPIRED, null);
        answers.put(user, answer);
        closed = true;
        if (answer == question.answer()) {
            return new Answer(Vote.CORRECT, new Result(Map.copyOf(answers), user));
        }
        return new Answer(Vote.WRONG, new Result(Map.copyOf(answers), null));
    }

    public synchronized Result expire(long now) {
        if (closed || deadline == 0 || now < deadline) return null;
        closed = true;
        return new Result(Map.copyOf(answers), null);
    }

    public synchronized void cancel() { closed = true; }
}
