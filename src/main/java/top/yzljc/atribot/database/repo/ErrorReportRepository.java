package top.yzljc.atribot.database.repo;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.database.DatabaseManager;
import top.yzljc.atribot.database.ErrorReportDTO;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName ErrorReportRepository
 * @Project AtriMeow
 * @Package top.yzljc.atribot.database.repo
 */
@Slf4j
public class ErrorReportRepository {

    /**
     * 堆栈在库中以换行分隔的纯文本保存，读取时再拆回列表
     */
    private static final String STACK_SEPARATOR = "\n";

    private ErrorReportRepository() {
    }

    // ==================== 初始化 ====================

    public static void init() {
        String sql = "CREATE TABLE IF NOT EXISTS `error_report` (" +
                "  `trace_id` VARCHAR(36) NOT NULL," +
                "  `class_name` VARCHAR(512) NOT NULL," +
                "  `exception_type` VARCHAR(512) NOT NULL," +
                "  `exception_message` TEXT NULL," +
                "  `stack_trace` MEDIUMTEXT NULL," +
                "  `cause_type` VARCHAR(512) NULL," +
                "  `cause_message` TEXT NULL," +
                "  `cause_stack_trace` MEDIUMTEXT NULL," +
                "  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "  PRIMARY KEY (`trace_id`)," +
                "  INDEX `idx_create_time` (`create_time`)," +
                "  INDEX `idx_class_name` (`class_name`(191))," +
                "  INDEX `idx_exception_type` (`exception_type`(191))" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        try (var con = DatabaseManager.getConnection(); var ps = con.prepareStatement(sql)) {
            ps.execute();
            log.info("错误报告数据库表初始化完成");
        } catch (Exception e) {
            log.error("初始化错误报告数据库表失败", e);
        }
    }

    // ==================== 写入 ====================

    /**
     * 落库一条错误报告
     *
     * @return 是否写入成功
     */
    public static boolean insert(ErrorReportDTO report) {
        String sql = "INSERT INTO `error_report` (`trace_id`, `class_name`, `exception_type`, `exception_message`, " +
                "`stack_trace`, `cause_type`, `cause_message`, `cause_stack_trace`, `create_time`) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, report.getTraceId());
            ps.setString(2, report.getClassName());
            ps.setString(3, report.getExceptionType());
            ps.setString(4, report.getExceptionMessage());
            ps.setString(5, joinStack(report.getStackTrace()));
            ps.setString(6, report.getCauseType());
            ps.setString(7, report.getCauseMessage());
            ps.setString(8, joinStack(report.getCauseStackTrace()));
            ps.setTimestamp(9, report.getCreateTime() != null
                    ? report.getCreateTime()
                    : new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
            return true;
        } catch (Exception e) {
            log.error("写入错误报告失败: traceId={}", report.getTraceId(), e);
            return false;
        }
    }

    // ==================== 查询 ====================

    /**
     * 按 traceId 查询单条错误详情（含完整堆栈）
     */
    public static ErrorReportDTO findByTraceId(String traceId) {
        String sql = "SELECT * FROM `error_report` WHERE `trace_id` = ?";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, traceId);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rowToDTO(rs, true);
                }
            }
        } catch (Exception e) {
            log.error("按 traceId 查询错误报告失败: traceId={}", traceId, e);
        }
        return null;
    }

    /**
     * 分页查询错误列表，列表不返回堆栈以减小体积
     *
     * @param keyword       模糊匹配类名 / 异常类型 / 异常消息，可为空
     * @param exceptionType 精确匹配异常类型，可为空
     */
    public static List<ErrorReportDTO> findPaginated(int page, int pageSize, String keyword, String exceptionType) {
        List<Object> params = new ArrayList<>();
        String sql = "SELECT `trace_id`, `class_name`, `exception_type`, `exception_message`, `cause_type`, " +
                "`cause_message`, `create_time` FROM `error_report`" +
                buildWhere(keyword, exceptionType, params) +
                " ORDER BY `create_time` DESC LIMIT ? OFFSET ?";

        params.add(pageSize);
        params.add((page - 1) * pageSize);

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            bind(ps, params);
            try (var rs = ps.executeQuery()) {
                List<ErrorReportDTO> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(rowToDTO(rs, false));
                }
                return list;
            }
        } catch (Exception e) {
            log.error("分页查询错误报告失败", e);
            return List.of();
        }
    }

    /**
     * 统计符合条件的错误总数，用于分页
     */
    public static int count(String keyword, String exceptionType) {
        List<Object> params = new ArrayList<>();
        String sql = "SELECT COUNT(*) FROM `error_report`" + buildWhere(keyword, exceptionType, params);

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            bind(ps, params);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (Exception e) {
            log.error("统计错误报告总数失败", e);
        }
        return 0;
    }

    /**
     * 最近 N 小时内的错误数量
     */
    public static int countSince(int hours) {
        String sql = "SELECT COUNT(*) FROM `error_report` WHERE `create_time` >= DATE_SUB(NOW(), INTERVAL ? HOUR)";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setInt(1, hours);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (Exception e) {
            log.error("统计近期错误数量失败: hours={}", hours, e);
        }
        return 0;
    }

    /**
     * 按异常类型聚合出现次数，倒序返回 TopN，用于统计面板
     */
    public static Map<String, Integer> topExceptionTypes(int limit) {
        String sql = "SELECT `exception_type`, COUNT(*) AS `cnt` FROM `error_report` " +
                "GROUP BY `exception_type` ORDER BY `cnt` DESC LIMIT ?";

        Map<String, Integer> result = new LinkedHashMap<>();
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString("exception_type"), rs.getInt("cnt"));
                }
            }
        } catch (Exception e) {
            log.error("聚合异常类型失败", e);
        }
        return result;
    }

    /**
     * 清理指定天数之前的历史错误，返回删除条数
     */
    public static int deleteOlderThan(int days) {
        String sql = "DELETE FROM `error_report` WHERE `create_time` < DATE_SUB(NOW(), INTERVAL ? DAY)";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setInt(1, days);
            int affected = ps.executeUpdate();
            if (affected > 0) {
                log.info("清理历史错误报告完成: 删除 {} 条 ({} 天前)", affected, days);
            }
            return affected;
        } catch (Exception e) {
            log.error("清理历史错误报告失败: days={}", days, e);
            return 0;
        }
    }

    // ==================== 内部工具 ====================

    private static String buildWhere(String keyword, String exceptionType, List<Object> params) {
        StringBuilder where = new StringBuilder();

        if (exceptionType != null && !exceptionType.isBlank()) {
            where.append(where.isEmpty() ? " WHERE " : " AND ").append("`exception_type` = ?");
            params.add(exceptionType);
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(where.isEmpty() ? " WHERE " : " AND ")
                    .append("(`class_name` LIKE ? OR `exception_type` LIKE ? OR `exception_message` LIKE ?)");
            String like = "%" + keyword + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        return where.toString();
    }

    private static void bind(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    private static String joinStack(List<String> stack) {
        return stack == null || stack.isEmpty() ? null : String.join(STACK_SEPARATOR, stack);
    }

    private static List<String> splitStack(String raw) {
        return raw == null || raw.isBlank() ? List.of() : List.of(raw.split(STACK_SEPARATOR));
    }

    private static ErrorReportDTO rowToDTO(ResultSet rs, boolean withStack) throws SQLException {
        ErrorReportDTO dto = new ErrorReportDTO();
        dto.setTraceId(rs.getString("trace_id"));
        dto.setClassName(rs.getString("class_name"));
        dto.setExceptionType(rs.getString("exception_type"));
        dto.setExceptionMessage(rs.getString("exception_message"));
        dto.setCauseType(rs.getString("cause_type"));
        dto.setCauseMessage(rs.getString("cause_message"));
        dto.setCreateTime(rs.getTimestamp("create_time"));
        if (withStack) {
            dto.setStackTrace(splitStack(rs.getString("stack_trace")));
            dto.setCauseStackTrace(splitStack(rs.getString("cause_stack_trace")));
        }
        return dto;
    }
}
