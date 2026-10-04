package top.yzljc.atribot.database.repo;

import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;

@Slf4j
public final class OfficialMessageIdSchema {
    public static final String ID_COLUMN = "`message_openId` MEDIUMTEXT NULL";
    public static final String HASH_COLUMN = "`message_id_hash` BINARY(32) GENERATED ALWAYS AS "
            + "(UNHEX(SHA2(`message_openId`, 256))) STORED";
    public static final String MATCH_ID = "message_id_hash = UNHEX(SHA2(?, 256)) AND message_openId = ?";

    private OfficialMessageIdSchema() {}

    public static void migrate(Connection connection) throws SQLException {
        migrate(connection, "official_group_record", "uk_group_message_openId");
        migrate(connection, "official_c2c_record", "uk_c2c_message_openId");
    }

    private static void migrate(Connection connection, String table, String index) throws SQLException {
        boolean hasHash = false;
        String idType = null;
        try (var columns = connection.getMetaData().getColumns(connection.getCatalog(), null, table, null)) {
            while (columns.next()) {
                if (!table.equalsIgnoreCase(columns.getString("TABLE_NAME"))) continue;
                String column = columns.getString("COLUMN_NAME");
                if ("message_openId".equalsIgnoreCase(column)) idType = columns.getString("TYPE_NAME");
                if ("message_id_hash".equalsIgnoreCase(column)) hasHash = true;
            }
        }
        if (idType == null) throw new SQLException("消息记录表缺少 message_openId 字段: " + table);

        boolean hasIndex = false;
        boolean hashIndex = false;
        try (var indexes = connection.getMetaData().getIndexInfo(connection.getCatalog(), null, table, false, false)) {
            while (indexes.next()) {
                if (!index.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) continue;
                hasIndex = true;
                hashIndex = "message_id_hash".equalsIgnoreCase(indexes.getString("COLUMN_NAME"))
                        && !indexes.getBoolean("NON_UNIQUE");
            }
        }
        boolean longId = "MEDIUMTEXT".equalsIgnoreCase(idType) || "LONGTEXT".equalsIgnoreCase(idType);
        if (longId && hasHash && hashIndex) return;

        var changes = new ArrayList<String>();
        if (hasIndex && !hashIndex) changes.add("DROP INDEX `" + index + "`");
        if (!longId) changes.add("MODIFY COLUMN " + ID_COLUMN);
        if (!hasHash) changes.add("ADD COLUMN " + HASH_COLUMN);
        if (!hashIndex) changes.add("ADD UNIQUE KEY `" + index + "` (`message_id_hash`)");
        // 同一次表变更完成扩容与索引替换，旧数据的摘要由数据库生成。
        log.info("正在升级官方消息 ID 字段及索引: {}", table);
        try (var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE `" + table + "` " + String.join(", ", changes));
        }
        log.info("官方消息 ID 字段及索引升级完成: {}", table);
    }
}
