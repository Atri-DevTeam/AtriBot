package top.yzljc.qqbot.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;

import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseManager {

    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);
    @Getter
    private static HikariDataSource dataSource;

    static {
        try {
            initDataSource();
        } catch (Exception e) {
            log.error("初始化数据库连接失败: {}", e.getMessage(), e);
        }
    }

    private static void initDataSource() {
        Settings settings = Config.getInstance();
        String host = settings.getMysqlHost();
        int port = settings.getMysqlPort();
        String database = settings.getMysqlDatabase();
        String user = settings.getMysqlUsername();
        String password = settings.getMysqlPassword();

        String dbUrl = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&serverTimezone=UTC&characterEncoding=utf8";

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dbUrl);
        config.setUsername(user);
        config.setPassword(password);

        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);

        dataSource = new HikariDataSource(config);
        log.info("数据库连接池初始化完成！");
    }

    public static Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("数据源未初始化，无法获取连接");
        }
        return dataSource.getConnection();
    }
}