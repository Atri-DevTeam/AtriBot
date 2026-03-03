package top.yzljc.qqbot.data;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class ProcessClassTable {

    private int getCurrentWeekNum() {
        return LocalDateTime.now().getDayOfWeek().getValue();
    }


}
