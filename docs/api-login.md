# 插件调用临时登录验证

核心只提供 Java 验证逻辑和 `/login <六位验证码>` 命令。请求接收、对外响应、Token/Cookie、登录会话及访问控制的接入由插件完成。

## 插件调用

```java
import top.yzljc.atribot.auth.LoginService;

// 在 AtriPlugin 内获取与宿主 /login 命令共享的服务。
LoginService service = getContext().getLoginService();
LoginService.LoginRequest request = service.create("/api/*");

String code = request.code(); // 插件返回给自己的请求方
request.completion().thenAcceptAsync(result -> {
    // 用户发送 /login <code> 且通过权限判断后，触发原请求的回调。
    String userId = result.identity().userId();
    String username = result.identity().username();
    boolean allowed = result.allows("/api/example");

    // 在这里由插件完成原请求的响应或建立自己的登录会话。
}).exceptionally(error -> {
    // 超时、取消或宿主关闭：插件通知自己的请求方验证失败。
    return null;
});
```

也可直接使用 `LoginService.getInstance()`，与插件上下文返回的是同一实例。不要自己创建另一份服务，否则宿主命令无法找到对应验证码。

默认 `create(...)` 要求 `api.login`。插件可使用 `createWithPermission("webui.shizoukia", "/webui/api/groups")` 指定该申请的审批权限，`/login` 按验证码所属申请检查 `sender.hasPermission(...)`，无需同时拥有 `api.login`。未通过权限检查不会消费验证码。权限名称必须由插件代码固定，不能从网页请求中获取。

默认申请仍经过 `LoginCommand.canApprove(sender)`，指定权限的申请经过 `canApprove(sender, permission)`。

## Java 接口

| 接口 | 用途 |
| --- | --- |
| `create(String... apiPaths)` | 创建申请，获取验证码、请求 ID、过期时间和异步结果 |
| `request.completion()` | 注册验证成功回调及异常处理 |
| `createWithPermission(permission, apiPaths...)` | 使用插件指定的权限创建申请 |
| `requiredPermission(code)` | 返回尚未确认申请的审批权限，无效时返回 null |
| `cancel(request.requestId())` | 取消尚未完成的申请，返回是否成功取消 |
| `result.identity()` | 确认者的用户 ID、用户名和命令来源类型 |
| `result.apiPaths()` | 申请时指定的不可变路径列表 |
| `result.allows(path)` | 纯字符串路径范围匹配，供插件调用 |
| `result.confirmedAt()` | 确认成功时间 |

`approve(code, identity)` 由宿主 `LoginCommand` 在权限校验后使用。插件正常接入只需创建申请、订阅结果和取消申请。

## 路径范围

```java
service.create("/api/*");                     // /api 本身及所有后代
service.create("/api/users/*", "/api/status"); // 一个目录范围 + 一个精确路径
```

- `/api/users/*` 不匹配 `/api/users2`，精确路径不包含其子路径。
- 仅支持精确路径和末尾 `/*`，不支持中间通配、`**` 或根目录 `/*`。
- 范围由插件服务端代码决定，不应直接信任外部调用方传入的授权范围。
- `allows` 接收路径，不接收完整 URL 或查询参数；含编码字符、反斜杠、分号、重复斜杠和 `.`/`..` 路径段返回 false。
- 这只是匹配工具，不会注册、开放或拦截任何 HTTP 接口。插件须自行在实际调用处使用匹配结果。

## 生命周期与回调

- 验证码为随机六位数字，有效期 5 分钟，只能确认一次。重复确认和并发确认不会重复成功。
- 每份申请独立保存回调，成功结果只送到对应申请；返回的 CompletionStage 不允许调用方修改核心 Future。
- 过期以 `TimeoutException` 完成；取消或宿主关闭以 `CancellationException` 完成。按 CompletionStage 的操作方式，异常可能被 CompletionException 包装。
- 每秒自动清理过期请求，不需要插件轮询。耗时回调使用 `thenAcceptAsync` 等异步方法，避免阻塞命令处理或清理线程。
- 插件应保存自己未完成的 requestId，并在请求断开、放弃登录或插件停用时调用 `cancel`。不要对共享服务调用 `close`，其生命周期由宿主管理。
- 确认后的结果是身份及授权范围，不是登录会话；会话有效期、撤销及权限变更处理由插件决定。
- userId 在来源内部才有意义，应与 `identity.senderType()` 一起使用。
- 最多保留 1024 个验证码，包括尚在原有效期内的已使用/取消验证码，以避免立即复用。命令端每个发送者每分钟最多尝试 10 次；对外请求限流由插件实现。
- 仅使用内存，重启后未完成的申请失效。

验证命令：`./gradlew.bat test --tests "top.yzljc.atribot.auth.*"`（JDK 25）。
