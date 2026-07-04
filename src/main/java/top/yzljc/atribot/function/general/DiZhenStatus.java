package top.yzljc.atribot.function.general;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.service.taskscheduler.DefaultTaskSchedule;
import top.yzljc.atribot.service.taskscheduler.ScheduleMode;
import top.yzljc.atribot.service.taskscheduler.ScheduledTask;
import top.yzljc.atribot.service.taskscheduler.TaskSchedule;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @Author YZ_Ljc_
 * @ClassName DiZhenStatus
 * @Created_at 2026/07/11
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.general
 */
@Slf4j
public class DiZhenStatus implements ScheduledTask, CommandExecutor {

    private static final String REQ_URL = Config.getInstance().getDizhenStatusUrl();
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Set<DiZhenData> dizhenCache = Collections.synchronizedSet(new HashSet<>());

    @Override
    public TaskSchedule schedule() {
        return new DefaultTaskSchedule().setMode(ScheduleMode.a_quarter);
    }

    @Override
    public void run() {

    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        return true;
    }

    private void loadCache() {}

    private

    private record DiZhenData(String addTime, String level, String weidu, String jingdu, String shendu, String weizhi, String hcTime) {
    }


//    {
//            "addtime": "2026-07-11 01:01:48",
//                    "leve": "3.3",
//                    "weidu": "50.090000",
//                    "jingdu": "121.180000",
//                    "shendu": "10.00",
//                    "weizhi": "内蒙古呼伦贝尔市牙克石市",
//                    "hctime": "2026-07-11 01:09:17"
//    },
}