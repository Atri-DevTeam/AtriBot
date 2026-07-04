package top.yzljc.atribot.function.task

import top.yzljc.atribot.function.napcat.CheckMojira
import top.yzljc.atribot.service.timer.Schedule
import top.yzljc.atribot.service.timer.ScheduleType

/**
 * @Author YZ_Ljc_
 * @ClassName MojiraChecker
 * @Created_at 2026/06/28
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.task
 */
object MojiraChecker {

    @Schedule(time = "00:00", type = ScheduleType.HALF_HOURLY)
    fun checkMojira() {
        CheckMojira.checkNewIssues()
    }
}