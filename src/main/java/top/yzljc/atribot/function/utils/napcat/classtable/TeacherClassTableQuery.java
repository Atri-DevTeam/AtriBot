package top.yzljc.atribot.function.utils.napcat.classtable;

import com.fasterxml.jackson.databind.JsonNode;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * 按教师姓名查询全校课表，换算上课日期并合并重复的合班记录。
 *
 * @Author YZ_Ljc_
 * @ClassName TeacherClassTableQuery
 * @Created_at 2026/09/19
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.utils.napcat.classtable
 */
public final class TeacherClassTableQuery {
    private static final int MAX_TEXT_LENGTH = 1800;
    private static final int MAX_CANDIDATES = 6;
    private static final ZoneId SCHOOL_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter UPDATED_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Pattern TEACHER_SEPARATOR = Pattern.compile("[、,，;；/／&＆|｜+＋\\r\\n\\t]+");
    private static final Pattern CHINESE_NAMES = Pattern.compile("[\\p{IsHan}·•]{2,8}(?:\\s+[\\p{IsHan}·•]{2,8})+");
    private static final String[] WEEKDAYS = {"", "周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    private TeacherClassTableQuery() {
    }

    public static List<String> query(TeacherClassTableService.Snapshot snapshot, String teacherName,
                               LocalDate from, LocalDate to) {
        String name = normalizeName(teacherName);
        if (name.isBlank()) return List.of("请填写教师姓名，例如：/教师课表 张三");
        if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > 6) {
            return List.of("请按教学周查询课表。");
        }
        if (snapshot == null) return List.of("课表暂不可用，请稍后重试。");

        JsonNode tables = snapshot.tables();
        Scan scan = new Scan();
        if (tables != null && tables.isObject()) {
            tables.elements().forEachRemaining(table -> {
                if (table.isObject()) scan.coveredClasses++;
            });
        }
        long week = ChronoUnit.WEEKS.between(ProcessClassTable.SEMESTER_START.with(DayOfWeek.MONDAY), from) + 1;
        String header = "**教师课表 · 第" + week + "周**\n\n教师：" + markdown(shorten(name, 48))
                + "\n\n学期：" + markdown(shorten(clean(snapshot.semester()), 36))
                + "\n\n日期：" + from + " 至 " + to + "\n\n";
        if (tables == null || !tables.isObject() || scan.coveredClasses == 0) {
            return List.of(header + "当前没有可查询的班级课表，请稍后重试。" + footer(snapshot, scan));
        }
        if (!ProcessClassTable.SEMESTER.equals(snapshot.semester())) {
            return List.of(header + "该学期暂不支持查询，请更新课表后重试。" + footer(snapshot, scan));
        }
        if (to.isBefore(ProcessClassTable.SEMESTER_START) || from.isAfter(ProcessClassTable.SEMESTER_END)) {
            return List.of(header + "查询周次不在当前学期范围（" + ProcessClassTable.SEMESTER_START
                    + " 至 " + ProcessClassTable.SEMESTER_END + "）。" + footer(snapshot, scan));
        }

        String target = nameKey(name);
        for (var classes = tables.fields(); classes.hasNext();) {
            var classEntry = classes.next();
            JsonNode timetable = classEntry.getValue();
            if (!timetable.isObject()) {
                scan.unrecognized++;
                continue;
            }
            String className = classEntry.getKey().startsWith(snapshot.semester() + "_")
                    ? classEntry.getKey().substring(snapshot.semester().length() + 1) : "";
            for (JsonNode day : timetable) {
                if (!day.isObject()) {
                    scan.unrecognized++;
                    continue;
                }
                for (JsonNode period : day) {
                    if (!period.isArray()) {
                        scan.unrecognized++;
                        continue;
                    }
                    for (JsonNode entry : period) {
                        collect(entry, className, target, snapshot.semester(), from, to, scan);
                    }
                }
            }
        }

        String footer = footer(snapshot, scan);
        if (!scan.exactNameFound) {
            StringBuilder message = new StringBuilder(header)
                    .append("当前课表中未找到姓名完全匹配的教师。");
            if (!scan.candidates.isEmpty()) {
                message.append("\n\n可能的姓名：");
                message.append(String.join("、", scan.candidates.stream().map(candidate -> markdown(shorten(candidate, 36))).toList()));
                message.append("\n\n请使用教师全名重新查询。");
            }
            return List.of(message.append(footer).toString());
        }
        if (scan.lessons.isEmpty()) {
            return List.of(header + "当前已收录课表在这一周未查到课程。" + footer);
        }

        List<Lesson> lessons = new ArrayList<>(scan.lessons.values());
        lessons.sort(Comparator.comparing((Lesson lesson) -> lesson.key.date())
                .thenComparingInt(lesson -> lesson.key.start())
                .thenComparingInt(lesson -> lesson.key.end())
                .thenComparing(lesson -> lesson.key.course())
                .thenComparing(lesson -> lesson.key.campus())
                .thenComparing(lesson -> lesson.key.location()));
        String pageHeader = header + "本周共 " + lessons.size() + " 堂课程（合班已合并）\n";
        List<String> pages = new ArrayList<>();
        StringBuilder message = new StringBuilder(pageHeader);
        for (Lesson lesson : lessons) {
            String block = formatLesson(lesson);
            // 长课表按课程块分页，每页仍属于同一教学周，不截掉其余课程。
            if (message.length() > pageHeader.length()
                    && message.length() + block.length() + footer.length() + 60 > MAX_TEXT_LENGTH) {
                pages.add(message.toString());
                message = new StringBuilder(pageHeader);
            }
            message.append(block);
        }
        pages.add(message.toString());
        for (int index = 0; index < pages.size(); index++) {
            String pagination = pages.size() > 1 ? "\n\n本周课程 · 第 " + (index + 1) + "/" + pages.size() + " 页" : "";
            pages.set(index, pages.get(index) + pagination + footer);
        }
        return pages;
    }

    private static void collect(JsonNode entry, String tableClass, String target, String semester,
                                LocalDate from, LocalDate to, Scan scan) {
        JsonNode data = entry.path("class_data");
        if (!data.isObject()) {
            scan.unrecognized++;
            return;
        }
        JsonNode teacherValue = data.path("teacher");
        Set<String> teachers = teacherNames(teacherValue.isTextual() ? teacherValue.asText() : "");
        if (teachers.isEmpty()) {
            scan.unrecognized++;
            return;
        }
        boolean matches = false;
        for (String teacher : teachers) {
            String key = nameKey(teacher);
            if (key.equals(target)) matches = true;
            else if (key.contains(target)) {
                scan.candidates.add(teacher);
                if (scan.candidates.size() > MAX_CANDIDATES) scan.candidates.pollLast();
            }
        }
        if (!matches) return;
        scan.exactNameFound = true;

        int weekday = positiveInt(data.path("weekday"));
        int start = positiveInt(data.path("class_start_time"));
        int end = positiveInt(data.path("class_end_time"));
        String fullName = text(data, "full_class_name");
        String course = fullName.isBlank() ? text(data, "class_name_show") : fullName;
        String rowSemester = text(data, "semester");
        JsonNode weekValues = entry.path("with_in_week");
        if (weekday < 1 || weekday > 7 || start < 1 || end < start || end > 99 || course.isBlank()
                || !weekValues.isArray() || (!rowSemester.isBlank() && !semester.equals(rowSemester))) {
            scan.unrecognized++;
            return;
        }
        Set<Integer> weeks = new LinkedHashSet<>();
        for (JsonNode week : weekValues) {
            int number = positiveInt(week);
            if (number < 1) {
                scan.unrecognized++;
                return;
            }
            weeks.add(number);
        }

        String building = text(data, "location");
        String room = text(data, "full_location");
        String campus = text(data, "campus");
        String location = room.isBlank() ? building
                : building.isBlank() || room.startsWith(building) ? room : building + " " + room;
        if (!campus.isBlank() && !location.startsWith(campus)) location = campus + (location.isBlank() ? "" : " " + location);
        if (location.isBlank()) location = "地点待公布";
        String className = tableClass.isBlank() ? text(data, "major_name") : clean(tableClass);
        if (className.isBlank()) className = "班级待补充";
        LocalDate weekStart = ProcessClassTable.SEMESTER_START.with(DayOfWeek.MONDAY);
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (date.isBefore(ProcessClassTable.SEMESTER_START) || date.isAfter(ProcessClassTable.SEMESTER_END)
                    || date.getDayOfWeek().getValue() != weekday) continue;
            int week = (int) (ChronoUnit.DAYS.between(weekStart, date) / 7) + 1;
            if (!weeks.contains(week)) continue;
            LessonKey key = new LessonKey(target, date, start, end, course, location, campus);
            Lesson lesson = scan.lessons.get(key);
            if (lesson == null) {
                lesson = new Lesson(key, location);
                scan.lessons.put(key, lesson);
            }
            lesson.classes.add(className);
        }
    }

    private static Set<String> teacherNames(String value) {
        Set<String> result = new LinkedHashSet<>();
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC);
        for (String part : TEACHER_SEPARATOR.split(normalized)) {
            String name = part.strip();
            if (name.isBlank() || Set.of("无", "未知", "待定", "--").contains(name)) continue;
            // 中文姓名可以空格分隔；外文姓名中的单个空格仍属于完整姓名。
            if (CHINESE_NAMES.matcher(name).matches()) {
                for (String teacher : name.split("\\s+")) result.add(normalizeName(teacher));
            } else {
                result.add(normalizeName(name));
            }
        }
        return result;
    }

    private static String formatLesson(Lesson lesson) {
        LessonKey key = lesson.key;
        String periods = key.start() == key.end() ? Integer.toString(key.start()) : key.start() + "-" + key.end();
        return "\n**" + WEEKDAYS[key.date().getDayOfWeek().getValue()] + " · 第" + periods + "节**（" + key.date() + "）\n\n"
                + "课程：" + markdown(shorten(key.course(), 70)) + "\n\n"
                + "地点：" + markdown(shorten(lesson.location, 60)) + "\n\n"
                + "班级：" + markdown(classList(lesson.classes)) + "\n";
    }

    private static String classList(TreeSet<String> classes) {
        StringBuilder result = new StringBuilder();
        int shown = 0;
        for (String className : classes) {
            String label = shorten(className, 28);
            if (shown >= 6 || result.length() + label.length() + 1 > 90) break;
            if (shown > 0) result.append("、");
            result.append(label);
            shown++;
        }
        if (shown < classes.size()) result.append("等").append(classes.size()).append("个班级");
        return result.toString();
    }

    private static String footer(TeacherClassTableService.Snapshot snapshot, Scan scan) {
        String updated = snapshot.updatedAtMillis() > 0
                ? UPDATED_FORMAT.format(Instant.ofEpochMilli(snapshot.updatedAtMillis()).atZone(SCHOOL_ZONE)) : "时间未知";
        String coverage = snapshot.expectedClasses() > 0
                ? scan.coveredClasses + "/" + snapshot.expectedClasses() : Integer.toString(scan.coveredClasses);
        StringBuilder footer = new StringBuilder("\n\n---\n\n缓存更新：").append(updated)
                .append("\n\n覆盖班级：").append(coverage);
        if (snapshot.failedClasses() > 0) {
            footer.append("\n\n提醒：").append(snapshot.failedClasses())
                    .append("个班级未能更新，结果可能不完整或含旧课表。");
        }
        if (scan.unrecognized > 0) {
            footer.append("\n\n另有").append(scan.unrecognized).append("条课表信息不完整，结果可能遗漏。");
        }
        return footer.toString();
    }

    private static int positiveInt(JsonNode value) {
        if (value.isIntegralNumber() && value.canConvertToInt()) return value.intValue();
        if (value.isTextual() && value.asText().matches("[0-9]{1,9}")) return Integer.parseInt(value.asText());
        return -1;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? clean(value.asText()) : "";
    }

    private static String clean(String value) {
        return value == null ? "" : value.replaceAll("[\\p{Cntrl}\\s]+", " ").strip();
    }

    /** 课程和地点来自远端接口，作为普通文本展示，避免被解释为 Markdown 或 QQ 标签。 */
    private static String markdown(String value) {
        StringBuilder escaped = new StringBuilder();
        for (char character : value.toCharArray()) {
            switch (character) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                default -> {
                    if ("\\`*_{}[]()#!|~".indexOf(character) >= 0) escaped.append('\\');
                    escaped.append(character);
                }
            }
        }
        return escaped.toString();
    }

    private static String normalizeName(String value) {
        return clean(Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC));
    }

    private static String nameKey(String value) {
        return normalizeName(value).toLowerCase(Locale.ROOT);
    }

    private static String shorten(String value, int max) {
        if (value.length() <= max) return value;
        int end = max - 1;
        if (Character.isHighSurrogate(value.charAt(end - 1))) end--;
        return value.substring(0, end) + "…";
    }

    private record LessonKey(String teacher, LocalDate date, int start, int end, String course,
                             String location, String campus) {
    }

    private static final class Lesson {
        private final LessonKey key;
        private final String location;
        private final TreeSet<String> classes = new TreeSet<>();

        private Lesson(LessonKey key, String location) {
            this.key = key;
            this.location = location;
        }
    }

    private static final class Scan {
        private final Map<LessonKey, Lesson> lessons = new HashMap<>();
        private final TreeSet<String> candidates = new TreeSet<>();
        private int coveredClasses;
        private int unrecognized;
        private boolean exactNameFound;
    }
}
