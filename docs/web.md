# WebUI 与 Miniapp

[文档目录](README.md) · [开发与构建](development.md)

## 入口与职责

| 项目 | WebUI | Miniapp |
| --- | --- | --- |
| 面向对象 | 机器人维护者 | 机器人用户与已绑定群的群主 |
| 页面入口 | `/webui/` | `/atrimeow/profile/` |
| API 前缀 | `/webui/api` | `/atrimeow/profile/api` |
| 前端目录 | `webui/` | `miniapp/` |
| 后端入口 | `WebUIRouter` | `MiniappRouter` |
| 认证 | 管理密钥验证后签发 Cookie 会话 | 入口票据交换内存 Bearer Token |

两套认证独立。页面隐藏控件不能代替后端授权

## WebUI

在 `qq.official-webui-token` 配置登录密钥。生产配置默认不自动开启 WebUI，机器人管理员可通过 `/webui` 操作开关；控制台执行 `webui` 切换状态。`env: dev` 在启动时自动开启

登录流程使用挑战值与证明值验证密钥，成功后签发会话 Cookie。会话固定有效期为 24 小时。关闭或重新开启 WebUI 会清除原认证状态并断开旧连接

主要页面包括聊天与群配置、指令面板、用户和权限、发送及事件记录、频道管理。群管理和加群欢迎从聊天侧栏进入

### 接口调试

调试页依据 [官方 autogen 目录](https://bot.q.qq.com/wiki/develop/api-v2/autogen/) 及[频道文档](https://bot.q.qq.com/wiki/develop/api-v2/server-inter/channel/)整理，核对日期为 2026-10-13。除 autogen 外，另读取文档站路由中 121 个页面，按请求方法与路径去重，共收录 96 个 HTTP 接口，其中 2 个已废弃。预设包含请求方法、路径、参数样例及对应文档链接，另提供 59 个操作变体和原有消息进阶样例

| 分类 | 内容 | 接口数 |
| --- | --- | --- |
| 接入与机器人 | 访问凭证、机器人资料、频道列表、WSS 网关及分片网关、分享链接 | 6 |
| 群聊与单聊消息 | 发送、撤回、流式消息、富媒体上传 | 7 |
| 分片上传 | 预上传、分片完成通知 | 4 |
| 群管理与自动审批 | 群资料、成员、加群申请、禁言、黑名单、审核策略与白名单 | 17 |
| 菜单、面板与交互 | 自定义菜单、指令面板、交互回应 | 9 |
| 频道管理 | 频道资料、子频道列表及增删改查 | 6 |
| 频道消息与私信 | 发送、撤回、创建私信会话 | 5 |
| 频道成员与身份组 | 成员查询与移除、在线人数、身份组管理及成员分配 | 11 |
| 频道权限 | 用户与身份组的子频道权限、接口权限及授权链接 | 6 |
| 频道禁言与设置 | 全员禁言、批量及指定成员禁言、消息频率设置 | 3 |
| 频道公告与精华 | 频道公告、精华消息 | 5 |
| 频道日程 | 列表、详情、创建、修改、删除 | 5 |
| 频道音频 | 播放控制、上麦、下麦 | 3 |
| 频道帖子 | 列表、详情、发表、删除，包含 RichText 请求样例 | 4 |
| 频道表情表态 | 添加、删除、参与用户列表 | 3 |
| 已废弃接口 | 创建与删除子频道公告，替代接口为精华消息 | 2 |

同一路径下的多种操作作为请求样例提供。例如全员禁言与批量成员禁言共用一个接口，通过 `user_ids` 区分；频道消息提供文本、图片 URL、Markdown、引用和 Embed 样例。事件、数据模型及小程序客户端 SDK 不列入 HTTP 调试接口

路径中的 `{参数名}` 由页面输入框替换。Headers 和 Body 保持原文，需手动填写实际 ID、校验值、文件数据及 RFC3339 时间。选择预设或切换样例不会发送请求

请求通过已登录的 WebUI 代理发送，使用当前机器人的凭据。获取访问凭证是例外：固定向 `api.bot.qq.com/app/getAppAccessToken` 发送 POST 请求，不附加 Authorization，需手动填写 AppID 与 Secret。平台标为内邀、私域或音频专用的接口也保留预设，调用结果取决于机器人实际权限

分片数据的预签名 URL 上传需要另行执行，调试页提供预上传、分片完成通知与文件合并样例。频道图片样例使用 URL，调试页不提供 multipart 文件上传控件

接口覆盖清单保存在 `webui/tests/fixtures/officialApiInventory.json`，可在 `webui/` 运行 `node --test tests/officialApiPresets.test.js` 校验

### 聊天加载与同步

会话列表首批加载 40 条，滚动继续加载。列表接口只返回短预览，聊天正文、图片及附件仍由消息接口完整返回。搜索在后端匹配会话名称、标识及最后一条消息的预览内容，不受首屏条数和预览截断影响

`webui_chat_conversation` 保存会话类型、标识、最新消息记录 ID、排序时间和最近已知用户名，不复制消息正文或媒体资源。首次启动时从现有消息表建立摘要，后续消息写入和记录清理在同一事务内更新摘要。首次初始化耗时取决于已有记录量

群头像仅在接近可见区域时加载，独立接口最多返回 9 个成员 ID。服务端缓存最多 256 个群，5 分钟过期；侧栏成员列表仍使用完整成员接口

实时通知触发增量查询，新增消息和已有消息的引用补写均参与同步。历史记录按消息 ID 向前翻页。变更记录在内存中最多保留 4096 条，断线重连使用游标补齐；进程重启、游标过期或记录清理后重新读取页面数据

### 统计接口

原 `/webui/api/public/official` 下的 GET 统计接口已移除，POST `/ntuid` 工具接口保留。WebUI 统计页使用私有的 `/webui/api/stats/overview` 和 `/webui/api/stats/series`，保留日期筛选

用户、群详情仅供 WebUI 使用，接口分别为 `/webui/api/stats/users/{userOpenId}` 和 `/webui/api/stats/groups/{groupOpenId}`，要求后台已开启且登录会话有效。原公开路径不再提供这两个接口

`GET /openapi/bot_stats` 为公开接口，无需鉴权，不受 WebUI 开关影响。无参数返回消息、小游戏、卡片、签到、图源及在档数量统计，包含完整个人成绩与排行榜。响应与统计口径见[机器人统计接口](integrations/bot-stats.md)

## Miniapp

```yaml
miniapp:
  enabled: true
  base-url: "https://bot.example.com"
```

`base-url` 可填写外部根地址或完整页面地址，外部地址要求 HTTPS，本机调试允许 localhost / 127.0.0.1 使用 HTTP。留空时不签发页面入口。用户在 QQ 官方 C2C 中执行 `/profile` 获取绑定本人身份的链接

入口票据有效期 5 分钟。GET 页面只加载外壳，POST 交换票据后取得会话，交换绑定 IP 与 User-Agent。Bearer Token 仅保存在前端内存，不写入 localStorage 或 Cookie

会话最长 2 小时，空闲 90 秒失效，前端通过心跳维持活动。进程重启后票据与会话全部失效。服务端从会话取得身份，不接受请求中的用户 ID 作为授权依据

### 群管理

群配置以已完成的群主绑定为归属依据。显示和访问群管区域均要求 `group.moderation`，自定义 AI 审查词的编辑还要求 `atri.custom_prompt`

共有配置包括关键词、审查模式、URL 正则白名单、提醒开关及生效时间。WebUI 专有高级设置在 Miniapp 只读，提交时由后端保护原值

关键词保存后仅显示首字，其余字符遮盖；URL 正则保留首尾字符，其余等长遮盖。已有内容通过规则 ID 操作，展示文本不能作为原始规则回写。URL 默认全放行使用 `.*`

Miniapp 新增规则使用固定提醒词；WebUI 已配置的提醒内容和自定义输出作为只读字段保留。Miniapp 不提供自定义输出编辑，具体规则见 [MiniappModerationService](../src/main/java/top/yzljc/atribot/miniapp/service/MiniappModerationService.java)

## 构建与部署

```sh
npm --prefix webui ci
npm --prefix webui run build
npm --prefix miniapp ci
npm --prefix miniapp run build
./gradlew build
```

构建输出分别进入 `src/main/resources/official-webui` 和 `src/main/resources/miniapp`，随 JAR 提供静态资源。修改 Vite 的部署路径时，必须同时核对后端路由与反向代理路径

源码入口：[WebUI](../webui/src)、[Miniapp](../miniapp/src)、[WebUISessionManager](../src/main/java/top/yzljc/atribot/webui/WebUISessionManager.java)、[MiniappRouter](../src/main/java/top/yzljc/atribot/miniapp/MiniappRouter.java)、[MiniappSessions](../src/main/java/top/yzljc/atribot/miniapp/MiniappSessions.java)
