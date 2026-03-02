package top.yzljc.qqbot.data;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TufeClassTable {
    private String classShowName;
    private String classLocation;
    private String classFullLocation;
    private String classTeacherName;
    private float classCredit;
    private int classWeek;
    private int classStartSession;
    private int classEndSession;
    private boolean isClassEnd;
    private int[] classWeekList;
}
