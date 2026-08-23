package top.yzljc.atribot.chat.official.moderation;

import lombok.Data;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** AI 消息审查的周期生效时间。星期使用 ISO 编号：周一为 1，周日为 7。 */
@Data
public class AiModerationSchedule {
    private boolean enabled = false;
    private String startDate = "";
    private String endDate = "";
    private String startTime = "00:00";
    private String endTime = "23:59";
    private List<Integer> daysOfWeek = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6, 7));

    public boolean isActive(LocalDateTime now) {
        if (!enabled) {
            return true;
        }
        try {
            LocalTime from = LocalTime.parse(startTime);
            LocalTime to = LocalTime.parse(endTime);
            LocalDate effectiveDate = now.toLocalDate();
            boolean timeMatches;

            if (from.equals(to)) {
                timeMatches = true;
            } else if (from.isBefore(to)) {
                timeMatches = !now.toLocalTime().isBefore(from) && now.toLocalTime().isBefore(to);
            } else if (!now.toLocalTime().isBefore(from)) {
                timeMatches = true;
            } else {
                timeMatches = now.toLocalTime().isBefore(to);
                effectiveDate = effectiveDate.minusDays(1);
            }

            if (!timeMatches || !withinDateRange(effectiveDate)) {
                return false;
            }
            return daysOfWeek != null && daysOfWeek.contains(effectiveDate.getDayOfWeek().getValue());
        } catch (DateTimeException | NullPointerException e) {
            return false;
        }
    }

    private boolean withinDateRange(LocalDate date) {
        if (startDate != null && !startDate.isBlank() && date.isBefore(LocalDate.parse(startDate))) {
            return false;
        }
        return endDate == null || endDate.isBlank() || !date.isAfter(LocalDate.parse(endDate));
    }
}
