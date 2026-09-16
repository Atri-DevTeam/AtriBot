package top.yzljc.atribot.webui.repo;

import top.yzljc.atribot.database.DatabaseManager;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** 数据库使用情况 */
public class DatabaseStatsRepo {

    /** 查询当前数据库的表统计信息，不扫描业务表。 */
    public static DatabaseUsage queryUsage() throws SQLException {
        String sql = """
                SELECT TABLE_NAME, ENGINE, COALESCE(TABLE_ROWS, 0) AS estimated_rows,
                       COALESCE(DATA_LENGTH, 0) AS data_bytes,
                       COALESCE(INDEX_LENGTH, 0) AS index_bytes
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE'
                ORDER BY data_bytes + index_bytes DESC, TABLE_NAME
                """;
        List<TableUsage> tables = new ArrayList<>();
        long dataBytes = 0;
        long indexBytes = 0;
        long estimatedRows = 0;
        String database;
        try (var conn = DatabaseManager.getConnection();
             var ps = conn.prepareStatement(sql)) {
            database = conn.getCatalog();
            ps.setQueryTimeout(10);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    long data = rs.getLong("data_bytes");
                    long indexes = rs.getLong("index_bytes");
                    long rows = rs.getLong("estimated_rows");
                    tables.add(new TableUsage(rs.getString("TABLE_NAME"), rs.getString("ENGINE"),
                            rows, data, indexes, data + indexes));
                    dataBytes += data;
                    indexBytes += indexes;
                    estimatedRows += rows;
                }
            }
        }
        return new DatabaseUsage(database, dataBytes, indexBytes, dataBytes + indexBytes,
                tables.size(), estimatedRows, System.currentTimeMillis(), List.copyOf(tables));
    }

    public record DatabaseUsage(String database, long dataBytes, long indexBytes, long totalBytes,
                                int tableCount, long estimatedRows, long updatedAt, List<TableUsage> tables) {}

    public record TableUsage(String name, String engine, long estimatedRows,
                             long dataBytes, long indexBytes, long totalBytes) {}
}
