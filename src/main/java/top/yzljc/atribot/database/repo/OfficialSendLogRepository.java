package top.yzljc.atribot.database.repo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.database.DatabaseManager;
import top.yzljc.atribot.database.OfficialSendLogDTO;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
public class OfficialSendLogRepository {

    public static final String TYPE_SEND = "SEND";
    public static final String TYPE_RESPONSE = "RESPONSE";
    public static final String TYPE_ERROR = "ERROR";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void init() {
        String sql = "CREATE TABLE IF NOT EXISTS `official_send_log` (" +
                "  `id` BIGINT NOT NULL AUTO_INCREMENT," +
                "  `trace_id` VARCHAR(36) NOT NULL," +
                "  `entry_type` VARCHAR(16) NOT NULL," +
                "  `scene` VARCHAR(64) NULL," +
                "  `method` VARCHAR(12) NOT NULL," +
                "  `url` MEDIUMTEXT NOT NULL," +
                "  `request_json` MEDIUMTEXT NULL," +
                "  `response_status` INT NULL," +
                "  `response_body` MEDIUMTEXT NULL," +
                "  `error_code` INT NULL," +
                "  `error_reason` VARCHAR(512) NULL," +
                "  `error_message` MEDIUMTEXT NULL," +
                "  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "  PRIMARY KEY (`id`)," +
                "  KEY `idx_trace_id` (`trace_id`)," +
                "  KEY `idx_entry_type_create_time` (`entry_type`, `create_time`)," +
                "  KEY `idx_create_time` (`create_time`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        try (var con = DatabaseManager.getConnection(); var ps = con.prepareStatement(sql)) {
            ps.execute();
            log.info("官方机器人发送日志表初始化完成");
        } catch (Exception e) {
            log.error("初始化官方机器人发送日志表失败", e);
        }
    }

    public static String recordSend(String scene, String method, String url, String requestJson) {
        String traceId = UUID.randomUUID().toString();
        insert(traceId, TYPE_SEND, scene, method, url, requestJson, null, null, null);
        return traceId;
    }

    public static void recordResponse(String traceId, String scene, String method, String url,
                                      String requestJson, Integer responseStatus, String responseBody) {
        insert(normalizeTraceId(traceId), TYPE_RESPONSE, scene, method, url, requestJson,
                responseStatus, responseBody, null);
    }

    public static void recordError(String traceId, String scene, String method, String url,
                                   String requestJson, Integer responseStatus, String responseBody,
                                   String errorMessage) {
        insert(normalizeTraceId(traceId), TYPE_ERROR, scene, method, url, requestJson,
                responseStatus, responseBody, errorMessage);
    }

    public static List<OfficialSendLogDTO> findPaginated(int page, int pageSize, String entryType, String keyword) {
        List<Object> params = new ArrayList<>();
        String sql = "SELECT `id`, `trace_id`, `entry_type`, `scene`, `method`, `url`, " +
                "LEFT(`request_json`, 400) AS `request_json`, `response_status`, " +
                "LEFT(`response_body`, 400) AS `response_body`, `error_code`, `error_reason`, " +
                "LEFT(`error_message`, 400) AS `error_message`, `create_time` " +
                "FROM `official_send_log`" + buildWhere(entryType, keyword, params) +
                " ORDER BY `create_time` DESC, `id` DESC LIMIT ? OFFSET ?";
        params.add(pageSize);
        params.add((page - 1) * pageSize);

        try (var con = DatabaseManager.getConnection(); var ps = con.prepareStatement(sql)) {
            bind(ps, params);
            try (var rs = ps.executeQuery()) {
                List<OfficialSendLogDTO> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(rowToDTO(rs));
                }
                return result;
            }
        } catch (Exception e) {
            log.error("分页查询官方发送日志失败", e);
            return List.of();
        }
    }

    public static OfficialSendLogDTO findById(long id) {
        String sql = "SELECT * FROM `official_send_log` WHERE `id` = ?";
        try (var con = DatabaseManager.getConnection(); var ps = con.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rowToDTO(rs);
                }
            }
        } catch (Exception e) {
            log.error("按 id 查询官方发送日志失败: id={}", id, e);
        }
        return null;
    }

    public static int count(String entryType, String keyword) {
        List<Object> params = new ArrayList<>();
        String sql = "SELECT COUNT(*) FROM `official_send_log`" + buildWhere(entryType, keyword, params);
        try (var con = DatabaseManager.getConnection(); var ps = con.prepareStatement(sql)) {
            bind(ps, params);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (Exception e) {
            log.error("统计官方发送日志失败", e);
        }
        return 0;
    }

    public static SendLogStats stats() {
        return new SendLogStats(
                count(null, null),
                count(TYPE_SEND, null),
                count(TYPE_RESPONSE, null),
                count(TYPE_ERROR, null)
        );
    }

    private static void insert(String traceId, String entryType, String scene, String method, String url,
                               String requestJson, Integer responseStatus, String responseBody,
                               String errorMessage) {
        String sql = "INSERT INTO `official_send_log` (`trace_id`, `entry_type`, `scene`, `method`, `url`, " +
                "`request_json`, `response_status`, `response_body`, `error_code`, `error_reason`, " +
                "`error_message`, `create_time`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        ErrorInfo errorInfo = TYPE_ERROR.equals(entryType) ? extractErrorInfo(responseBody) : new ErrorInfo(null, null);

        try (var con = DatabaseManager.getConnection(); var ps = con.prepareStatement(sql)) {
            ps.setString(1, traceId);
            ps.setString(2, entryType);
            ps.setString(3, scene);
            ps.setString(4, method == null || method.isBlank() ? "POST" : method.toUpperCase());
            ps.setString(5, url);
            ps.setString(6, requestJson);
            if (responseStatus == null) ps.setNull(7, Types.INTEGER);
            else ps.setInt(7, responseStatus);
            ps.setString(8, responseBody);
            if (errorInfo.code() == null) ps.setNull(9, Types.INTEGER);
            else ps.setInt(9, errorInfo.code());
            ps.setString(10, limit(errorInfo.message(), 512));
            ps.setString(11, errorMessage);
            ps.setTimestamp(12, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        } catch (Exception e) {
            log.error("写入官方发送日志失败: traceId={}, entryType={}", traceId, entryType, e);
        }
    }

    private static String buildWhere(String entryType, String keyword, List<Object> params) {
        StringBuilder where = new StringBuilder();
        if (entryType != null && !entryType.isBlank() && !"ALL".equalsIgnoreCase(entryType)) {
            where.append(" WHERE `entry_type` = ?");
            params.add(entryType.toUpperCase());
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(where.isEmpty() ? " WHERE " : " AND ")
                    .append("(`trace_id` LIKE ? OR `scene` LIKE ? OR `method` LIKE ? OR `url` LIKE ? ")
                    .append("OR `request_json` LIKE ? OR `response_body` LIKE ? OR `error_message` LIKE ? ")
                    .append("OR `error_reason` LIKE ?)");
            String like = "%" + keyword + "%";
            for (int i = 0; i < 8; i++) {
                params.add(like);
            }
        }
        return where.toString();
    }

    private static void bind(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    private static OfficialSendLogDTO rowToDTO(ResultSet rs) throws SQLException {
        OfficialSendLogDTO dto = new OfficialSendLogDTO();
        dto.setId(rs.getLong("id"));
        dto.setTraceId(rs.getString("trace_id"));
        dto.setEntryType(rs.getString("entry_type"));
        dto.setScene(rs.getString("scene"));
        dto.setMethod(rs.getString("method"));
        dto.setUrl(rs.getString("url"));
        dto.setRequestJson(rs.getString("request_json"));
        int status = rs.getInt("response_status");
        dto.setResponseStatus(rs.wasNull() ? null : status);
        dto.setResponseBody(rs.getString("response_body"));
        int code = rs.getInt("error_code");
        dto.setErrorCode(rs.wasNull() ? null : code);
        dto.setErrorReason(rs.getString("error_reason"));
        dto.setErrorMessage(rs.getString("error_message"));
        dto.setCreateTime(rs.getTimestamp("create_time"));
        return dto;
    }

    private static String normalizeTraceId(String traceId) {
        return traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId;
    }

    private static ErrorInfo extractErrorInfo(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return new ErrorInfo(null, null);
        }
        try {
            JsonNode node = MAPPER.readTree(responseBody);
            JsonNode codeNode = firstPresent(node, "err_code", "code");
            Integer code = parseErrorCode(codeNode);
            JsonNode messageNode = firstPresent(node, "message", "msg", "error", "error_description");
            String message = messageNode == null || messageNode.isNull() ? null : messageNode.asText(null);
            return new ErrorInfo(code, message);
        } catch (Exception ignored) {
            return new ErrorInfo(null, null);
        }
    }

    private static JsonNode firstPresent(JsonNode node, String... keys) {
        if (node == null) {
            return null;
        }
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private static Integer parseErrorCode(JsonNode codeNode) {
        if (codeNode == null || codeNode.isNull()) {
            return null;
        }
        if (codeNode.canConvertToInt()) {
            return codeNode.asInt();
        }
        try {
            return Integer.parseInt(codeNode.asText());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    public record SendLogStats(int all, int send, int response, int error) {
    }

    private record ErrorInfo(Integer code, String message) {
    }
}
