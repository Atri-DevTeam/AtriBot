package top.yzljc.atribot.function.games.question;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName QuestionBank
 * @Created_at 2026/11/03
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.games
 */
public final class QuestionBank {
    public record Question(String id, String category, String text, String optionA, String optionB) {
    }

    private QuestionBank() {
    }

    public static List<Question> load() throws IOException {
        try (var input = QuestionBank.class.getResourceAsStream("/games/questions.json")) {
            if (input == null) throw new IOException("缺少题库 games/questions.json");
            var root = new ObjectMapper().readTree(input);
            if (root == null || !root.isArray() || root.isEmpty()) throw new IOException("每日问题题库为空或格式无效");
            var questions = new ArrayList<Question>();
            var ids = new HashSet<String>();
            for (var node : root) {
                var q = new Question(node.path("id").asText(""), node.path("category").asText(""),
                        node.path("question").asText(""), node.path("options").path("A").asText(""),
                        node.path("options").path("B").asText(""));
                if (q.id().isBlank() || q.id().length() > 64 || !ids.add(q.id()) || q.category().isBlank()
                        || q.text().isBlank() || q.optionA().isBlank() || q.optionB().isBlank()
                        || q.optionA().equals(q.optionB())) throw new IOException("每日问题条目无效: " + q.id());
                questions.add(q);
            }
            return List.copyOf(questions);
        }
    }
}
