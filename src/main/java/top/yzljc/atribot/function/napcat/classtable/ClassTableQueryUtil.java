package top.yzljc.atribot.function.napcat.classtable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ClassTableQueryUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(ClassTableQueryUtil.class);

    @Getter
    @Setter
    public static class ClassSession {
        @JsonProperty("class_data")
        private ClassData classData;

        @JsonProperty("time_end_span")
        private int timeEndSpan;

        @JsonProperty("with_in_week")
        private List<Integer> withInWeek;
    }

    @Getter
    @Setter
    public static class ClassData {
        private String campus;
        @JsonProperty("class_end_time") private int classEndTime;
        @JsonProperty("class_hour") private int classHour;
        @JsonProperty("class_id") private String classId;
        @JsonProperty("class_name_show") private String classNameShow;
        @JsonProperty("class_start_time") private int classStartTime;
        private double credit;
        @JsonProperty("full_class_name") private String fullClassName;
        @JsonProperty("full_location") private String fullLocation;
        private int id;
        private String location;
        @JsonProperty("major_name") private String majorName;
        @JsonProperty("odd_even_week") private int oddEvenWeek;
        private String semester;
        private String teacher;
        @JsonProperty("theoretical_or_practice") private String theoreticalOrPractice;
        @JsonProperty("unified_exam") private int unifiedExam;
        @JsonProperty("update_time") private long updateTime;
        private int weekday;
    }

    /**
     * @param rootNode       接口返回的总JsonNode
     * @param majorKey       专业Key (如: "2025-2026-2_软件2501")
     * @param weekday        星期几 (如: "1")
     * @param currentSession 当前节次 (如: 1)
     * @return 包含该节次的所有课程封装对象List (可能有多节，也可能为空)
     */
    public static List<ClassSession> getClasses(JsonNode rootNode, String majorKey, String weekday, int currentSession) {
        List<ClassSession> result = new ArrayList<>();
        if (rootNode == null || rootNode.isMissingNode() || currentSession <= 0) {
            return result;
        }

        String resolvedMajorKey = resolveMajorKey(rootNode, majorKey);

        JsonNode majorNode = rootNode.path(resolvedMajorKey);
        JsonNode dayNode = majorNode.path(weekday);
        if (dayNode.isMissingNode() || !dayNode.isObject()) {
            return result;
        }

        Iterator<Map.Entry<String, JsonNode>> fields = dayNode.fields();
        while (fields.hasNext()) {
            JsonNode classesArray = fields.next().getValue();
            if (classesArray.isArray()) {
                for (JsonNode classNode : classesArray) {
                    try {
                        ClassSession sessionObj = MAPPER.treeToValue(classNode, ClassSession.class);
                        ClassData data = sessionObj.getClassData();
                        if (data == null) continue;
                        if (sessionObj.getWithInWeek() == null) {
                            sessionObj.setWithInWeek(Collections.emptyList());
                        }
                        int start = data.getClassStartTime();
                        int end = data.getClassEndTime();
                        if (start <= 0 || end <= 0 || start > end) continue;
                        // 筛选出包含当前节次的课
                        if (currentSession >= start && currentSession <= end) {
                            result.add(sessionObj);
                        }
                    } catch (Exception e) {
                        log.warn("解析单条课程数据失败: {}", e.getMessage());
                    }
                }
            }
        }
        return result;
    }

    private static String resolveMajorKey(JsonNode rootNode, String majorKey) {
        if (rootNode.has(majorKey)) {
            return majorKey;
        }
        Iterator<String> keys = rootNode.fieldNames();
        while (keys.hasNext()) {
            String key = keys.next();
            if (majorKey.equals(decodeUtf8(key))) {
                return key;
            }
        }
        return majorKey;
    }

    private static String decodeUtf8(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return s;
        }
    }
}