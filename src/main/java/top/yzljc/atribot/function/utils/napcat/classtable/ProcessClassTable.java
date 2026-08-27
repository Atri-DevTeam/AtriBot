package top.yzljc.atribot.function.utils.napcat.classtable;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.service.request.HttpService;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class ProcessClassTable {
    private static final Logger log = LoggerFactory.getLogger(ProcessClassTable.class);
    public static final int RESULT_FOUND = 0;
    public static final int RESULT_NOT_FOUND = -1;
    public static final int RESULT_REQUEST_FAILED = 404;
    private static final String CLASS_TABLE_URL =
            "https://ita.moentg.cn/api/class_table/get_raw_class_table?semester=2025-2026-2&major=%E8%BD%AF%E4%BB%B62501";
    private static final String MAJOR_KEY = "2025-2026-2_软件2501";
    private static final LocalDate SEMESTER_START = LocalDate.parse("2026-03-02");
    private static final LocalDate SEMESTER_END = LocalDate.parse("2026-06-28");

    public static void getClassTableJson(int sessionId) {
        getClassTableJson(sessionId, "1065552660");
    }

    public static void getClassTableJson(int sessionId, String groupId) {
        getClassTableJson(sessionId, groupId, LocalDate.now());
    }

    public static int getCurrentWeek(LocalDate startDate, LocalDate endDate) {
        LocalDate today = LocalDate.now();

        return getCurrentWeek(startDate, endDate, today);
    }

    public static int getCurrentWeek(String startDateStr, String endDateStr) {
        LocalDate start = LocalDate.parse(startDateStr);
        LocalDate end = LocalDate.parse(endDateStr);
        return getCurrentWeek(start, end);
    }

    public static int getCurrentWeek(LocalDate startDate, LocalDate endDate, LocalDate targetDate) {
        if (targetDate.isBefore(startDate)) {
            return 0;
        }
        if (targetDate.isAfter(endDate)) {
            return -1;
        }
        LocalDate startMonday = startDate.with(DayOfWeek.MONDAY);
        LocalDate targetMonday = targetDate.with(DayOfWeek.MONDAY);
        long weeksBetween = ChronoUnit.WEEKS.between(startMonday, targetMonday);
        return (int) weeksBetween + 1;
    }

    public static int getClassTableJson(int sessionId, String groupId, LocalDate targetDate) {
        int currentWeek = getCurrentWeek(SEMESTER_START, SEMESTER_END, targetDate);
        if (currentWeek <= 0) {
            return RESULT_NOT_FOUND;
        }

        JsonNode rootNode = HttpService.sendPostRequestFollowRedirect(CLASS_TABLE_URL);
        if (rootNode == null) {
            log.warn("课表获取失败，接口返回为空");
            return RESULT_REQUEST_FAILED;
        }

        String weekday = String.valueOf(targetDate.getDayOfWeek().getValue());
        List<ClassTableQueryUtil.ClassSession> targetClasses =
                ClassTableQueryUtil.getClasses(rootNode, MAJOR_KEY, weekday, sessionId);

        if (targetClasses.isEmpty()) {
            return RESULT_NOT_FOUND;
        }

        for (ClassTableQueryUtil.ClassSession sessionObj : targetClasses) {
            List<Integer> withInWeek = sessionObj.getWithInWeek();
            if (withInWeek != null && withInWeek.contains(currentWeek)) {
                StringBuilder sb = new StringBuilder();
                sb.append("下节课程安排\n");
                sb.append("课程: ").append(sessionObj.getClassData().getClassNameShow()).append("\n");
                sb.append("地点: ").append(sessionObj.getClassData().getLocation()).append(" ").append(sessionObj.getClassData().getFullLocation()).append("\n");
                sb.append("教师: ").append(sessionObj.getClassData().getTeacher()).append("\n");
                sb.append("节次: ").append(sessionObj.getClassData().getClassStartTime()).append("-").append(sessionObj.getClassData().getClassEndTime());
                if (!targetDate.equals(LocalDate.now())) {
                    sb.append("\n日期: ").append(targetDate);
                }
                GroupMessage.chatMessage(String.valueOf(groupId), sb.toString());
                return RESULT_FOUND;
            }
        }
        return RESULT_NOT_FOUND;
    }
}