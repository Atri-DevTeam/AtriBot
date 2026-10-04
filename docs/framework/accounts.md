# 账号与权限

[文档目录](../README.md) · [平台与消息](platforms.md)

## 三类身份信息

| 类型 | 含义 |
| --- | --- |
| 平台用户 `User` | 当前消息来源的用户 ID、名称、平台及平台数据 |
| `AtriAccount` | 项目内的账号及已建立的平台绑定 |
| 权限记录与平台角色 | 机器人授权记录，以及群或服务器中的原生角色 |

平台角色与机器人权限分别判断。群主、频道管理员等平台身份不自动等价于机器人的 `ADMIN` 或 `OWNER`

## AtriAccount

[AtriAccount](../../src/main/java/top/yzljc/atribot/auth/AtriAccount.java) 使用 record 表达账号数据：

| 字段 | 含义 |
| --- | --- |
| `uuid`、`username` | 项目内账号标识与名称 |
| `qqUserOpenId`、`qqUserUin` | QQ 官方身份与 QQ 号绑定 |
| `minecraftUuid` | Minecraft 身份绑定 |
| `role`、`permissions` | 账号角色与权限记录 |
| `status` | 账号状态 |
| `createTime`、`lastUpdateTime` | 创建与更新时间 |

查询、绑定和缓存服务仍名为 [UnifiedAuthentication](../../src/main/java/top/yzljc/atribot/auth/UnifiedAuthentication.java)，数据库表为 `unified_account`

```java
Optional<AtriAccount> account = user.getAccount();
```

| 用户类型 | 账号查询方式 |
| --- | --- |
| `QQUser` | 按用户 OpenID 查询 |
| `QQGuildUser` | 按可用的 `userOpenId` 查询，不使用频道用户 ID 代替 |
| `NapcatUser` | 按 QQ 号查询 |
| `DiscordUser`、`KookUser` | 当前返回 `Optional.empty()` |

查询不自动创建绑定，也不会将所有平台权限合并到当前用户

## 权限判断

业务通过 `User.hasPermission(permission)` 或 `CommandSender.hasPermission(permission)` 检查权限。无参数版本用于对应平台实现中的机器人管理权限判断

QQ 官方用户的节点判断为：机器人管理员，或显式拥有该权限。机器人 `ADMIN` / `OWNER` 默认通过；平台群角色需另外判断

[OfficialUsers.hasPermission](../../src/main/java/top/yzljc/atribot/auth/official/OfficialUsers.java) 本身检查精确节点或 `*`。用户封装层的管理角色判断与该静态方法不是同一层逻辑，也不支持从 `example.*` 推导任意子权限

其他平台保留各自实现：

- QQ 频道权限依赖可用的用户 OpenID
- Napcat 使用配置的管理员列表和现有权限记录
- Discord 使用当前实现中的 `OfficialUsers` 权限查询
- KOOK 使用 `kook.admin-ids`，列表中的用户通过权限检查，尚无独立节点存储

封禁状态是独立检查，不能只检查权限就推定用户可操作。HTTP 接口还需校验登录身份、目标对象归属和具体操作权限

## 临时登录验证

[LoginService](../../src/main/java/top/yzljc/atribot/auth/LoginService.java) 提供验证码申请与 `/login <验证码>` 确认。插件通过上下文取得宿主的共享实例：

```java
LoginService service = getContext().getLoginService();
LoginService.LoginRequest request =
        service.createWithPermission("example.login", "/example/api/*");
```

申请包含验证码、请求 ID、过期时间和 `completion()`。用户完成命令确认后，异步结果提供 `identity()`、`apiPaths()`、`allows(path)`、`confirmedAt()`

默认 `create(paths...)` 要求 `api.login`。指定权限的申请只检查相应节点，权限名称与授权路径由服务端代码固定

### 路径范围

| 声明 | 范围 |
| --- | --- |
| `/api/status` | 只匹配该路径 |
| `/api/users/*` | 匹配 `/api/users` 及后代，不匹配 `/api/users2` |

只支持精确路径和末尾 `/*`，不支持根范围 `/*`、中间通配或 `**`。`allows()` 接收路径，不接收完整 URL 或查询参数；编码字符、反斜杠及异常路径段不通过匹配

### 生命周期

验证码为六位数字，有效期 5 分钟，只能确认一次。超时以 `TimeoutException` 完成，取消或关闭以 `CancellationException` 完成；请求可通过 `cancel(requestId)` 取消

申请保存在内存中，重启失效。插件在请求断开或停用时应取消未完成申请，耗时结果处理使用异步回调

验证结果只证明身份与授权范围，不自动建立 HTTP 会话，也不会拦截接口。插件负责自己的 Token / Cookie、有效期、撤销与访问检查；用户 ID 应与 `identity.senderType()` 一起识别
