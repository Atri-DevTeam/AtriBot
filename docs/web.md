# WebUI、Miniapp 与个人面板

[文档目录](README.md) · [开发与构建](development.md)

## 入口与职责

| 项目 | WebUI | Miniapp |
| --- | --- | --- |
| 面向对象 | 机器人维护者 | 机器人用户与已绑定群的群主 |
| 页面入口 | `/webui/` | `/dashboard/` |
| API 前缀 | `/webui/api` | `/atrimeow/profile/api` |
| 前端目录 | `webui/` | `miniapp/` |
| 后端入口 | `WebUIRouter` | `MiniappRouter` |
| 认证 | QQ 互联会话及统一账号 ADMIN／OWNER 身份 | QQ 互联会话签发页面 Token |

网页统一从主域名进入，根路径临时跳转至 `/dashboard/`。登录后，ADMIN／OWNER 显示个人面板与 WebUI 的入口选择；其他用户直接进入个人面板。未绑定用户先按页面提示完成绑定

WebUI 与个人面板共用 QQ 登录会话，管理权限由后端根据统一账号的当前状态和权限组校验，不接受客户端声明的角色或通配权限节点

## QQ 互联登录

在 QQ 互联中创建网站应用，使用网站应用的 AppID 和 AppKey。此处的凭据与 QQ 机器人的 AppID、Secret 相互独立

在运行目录的 `config.yml` 中添加以下配置，修改后重启服务：

```yaml
miniapp:
  enabled: true

dashboard:
  enabled: true
  base-url: "https://bot.example.com"
  callback-url: "https://api.bot.example.com/auth/qq/callback"
  qq-connect:
    app-id: "网站应用 AppID"
    app-key: "网站应用 AppKey"
```

| 项目 | 地址 |
| --- | --- |
| 页面入口 | `https://bot.example.com/dashboard/` |
| QQ 互联回调地址 | `https://api.bot.example.com/auth/qq/callback` |
| 页面域名下的授权完成接口 | `https://bot.example.com/dashboard/auth/complete` |
| 登录接口前缀 | `/dashboard/api/auth` |
| 页面资源及业务接口 | `/atrimeow/profile/` |

将示例域名替换为实际域名。`base-url` 填写页面域名，`callback-url` 填写与 QQ 互联后台登记一致的完整回调 URL，路径固定为 `/auth/qq/callback`，不带尾斜杠或查询参数。`callback-url` 留空时使用页面域名下的 `/auth/qq/callback`

两个域名的上述路径均需转发至同一个机器人服务实例，保留原始路径；登录接口不得缓存。独立回调域名收到授权结果后，以 302 跳转至页面域名的 `/dashboard/auth/complete`，在此校验发起授权的浏览器 Cookie、交换授权码并签发登录 Cookie，最后返回 `/dashboard/`。无需设置跨子域 Cookie

### 绑定与登录

1. 用户在 QQ 机器人私聊中发送 `/dash` 或 `/dashboard`
2. 机器人返回带 `userOpenId` 和一次性 `ticket` 的入口链接，同时携带 `_wwv=9983&qq=1`
3. 带 `qq=1` 的入口自动跳转 QQ 互联授权，其他入口点击「QQ 登录并绑定」后跳转
4. 服务端验证授权结果后建立绑定，返回 `/dashboard/`
5. 后续可直接访问 `/dashboard/`，使用同一 QQ 账号登录

入口票据有效期为 5 分钟，在发起授权时消费，仅可使用一次。页面读取票据后从地址栏移除；QQ 授权状态由服务端随机生成，有效期 10 分钟，并校验发起授权时的浏览器 Cookie。`_nav`、`_wwv` 和 `qq=1` 会在授权完成后保留，不能用于指定跳转地址

`qq=1` 仅用于 QQ 网页适配：标题使用零宽空白字符，未登录时自动发起授权。取消或失败后保留手动重试入口。未携带此参数时显示正常标题，不自动发起授权；该参数不参与身份认证和权限判断

QQ 互联 OpenID 与机器人 `userOpenId` 属于不同应用范围，不直接比较。绑定写入现有 `unified_account` 统一账号表：`qq_user_open_id` 保存官机用户标识，`qq_connect_open_id` 保存 QQ 互联 OpenID。绑定时优先使用已有统一账号，没有账号时自动创建，保留原有 UUID、权限及其他账号关联

一个统一账号绑定一个 QQ 互联身份，同一 OpenID 不能绑定多个统一账号。重复确认相同绑定允许通过，其他冲突不覆盖原记录。现有数据库启动时补充 `qq_connect_open_id` 字段及唯一索引，无需新增绑定表。AppID 和 AppKey 只在网站配置中填写

未绑定的 QQ 账号只能查看绑定指引。用户 ID 不作为独立登录凭据，个人资料与群配置均从已验证会话读取身份；群主身份、`group.moderation` 和自定义审查词权限仍由原业务接口检查

### 会话

登录 Cookie 使用 HttpOnly、SameSite=Lax 和 HTTPS Secure 属性，最长有效期 12 小时。页面通过同源 POST 接口领取内存 Bearer Token，复用 Miniapp 的 2 小时页面会话及 90 秒空闲限制。个人面板右上角“退出登录”会注销 QQ 登录，同步使该登录签发的页面会话失效，并返回统一登录页；退出后刷新页面也不会自动发起 QQ 授权。登录页及工作台选择页的“切换账号”会先注销当前登录，再进入 QQ 授权；外站授权页切换账号时保留当前外站认证请求。

重启服务后需要重新登录，数据库中的绑定关系保留。AppKey 仅在服务端使用，前端不持有 QQ access_token，也不将登录凭证写入 localStorage 或 sessionStorage

### 本地验证

在 `miniapp/` 执行 `npm run dev`，通过 `/dashboard/` 访问个人中心。开发服务器将登录、回调和 Miniapp API 转发至 `VITE_API_TARGET`，默认 `http://127.0.0.1:1234`。本地页面会话测试要求 `dashboard.base-url` 与浏览器访问地址一致；真实 QQ 授权使用互联后台登记的公开域名与回调地址

授权流程参考 [QQ 互联 Authorization Code](https://wiki.connect.qq.com/使用authorization_code获取access_token) 与 [获取用户 OpenID](https://wiki.connect.qq.com/获取用户openid_oauth2-0)。登录按钮使用 [QQ 互联官方标识](https://wiki.connect.qq.com/wp-content/uploads/2016/12/Connect_logo_1.png)

## WebUI

其他站点复用 QQ 登录的接入方式见[站点授权](integrations/site-authorization.md)。该流程通过独立签名请求和一次性授权码交接身份，不共享 WebUI 或个人中心的页面凭证

WebUI 使用 QQ 互联登录，不再读取 `qq.official-webui-token`，也不接受旧密钥会话。生产配置默认不自动开启 WebUI，机器人管理员可通过 `/webui` 操作开关；控制台执行 `webui` 切换状态。`env: dev` 在启动时自动开启

QQ 登录会话有效期为 12 小时，Cookie 仅在页面域名签发。每次管理请求校验登录状态、当前 QQ 绑定、账号状态及 ADMIN／OWNER 权限组；写请求校验页面 Origin。实时连接在发送数据前重新鉴权。退出登录、账号受限或解绑后会话失效，权限降级后不能继续管理

WebUI 关闭时管理接口不可用并断开实时连接，个人面板及 QQ 登录状态保留。导航底部的账号框显示当前用户及身份，可进入个人档案、返回工作台、刷新页面或退出登录

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

原 `/webui/api/public/official` 下的 GET 统计接口已移除，POST `/ntuid` 工具接口保留路径，但与其他 WebUI API 一样要求有效的 ADMIN 或 OWNER 登录会话，WebUI 关闭时不可访问。WebUI 统计页使用私有的 `/webui/api/stats/overview` 和 `/webui/api/stats/series`，保留日期筛选

用户、群详情仅供 WebUI 使用，接口分别为 `/webui/api/stats/users/{userOpenId}` 和 `/webui/api/stats/groups/{groupOpenId}`，要求后台已开启且登录会话有效。原公开路径不再提供这两个接口

`GET /openapi/bot_stats` 为公开接口，无需鉴权，不受 WebUI 开关影响。无参数返回消息、小游戏、卡片、签到及在档数量统计，每项排行榜最多返回 100 条及对应上榜用户成绩，个人中心可查看自己的全量名次。响应与统计口径见[机器人统计接口](integrations/bot-stats.md)

## Miniapp

```yaml
miniapp:
  enabled: true
```

个人面板统一通过 `/dashboard/` 进入，外部地址填写在 `dashboard.base-url`。官机私聊中执行 `/dash` 或 `/dashboard` 获取 QQ 互联绑定链接

通过 QQ 登录后，由服务端签发页面会话。Bearer Token 仅保存在前端内存，不写入 localStorage 或 Cookie

会话最长 2 小时，空闲 90 秒失效，前端通过心跳维持活动。进程重启后页面会话失效。服务端从会话取得身份，不接受请求中的用户 ID 作为授权依据

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

## 工单管理

用户发送 `/feedback <内容>` 提交工单；同一平台身份已有未关闭工单时，内容追加到最近创建的未关闭工单。QQ 群聊与私聊共用用户身份，QQ 频道与频道私信单独匹配

收到新的补充后，工单恢复为待处理，已有回复及操作记录保留；管理员再次回复后自动转为等待用户回复。回复时可勾选“同时关闭工单”，将回复与关闭操作一并保存，并记录关闭时间及操作者；默认不勾选，可与静默回复或私聊回复搭配使用

已开放主动消息的 QQ 群优先主动发送工单回复，失败后加入被动队列。未开放主动消息的群及私聊使用对应提交或补充消息的 `msg_id`：来源消息未满五分钟时立即被动回复，发送失败则加入被动队列。超过五分钟、来源时间无效或旧记录缺少消息 ID 时，沿用主动通知及失败入队流程。静默回复不投递。消息 ID 与平台消息时间保存在 `feedback_entries`，回复使用当时的来源记录，后续补充不会改变该回复的发送目标

回复窗口提供“私聊回复”选项，用户在机器人好友列表内且开启私聊主动消息权限时可选，否则置灰。仅 QQ 群聊和私聊来源支持此选项，提交时后端重新检查资格；不可与静默回复同时选择。私聊回复只改变该条回复的投递目标，工单来源保持不变；发送失败后仅在私聊补发

工单通知由 `FeedbackCommand.getReplyMessage` 统一生成 `Markdown` 对象，可通过 `setKeyboard` 附加内联键盘。队列保留工单及回复记录编号，补发时从对应回复重新构建消息；QQ 群聊和私聊直接发送完整对象，群内追加 @ 不会丢弃键盘。频道沿用纯文本回复

| 状态 | 含义 |
| --- | --- |
| `PENDING` | 已提交，等待管理员处理 |
| `PROCESSING` | 管理员已回复，工单仍可补充和继续回复 |
| `WAITING_USER` | 管理员提交回复后自动进入此状态，用户补充后转回待处理 |
| `CLOSED` | 用户或管理员手动关闭，重新打开后可继续回复 |

用户使用 `/feedback -c <工单编号>` 关闭本人工单，支持完整编号或至少 8 位的唯一前缀。管理员在 WebUI 中关闭工单。关闭后再次提交会创建新工单；如果该用户有其他历史未关闭工单，则追加到其中最近创建的一条

管理员可在 WebUI 重新打开已关闭工单：未回复过的恢复为待处理，已有回复的恢复为等待用户回复。同一平台身份已有其他未关闭工单时，不允许重新打开。重新打开会清除当前关闭时间，保留历史关闭记录，并记录本次操作者；不向用户发送通知

回复记录保存当前登录管理员的统一账号 UUID 和名称快照，仅管理端可见，不写入发给用户的通知。回复通知始终不附带用户提交或补充的原文，原文仅在管理端保留。回复成功保存后默认转为等待用户回复，勾选“同时关闭工单”则转为已关闭。静默回复保存记录，但不发送通知，也不在后续消息中补发

### 管理接口

以下接口均位于 `/webui/api`，沿用 WebUI 的 QQ 登录会话、管理员身份、服务开关及写请求来源校验，响应使用统一 `Result` 格式

| 方法 | 路径 | 参数及响应 |
| --- | --- | --- |
| GET | `/feedback/list` | `filter=pending\|processing\|waiting\|closed\|all`，默认 pending；`page` 从 1 开始，`pageSize` 默认 20、最多 100；返回分页工单及状态、最新补充、最近回复人 |
| GET | `/feedback/count` | 返回 `pending`、`processing`、`waiting`、`closed`、`all` 数量 |
| GET | `/feedback/{id}/entries` | 返回倒序记录 `items` 和 `nextCursor`，每次最多 50 条；下一次传 `before=nextCursor` 读取更早记录；`canDelete` 表示回复尚未送达、允许申请删除，删除时后端重新检查 |
| DELETE | `/feedback/{id}/entries/{entryId}` | 删除尚未送达的非静默回复并取消队列补发；仅管理员可操作，回复不存在、正在发送或已经送达时返回 HTTP 409 |
| POST | `/feedback/reply` | JSON：`id`、`replyContent`、`silent`、`privateReply`、`closeTicket`（布尔选项默认 false）；`closeTicket=true` 同时关闭工单，否则转为等待用户回复；内容最多 4000 字符，操作者由服务端会话确定；已关闭工单返回 HTTP 409，私聊回复资格不满足或同时指定静默回复返回 HTTP 400 |
| POST | `/feedback/close` | JSON：`id`；重复关闭成功，工单不存在返回 HTTP 404 |
| POST | `/feedback/reopen` | JSON：`id`；要求 ADMIN 或 OWNER 会话，操作者由服务端确定；重复打开成功，工单不存在返回 HTTP 404，已有其他未关闭工单返回 HTTP 409 |

记录类型为 `SUBMIT`（提交）、`APPEND`（补充）、`REPLY`（管理员回复）、`REPLY_DELETED`（已删除回复）、`ADMIN_DELETE`（删除回复操作）、`USER_CLOSE`（用户关闭）、`ADMIN_CLOSE`（管理员关闭）、`ADMIN_REOPEN`（管理员重新打开）。记录包含操作者标识及名称、内容、时间及静默选项；记录 ID 和翻页游标以字符串返回

删除回复会清除该条回复正文和对应待补发通知，并记录删除操作者。工单摘要恢复到上一条有效回复；没有有效回复时恢复待处理，最新有效记录为用户补充时也保持待处理。已关闭工单保持关闭，不会因删除回复自动重新打开

工单记录中的用户头像按平台用户标识生成；WebUI 管理员头像取自服务端登录会话，随回复、关闭及重新打开操作保存到 `feedback_entries.actor_avatar_url`。历史记录没有头像时显示名称首字，不使用当前查看者的头像替代

启动时为 `feedback` 增加状态、更新时间和关闭时间，并创建 `feedback_entries` 保存追加记录。旧的未回复记录迁为待处理，已回复记录迁为处理中，历史回复人的身份留空。迁移保留原始内容及回复，不自动关闭历史工单，可重复启动
