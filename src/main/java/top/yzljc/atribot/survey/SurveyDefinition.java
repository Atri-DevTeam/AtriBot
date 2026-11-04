package top.yzljc.atribot.survey;

import top.yzljc.atribot.webui.ApiCode;

import java.util.*;

/**
 * @Author YZ_Ljc_
 * @ClassName SurveyDefinition
 * @Created_at 2026/10/30
 * @Project AtriMeow
 * @Package top.yzljc.atribot.survey
 */
public record SurveyDefinition(String title, String description, List<Question> questions) {
    public enum Type { SINGLE, MULTIPLE, TEXT }
    public enum Status { DRAFT, PUBLISHED, CLOSED }
    public record Option(String id, String label) {}
    public record Question(String id, Type type, String title, boolean required, List<Option> options) {}

    public static final class Failure extends RuntimeException {
        public final ApiCode code;

        public Failure(ApiCode code) {
            super(code.getMessage());
            this.code = code;
        }
    }

    public SurveyDefinition validated() {
        if (questions == null || questions.isEmpty()) invalid();
        Set<String> ids = new HashSet<>();
        List<Question> normalized = new ArrayList<>();
        for (var q : questions) {
            if (q == null || q.type == null || !identifier(q.id) || !ids.add(q.id)) invalid();
            List<Option> options = q.options == null ? List.of() : q.options;
            if (q.type == Type.TEXT && !options.isEmpty()
                    || q.type != Type.TEXT && (options.size() < 2 || options.size() > 20)) invalid();
            Set<String> optionIds = new HashSet<>();
            List<Option> normalizedOptions = new ArrayList<>();
            for (var option : options) {
                if (option == null || !identifier(option.id) || !optionIds.add(option.id)) invalid();
                normalizedOptions.add(new Option(option.id, text(option.label, 200, true)));
            }
            normalized.add(new Question(q.id, q.type, text(q.title, 300, true), q.required, List.copyOf(normalizedOptions)));
        }
        return new SurveyDefinition(text(title, 120, true), text(description, 3000, false), List.copyOf(normalized));
    }

    public Map<String, List<String>> validateAnswers(Map<String, List<String>> answers) {
        if (answers == null || answers.size() > questions.size()) invalid();
        Set<String> ids = new HashSet<>();
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (var question : questions) {
            ids.add(question.id);
            List<String> values = answers.getOrDefault(question.id, List.of());
            if (values == null || values.stream().anyMatch(Objects::isNull)) invalid();
            if (question.type == Type.TEXT) {
                if (values.size() > 1) invalid();
                String value = values.isEmpty() ? "" : text(values.getFirst(), 2000, false);
                values = value.isEmpty() ? List.of() : List.of(value);
            } else {
                if (question.type == Type.SINGLE && values.size() > 1 || new HashSet<>(values).size() != values.size()) invalid();
                Set<String> allowed = new HashSet<>();
                question.options.forEach(option -> allowed.add(option.id));
                if (!allowed.containsAll(values)) invalid();
            }
            if (question.required && values.isEmpty()) invalid();
            result.put(question.id, List.copyOf(values));
        }
        if (!ids.containsAll(answers.keySet())) invalid();
        return result;
    }

    /**
     * 已发布问卷仅允许修订文字，题目、选项的标识和顺序及填写规则保持不变
     *
     * @param original 已保存的题目结构
     */
    public void requireSameStructure(List<Question> original) {
        if (questions.size() != original.size()) throw new Failure(ApiCode.SURVEY_LOCKED);
        for (int i = 0; i < questions.size(); i++) {
            var current = questions.get(i);
            var previous = original.get(i);
            if (!current.id.equals(previous.id) || current.type != previous.type || current.required != previous.required
                    || current.options.size() != previous.options.size()) throw new Failure(ApiCode.SURVEY_LOCKED);
            for (int j = 0; j < current.options.size(); j++) {
                if (!current.options.get(j).id.equals(previous.options.get(j).id)) throw new Failure(ApiCode.SURVEY_LOCKED);
            }
        }
    }

    private static boolean identifier(String id) {
        return id != null && id.matches("[A-Za-z0-9_-]{1,40}");
    }

    private static String text(String value, int max, boolean required) {
        String result = value == null ? "" : value.trim();
        if (result.length() > max || required && result.isEmpty()) invalid();
        return result;
    }

    private static void invalid() {
        throw new Failure(ApiCode.SURVEY_INVALID);
    }
}
