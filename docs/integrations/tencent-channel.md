# QQ 频道第二账号

[文档目录](../README.md) · [WebUI 与 Miniapp](../web.md)

该模块通过 `tencent-channel-cli` 操作第二账号，封装位于 [sakuraba_ema](../../src/main/java/top/yzljc/sakuraba_ema)。它与 QQ 官方机器人频道适配器使用不同连接和账号

## 配置

```yaml
tencent-channel:
  enabled: true
  cli-path: "tencent-channel-cli"
  login-token: ""
  timeout-seconds: 90
```

在宿主运行环境安装并登录 CLI，`cli-path` 填可执行文件或可从 PATH 查找的命令。显式配置的登录 Token 可由环境变量 `TENCENT_CHANNEL_LOGIN_TOKEN` 覆盖

未配置显式 Token 时使用 CLI 自身的登录状态。已配置显式 Token 时，本机重新扫码不会替换该配置，应同步更新对应 Token

## Java 接口

业务通过静态封装传递参数，返回 `ChannelCliResult`：

```java
ChannelCliResult result = ChannelInformation.getJoinedGuilds();
if (result.success()) {
    JsonNode data = result.getData();
}
```

| 封装 | 内容 |
| --- | --- |
| `ChannelInformation` | 频道资料、列表、板块、搜索与分享 |
| `ChannelPosts` | 帖子查询、发布和管理 |
| `ChannelComments` | 评论与回复 |
| `ChannelPrivateChat` | 私信 |
| `ChannelNotices` | 通知 |
| `ChannelMembers` | 成员 |
| `ChannelRoles` | 身份组 |
| `ChannelManagement` | 频道管理 |
| `ChannelSystem` | 系统与账号相关操作 |

`ChannelCliResult` 保留成功状态、退出码、JSON 响应、标准输出、标准错误和调用信息。通过 `getData()`、`getError()` 读取业务结果，并可检查限流或认证过期

[ChannelCliClient](../../src/main/java/top/yzljc/sakuraba_ema/ChannelCliClient.java) 统一管理进程执行与参数传递。新增业务调用优先扩展对应封装，避免业务层自行拼接 shell 命令

## 管理指令

`/ema` 仅向机器人管理员开放，具体参数通过内置帮助查询：

```text
/ema help
/ema help member mute
/ema guild list
/ema guild info <频道ID>
/ema result 2
```

指令覆盖频道、成员、身份组、帖子、评论、通知和私信。操作参数与确认规则以内置帮助和执行器为准

## WebUI 频道页

入口为 `/webui/channels`。页面使用同一 CLI 账号，查询频道、板块、帖子、评论和成员，并提供内容发布及管理操作

| 路由 | 用途 |
| --- | --- |
| `GET /webui/api/channels/query/{operation}` | 查询 |
| `POST /webui/api/channels/action/{operation}` | 写入操作 |

接口沿用 WebUI 会话，写入要求 JSON、`X-Requested-With: XMLHttpRequest` 和 `confirmed: true`。后端按操作重建参数白名单，不接收任意 CLI 命令或本地文件路径

页面实时查询 CLI，不另建频道内容数据库。媒体使用返回的远程 URL。交互调用不自动重试，写入失败后需先核对实际结果再提交

未启用或未配置返回 `503`，限流返回 `429`，超时返回 `504`，CLI 业务失败返回 `502`。CLI 登录过期与 WebUI 登录会话分别处理

接口实现见 [ChannelController](../../src/main/java/top/yzljc/atribot/webui/controller/ChannelController.java)
