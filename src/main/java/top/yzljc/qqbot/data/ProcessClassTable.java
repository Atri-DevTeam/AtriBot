package top.yzljc.qqbot.data;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.botservice.message.MessageSender;
import top.yzljc.qqbot.botservice.request.HttpRequest;
import top.yzljc.qqbot.config.Config;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class ProcessClassTable {
    private static final Logger log = LoggerFactory.getLogger(ProcessClassTable.class);

    private static String getCurrentWeekNum() {
        return String.valueOf(LocalDateTime.now().getDayOfWeek().getValue());
    }

    public static int getClassTableJson(int sessionId) {
        return getClassTableJson(sessionId, Config.getInstance().getDebugGroupId());
    }

    public static int getClassTableJson(int sessionId, long groupId) {
        String url = "https://ita.moentg.cn/api/class_table/get_raw_class_table?semester=2025-2026-2&major=%E8%BD%AF%E4%BB%B62501";
        int currentWeek = getCurrentWeek("2026-03-02", "2026-06-28");

        JsonNode rootNode = HttpRequest.sendPostRequest(url, new HashMap<>());
        if (rootNode == null) {
            log.warn("课表获取失败，接口返回为空");
            return 404;
        }

        String majorKey = "2025-2026-2_软件2501";
        String weekday = getCurrentWeekNum();

        List<ClassTableQueryUtil.ClassSession> targetClasses =
                ClassTableQueryUtil.getClasses(rootNode, majorKey, weekday, sessionId);

        if (targetClasses.isEmpty()) {
            System.out.println("当前没有课");
            return -1;
        }

        for (ClassTableQueryUtil.ClassSession sessionObj : targetClasses) {
            if (sessionObj.getWithInWeek().contains(currentWeek)) {
                StringBuilder sb = new StringBuilder();
                sb.append("下节课程安排\n");
                sb.append("课程: ").append(sessionObj.getClassData().getClassNameShow()).append("\n");
                sb.append("地点: ").append(sessionObj.getClassData().getLocation()).append(" ").append(sessionObj.getClassData().getFullLocation()).append("\n");
                sb.append("教师: ").append(sessionObj.getClassData().getTeacher()).append("\n");
                sb.append("节次: ").append(sessionObj.getClassData().getClassStartTime()).append("-").append(sessionObj.getClassData().getClassEndTime()).append("\n");
                MessageSender.sendGroupMessage(groupId, sb.toString());
                return 0;
            }else {
                return -1;
            }
        }
        return 404;
    }

    public static int getCurrentWeek(LocalDate startDate, LocalDate endDate) {
        LocalDate today = LocalDate.now();

        if (today.isBefore(startDate)) {
            return 0; // 未开学
        }

        if (today.isAfter(endDate)) {
            return -1; // 已结束/放假
        }
        LocalDate startMonday = startDate.with(DayOfWeek.MONDAY);
        LocalDate currentMonday = today.with(DayOfWeek.MONDAY);
        long weeksBetween = ChronoUnit.WEEKS.between(startMonday, currentMonday);

        return (int) weeksBetween + 1;
    }

    public static int getCurrentWeek(String startDateStr, String endDateStr) {
        LocalDate start = LocalDate.parse(startDateStr);
        LocalDate end = LocalDate.parse(endDateStr);
        return getCurrentWeek(start, end);
    }
}