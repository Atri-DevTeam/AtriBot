package top.yzljc.atribot.webui.controller;

import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.webui.Result;
import top.yzljc.atribot.webui.repo.DatabaseStatsRepo;

import java.sql.SQLException;

/** 数据库统计 */
@Slf4j
public class DatabaseStatsController {

    public static void usage(Context ctx) {
        try {
            ctx.json(Result.success(DatabaseStatsRepo.queryUsage()));
        } catch (SQLException e) {
            log.error("查询数据库使用情况失败: {}", e.getMessage(), e);
            ctx.status(500).json(Result.fail(500, "查询数据库使用情况失败"));
        }
    }
}
