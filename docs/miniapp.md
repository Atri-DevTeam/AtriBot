# Miniapp 个人空间

前端位于 `miniapp/`，后端位于 `top.yzljc.atribot.miniapp`，构建资源位于 `src/main/resources/miniapp/`。页面使用 `#F4F5F0` 背景、半透明毛玻璃卡片和手机/桌面响应式布局，提供个人档案与机器人记录概览。未引入 WASM。

首次进入展示 `atri-main.png` 插画的轻浮动加载动画，在后台并行读取资料与小游戏记录；就绪后按顶栏、头像、卡片顺序淡入。插画复用现有源文件，由 Vite 打包至 `/atrimeow/profile/assets/`，仅反代 `/atrimeow/profile` 即可访问。快速响应时保留约 650ms 的过渡，认证成功后最长等待约 2.8 秒，远端背包图片不阻塞首屏。普通更新不重播动画，退出会清除延迟任务；系统开启“减少动态效果”时关闭位移和循环动画。

底部悬浮 Dock 左侧为“群管理”，右侧为“我的档案”，默认打开档案。图标复用 atriwebsite 的红石中继器与纸张资源。切换栏目保留会话、档案数据、背包缓存及滚动位置。群管理首页为单列群列表，仅显示群名、群号、添加时间；点击后读取该群详情，可返回列表或解除绑定。绑定信息保存在 official_users.user_settings.bound_groups，以群开放平台 ID 为键，值中保存 group_name、group_number、bound_at；无需新表或新列。

## 档案数据

登录后使用页面会话请求 `GET /atrimeow/profile/api/profile`；用户身份来自服务器会话，URL 中另传 `userId` 不会改变查询对象。接口只读，不创建账号、不改动机器人记录。

- 头像使用项目已有的 QQ 官方头像地址格式，加载失败显示默认头像。
- 用户名优先读取 `unified_account.username`，缺失时查询该用户最新的已知群聊/私聊用户名；没有记录时明确显示“暂无记录”。
- 消息数量统计 `official_c2c_record`、`official_group_record` 中该用户的消息，排除机器人发送记录，并展示各自最近记录时间。
- 签到来自 `check_in_total`；金粒余额来自 `user_loots`；卡片总量和种类来自 `user_loot_items`。
- 基础资料展示已有的统一账号 ID、绑定 QQ 号（`unified_account.qq_user_uin`）、创建日期和 Minecraft 绑定 UUID；QQ 号未绑定时显示“未绑定”。
- `unavailable` 标识查询失败的分区，和“没有记录”区分；页面支持重试。没有记录的签到/余额返回 null，实际零余额返回 0，不用虚构数据填充。
- 浏览器预览截图使用测试数据和仓库现有插画作为测试头像，不表示线上用户的实际记录。

## 开启

在运行目录的 `config.yml` 加入：

```yaml
miniapp:
  enabled: true
  base-url: "https://bot.example.com"
```

`base-url` 使用公网 HTTPS 地址，允许包含页面路径，不含查询参数、片段或用户名密码。仅填域名（或根路径 `/`）时默认进入 `/atrimeow/profile/`；配置 `https://bot.example.com/atrimeow/profile/` 或其他完整页面路径时原样保留路径，不重复拼接 `/atrimeow/profile/`，已有路径中的百分号编码也会保留。本地开发允许 `http://localhost:1234` 或 `http://127.0.0.1:1234`。未配置时指令不会签发链接。反向代理将页面请求转发至现有 Javalin 端口；与管理后台开关及登录系统独立。

官方机器人 **C2C 私聊**发送 `/profile` 获取链接：

```text
https://bot.example.com/atrimeow/profile/?_nav_alpha=0&userId=官方用户ID&ticket=一次性凭证
```

`_nav_alpha=0` 按约定保留，具体 QQ 客户端展示效果需真机确认。其他来源（官机群聊、Napcat、Discord、控制台等）不能签发。没有额外 QQ 身份验证：首次持有完整有效链接的人可以兑换。

## 生命周期

- 凭证使用 256 位安全随机数，与官机事件的用户 ID 绑定，有效期 5 分钟。
- 每位用户 10 秒内仅可申请一次；重新申请会替换该用户尚未使用的链接，不关闭已经进入的页面。
- 普通 GET / HEAD 只加载无用户数据的外壳，不消费凭证。前端 POST `/atrimeow/profile/api/auth/exchange` 原子兑换；成功后链接立即失效，并发仅一个请求成功。
- 链接预览只做 GET 时不会误消费；执行页面 JavaScript 的扫描器仍可能兑换。兑换结果丢失或加载失败时，需重新获取链接，不恢复旧凭证。
- 前端提取参数后立即从地址栏移除 `userId`、`ticket`，保留 `_nav_alpha`。源站和反代访问日志须避免记录此入口的查询参数；HTTPS 不能阻止服务端访问日志记录 URL。
- 独立页面会话凭证仅在 JS 私有内存中保存。无 Cookie、localStorage、sessionStorage。接口通过 Bearer 会话确定用户，不能通过请求参数切换用户。
- 心跳间隔 20 秒，90 秒无活动失效，最长 2 小时。退出、刷新、离开页面、前进后退恢复缓存均失效；页面内部组件切换可继续使用。
- 切后台和锁屏不立即退出，但浏览器挂起心跳超过空闲期限后必须重新进入。
- `pagehide` 清理内存和界面并尝试 Beacon 撤销；失败、强杀、断网由空闲超时兜底。无法保证服务器在客户端退出瞬间就收到通知。
- 重启会撤销全部凭证和会话；单进程部署，若未来多实例需要共享存储和原子消费。

## 接口

| 方法与路径 | 用途 |
| --- | --- |
| `POST /atrimeow/profile/api/auth/exchange` | JSON `{userId,ticket}` 兑换，返回 `{token,user,expiresAt,idleTimeoutMillis}` |
| `GET /atrimeow/profile/api/me` | Bearer 会话对应的最小身份信息 |
| `GET /atrimeow/profile/api/profile` | Bearer 会话对应的只读档案与消息、签到、金粒、收藏概览 |
| `POST /atrimeow/profile/api/session/heartbeat` | Bearer 会话保活，过期返回 401 |
| `POST /atrimeow/profile/api/session/close` | Bearer 或 Beacon text/plain 凭证，仅撤销会话，幂等 |

后续私有接口应调用 `MiniappRouter.requireIdentity`，使用返回身份限定数据范围。未知接口和资源返回 404，不回退为 HTML。当前仅有 `/atrimeow/profile/` 单页入口；以后增加页面路由时再扩展明确的页面入口。

## 开发和构建

```powershell
cd miniapp
npm ci
npm test
npm run build
cd ..
.\gradlew.bat test --tests "top.yzljc.atribot.miniapp.*"
.\gradlew.bat build
```

使用 Node.js 24、JDK 25。`npm run dev` 启动 5174 端口并代理 API 至 `localhost:1234`，可用 `VITE_API_TARGET` 调整。开发时在私聊获取链接，将源站替换为本地 Vite 地址并保留参数；不提供绕过私聊签发的开发接口。

`npm run test:browser` 使用本机 Edge，测试手机尺寸和桌面尺寸（并非真实 QQ WebView）。可通过 `MINIAPP_BROWSER_CHANNEL` 改为 `chrome`。测试使用模拟接口，不连接真实 QQ。真实 Java HTTP 路由和原子兑换由 JUnit 验证。Java 测试放在 `src/miniappTest/java`，由 Gradle 的 test 源集加载，避免本仓库本地 `/src/test/` 忽略规则遗漏这些回归测试。

前端输出独立目录，随 JAR 打包。Gradle 对 miniapp 静态资源跳过模板展开，后续添加 `.wasm` 也按原字节复制。CI 构建两个前端并运行 miniapp 单元测试和 Java 鉴权测试。

## 小游戏、背包与活动明细

页面新增以下只读接口，均要求独立页面会话，服务端只使用会话用户 ID：

| 接口 | 内容 |
| --- | --- |
| `GET /atrimeow/profile/api/activity` | 六种小游戏数据、今日免费抽卡状态和已解析的用户设置 |
| `GET /atrimeow/profile/api/inventory?offset=0` | 每页 12 种已有物品卡，数量、首次/最近获取日期和记录来源 |
| `GET /atrimeow/profile/api/inventory/image` | 当前用户的远端背包总览图，返回图片二进制 |
| `GET /atrimeow/profile/api/gains?offset=0` | 每页 8 条已保存的金粒来源记录 |

- 六种小游戏统一读取 `official_users.game_data`。反应力使用 `reaction` 对象，`wins` 沿用旧文件语义，表示完成次数；排名按最好用时并列计算，只返回当前用户成绩与排名。
- 扫雷按有效挖格、插旗、拔旗计操作；听声辨物按有效作答计操作和正确数；四子棋、幸运轮盘、石头剪刀布按已结算对局计场次和胜场。平局计场次不计胜场，轮盘幸存玩家计胜，未开局或未完成就取消的对局不计场次。四子棋落子超时按既有规则计胜负，整局超时按平局统计。
- 免费抽卡读取 `data/loot-free-draws.json`，按现有北京时间日期和 23:50—00:00 结算窗口展示状态；只查询，不领取机会、不抽卡。
- `check_in_daily` 是每日清空的日表，不能据此推断历史签到；页面不展示日历，也不据此累计奖励。签到天数和最近日期仅来自 `check_in_total`。
- 背包放在资料内容最下方，点击图片进入全屏浏览，支持手机双指缩放、拖动，桌面滚轮缩放、拖动，以及缩放按钮和适应屏幕。浏览器内复用当前会话的图片缓存，关闭大图返回档案页。
- 背包区域复用 `LootService.renderOverviewCard` 调用远端物品卡总览渲染，后端立即读取临时图片并通过已鉴权的 `/atrimeow/profile/api/inventory/image` 返回。该链路只读取已有背包及排名，不抽卡、不扣款、不发放库存；不将渲染密钥或临时图片地址返回前端。原有分页明细接口继续可用。
- 前端仅在当前 `PageSession` 内缓存图片 Blob URL；并发请求合并，普通“更新记录”复用图片，背包“更新”按钮重新渲染。成功更新时释放旧图片；失败保留原图并提供重试。退出时中止下载、释放 Blob URL，晚到的响应不会重新创建缓存。不使用 localStorage、sessionStorage、IndexedDB 或持久 HTTP 缓存。
- 非正常退出时，页面/WebView 被销毁会由浏览器或系统回收内存。仅后台冻结时内存可能暂时保留，恢复后通过会话超时检查释放；服务端会话在 90 秒无活动后失效，并每 30 秒清理过期项，最长会话为 2 小时。远端临时文件沿用渲染服务自己的过期清理。
- 页面已移除金粒来源区域，也不再请求来源接口。现有 `/gains` 接口保留。
- 明细页通过 `offset` 翻页并返回 `hasMore`；参数范围为 0—1000000，SQL 参数绑定。页面会话结束后组件卸载，晚到的数据也由会话层丢弃。
- 免费抽卡文件缺失表示无记录；游戏 JSON、文件损坏或查询失败标记对应区块不可用。页面可局部重试。

### 小游戏统计存储

启动时由 `C2CRepository.initTable()` 为 `official_users` 新增 `game_data JSON NULL` 列，已存在则跳过。每个游戏拥有独立对象，字段可自由扩展，例如：

```json
{
  "minesweeper": { "operations": 11 },
  "sound": { "operations": 10, "correct": 7 },
  "connect4": { "plays": 5, "wins": 3 },
  "reaction": { "plays": 12, "wins": 9, "bestMs": 18240, "bestMisses": 1, "bestDate": "2026-09-19" }
}
```

`OfficialUsers.getGameData(userId, game)` 读取单个游戏；`OfficialUsers.updateGameData(userId, game, eventId, node -> ...)` 在事务中修改其 JSON 对象。底层由 `UserGameDataRepository` 负责持久化、事件去重和旧数据迁移。修改器可添加数字、字符串、对象、数组等任意字段，必须只修改节点，不执行发消息等外部操作，因为失败时可能重试。新增游戏不必修改表结构或统计枚举。

写入通过用户行锁串行化，保留其他游戏和未知字段；多位参与者在同一事务提交。保留 `user_game_stat_events` 仅用于唯一事件去重，不保存游戏指标。数据库瞬时失败重试一次，再失败记录日志。旧的 `user_game_stats` 不再创建或读写，也不会自动删除已有表。

一次性反应力迁移函数为 `OfficialUsers.migrateLegacyReactionData()`，已接入初始化，读取原 `data/click-train-record.json`，源文件保留不动。以每位用户的 `legacy-click-train-v1` 事件标识防止重复导入；历史场次与新记录相加，最好用时取更小的有效值，其失误数和日期一起保留。失败用户可再次调用此函数补齐，已成功用户会跳过；导入结果输出成功、跳过、失败数量。根 JSON 损坏会停止启动，不会当成空记录覆盖；单个用户记录损坏则记失败并继续导入其他用户。之后的反应力游戏、排名和档案页都只读写数据库。

新增五种小游戏在接入记录前的历史无法回填；反应力旧数据按上述函数导入。网页不修改统计或用户设置，不展示其他用户的明细。

用户设置读取 `official_users.user_settings`，解析 B站 UID、Hypixel 自动领奖开关/模式/奖励优先级、已有分享链接/创建日期/邀请人数/是否由他人邀请。只返回已知展示字段；邀请列表转为人数，不返回其中的其他 OpenID；链接仅允许无账号密码的 HTTPS 地址。缺少设置时沿用现有自动领奖默认值（关闭、稀有度优先）。

### 仅代理 /atrimeow/profile

miniapp 的环境配置独立于其他前端：

- `miniapp/.env.development`：`VITE_BASE=/atrimeow/profile/`、`VITE_API_BASE=/atrimeow/profile/api`、`VITE_API_TARGET=http://127.0.0.1:1234`。最后一项仅供 Vite 开发代理，按机器人实际监听地址修改。
- `miniapp/.env.production`：保留 `VITE_BASE=/atrimeow/profile/` 和 `VITE_API_BASE=/atrimeow/profile/api`，浏览器自动使用页面当前域名。修改后执行 `npm run build`，再重新打包 JAR。
- 本地覆盖写入 `miniapp/.env.development.local` 或 `miniapp/.env.production.local`，不会提交仓库。前端环境变量不应放密码或密钥。
- 将 `miniapp/deploy/nginx-profile.conf` 中的两个 location 放入已有 HTTPS server 中。它只转发 `/atrimeow/profile` 与 `/atrimeow/profile/...`，包括静态资源和 API，不代理其他路径。按实际情况修改上游 `127.0.0.1:1234`。
- 机器人运行目录的 `config.yml` 中，`miniapp.base-url` 填入该反代的公网地址，如 `https://bot.example.com`（默认 `/atrimeow/profile/`），也可以直接填写 `https://bot.example.com/atrimeow/profile/` 或其他页面路径。它负责生成登录链接；自定义外部路径时还需匹配反代映射及前端 `VITE_BASE`、`VITE_API_BASE`。前端的 .env 由 Vite 读取，Java 不读取这些前端 .env 文件。
- 转发时保留完整路径、查询参数和 Authorization；一次性兑换不缓存、不向其他后端自动重试。票据和会话保存在机器人进程内，这些路径须转发到同一实例。

## 群绑定

- 群管理填写机器人已记录的群号或群开放平台 ID（群内 `/whoami` 可查），生成 `/群绑定 <验证码>` 指令。验证码有效期 10 分钟，只能由发起请求的同一用户在目标官机群、以群主身份完成验证，管理员或其他群的验证码无效。成功保存后才消费验证码。
- `GET /atrimeow/profile/api/groups` 仅返回当前页面会话用户的绑定群与待验证请求；`POST /atrimeow/profile/api/groups/binding` 接收 `{ "groupId": "群号或群ID" }`，只生成验证请求，不直接保存绑定。群主身份只接受官方群消息事件，不接受网页参数。
- 验证绑定时从 official_groups 将群名、群号与绑定时间一起写入 user_settings。列表只读用户设置，不遍历群表或调用 QQ API；不提供旧结构的数据迁移或读取回填。
- `GET /atrimeow/profile/api/groups/{groupId}` 先检查该群属于会话用户的绑定，再查询单个群的详情，包括人数、分类、标签、简介、机器人身份、消息接收范围、主动消息许可与机器人入群时间。群名和群号采用绑定时保存的值；缺失群记录仍保留这两个字段，并显示资料不可用。
- 群详情下方展示加群欢迎。`GET /atrimeow/profile/api/groups/{groupId}/join-welcome` 校验当前会话用户的群绑定，读取已保存的欢迎正文、按钮外观与启用状态；没有自定义时展示普通新成员的系统默认欢迎。复用 WebUI 的 Markdown 渲染器，支持图片、公式与 QQ 按钮行；该预览没有编辑、保存、开关或发送测试功能，按钮和正文链接不触发动作，接口也不返回按钮指令或权限名单。渲染组件进入群详情后才加载。
- `POST /atrimeow/profile/api/groups/unbinding` 接收 `{ "groupId": "群ID" }`，通过行锁事务只移除当前会话用户的该条绑定，保留其他设置和绑定。重复解绑视为成功，同时取消该用户对此群的待验证请求。解绑不删除群记录，也不让机器人退群。
- 绑定写入 user_settings.bound_groups 的行锁事务保留其他设置；同群新群主验证后会移除旧用户的该群绑定。当前阶段只展示基础资料与管理自己的绑定，不把已验证的历史绑定当作后续敏感操作的实时群主凭据。
- 待验证请求仅保存在服务器内存，页面关闭后仍可在有效期内完成，重新获取 `/profile` 入口可查看结果；服务器重启则需重新生成验证码。已完成绑定保存在数据库，重启后仍可读取。
- 群栏目处于前台且等待验证时每 4 秒检查一次结果；切换栏目、切后台或结束页面后停止相应轮询。最多尝试 5 次错误验证码，新申请同一群返回现有未过期请求。

统一部署路径为 `/atrimeow/profile/`，开发和生产配置一致。Nginx 的 `proxy_pass` 使用不带 URI 的上游地址（例如 `http://127.0.0.1:1234`），完整保留该前缀；旧配置中的 `proxy_pass http://上游/profile/;` 需要相应修改。机器人指令仍为 `/profile`；已有 `miniapp.base-url` 若显式配置了旧路径，请改为 `/atrimeow/profile/`，仅填写域名则自动使用新入口。
