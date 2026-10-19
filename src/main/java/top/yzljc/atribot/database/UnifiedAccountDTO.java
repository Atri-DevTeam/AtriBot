package top.yzljc.atribot.database;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import top.yzljc.atricore.auth.AccountStatus;
import top.yzljc.atricore.auth.AtriAccount;
import top.yzljc.atricore.auth.official.UnifiedRole;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * @Author YZ_Ljc_
 * @ClassName UnifiedAccountDTO
 * @Created_at 2026/08/13
 * @Project AtriMeow
 * @Package top.yzljc.atribot.database
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UnifiedAccountDTO {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private UUID uuid;
    private String username;
    private String qqUserOpenId;
    private String qqUserUin;
    private String minecraftUuid;
    private String role;
    private List<String> permissions;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime lastUpdateTime;
    private String qqConnectOpenId;

    /**
     * 转为统一账号，保留数据库 DATETIME 的原日期时间，不进行时区换算
     *
     * @return 统一账号数据
     */
    public AtriAccount toAccount() {
        return new AtriAccount(
                uuid,
                username,
                qqUserOpenId,
                qqUserUin,
                minecraftUuid,
                UnifiedRole.fromString(role),
                permissions,
                AccountStatus.fromString(status),
                createTime == null ? null : createTime.format(TIME_FORMAT),
                lastUpdateTime == null ? null : lastUpdateTime.format(TIME_FORMAT),
                qqConnectOpenId
        );
    }

}
