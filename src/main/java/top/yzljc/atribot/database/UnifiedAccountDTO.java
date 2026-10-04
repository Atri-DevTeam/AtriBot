package top.yzljc.atribot.database;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import top.yzljc.atribot.auth.AccountStatus;
import top.yzljc.atribot.auth.AtriAccount;
import top.yzljc.atribot.auth.official.UnifiedRole;
import top.yzljc.atribot.utils.FormatTools;

import java.sql.Timestamp;
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

    private UUID uuid;
    private String username;
    private String qqUserOpenId;
    private String qqUserUin;
    private String minecraftUuid;
    private String role;
    private List<String> permissions;
    private String status;
    private Timestamp createTime;
    private Timestamp lastUpdateTime;

    /**
     * 转回业务侧 record（role 反查枚举，时间戳转字符串）。
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
                FormatTools.formatTimestamp(createTime, null),
                FormatTools.formatTimestamp(lastUpdateTime, null)
        );
    }

}
