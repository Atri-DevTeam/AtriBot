# 功能索引

[文档目录](README.md)

业务功能位于 `function`，支持平台与参数以对应执行器为准。命令声明见 [atribot.yml](../src/main/resources/atribot.yml)，运行时可使用帮助命令查询

| 分类 | 内容 | 源码入口 |
| --- | --- | --- |
| Minecraft / Hypixel | 玩家查询、SkyBlock、资源包、状态与账号绑定 | [function/command](../src/main/java/top/yzljc/atribot/function/command)、[function/minecraft](../src/main/java/top/yzljc/atribot/function/minecraft) |
| 群管理 | 内容审查、加群欢迎、审批及群配置 | [function/tasks](../src/main/java/top/yzljc/atribot/function/tasks)、[WebUI 控制器](../src/main/java/top/yzljc/atribot/webui/controller) |
| 消息记录与日志 | 群聊、私聊、发送记录、原始事件和操作日志 | [QQChatContentRecord](../src/main/java/top/yzljc/atribot/function/tasks/QQChatContentRecord.java)、[database](../src/main/java/top/yzljc/atribot/database) |
| 推送 | 公告、资讯、日历与订阅任务 | [function/tasks](../src/main/java/top/yzljc/atribot/function/tasks)、[function/task](../src/main/java/top/yzljc/atribot/function/task) |
| 提醒 | 两步提醒设置与定时引用 | [function/reminder](../src/main/java/top/yzljc/atribot/function/reminder) |
| 游戏与签到 | 签到、抽卡、扫雷及其他游戏 | [function/games](../src/main/java/top/yzljc/atribot/function/games)、[function/command](../src/main/java/top/yzljc/atribot/function/command) |
| Minecraft 音效 | 听音辨物、资源索引与答题 | [SoundCommand](../src/main/java/top/yzljc/atribot/function/command/SoundCommand.java)、[games/sound](../src/main/java/top/yzljc/atribot/function/games/sound) |
| 群聊 AI | Napcat 群内会话 | [AtriChat](../src/main/java/top/yzljc/atribot/function/utils/AtriChat.java) |
| 图片及资料审查 | 生图、转存、皮肤和名字审查 | [图片与业务接口](integrations/images.md) |
| 频道第二账号 | 频道内容、成员与通知操作 | [QQ 频道第二账号](integrations/tencent-channel.md) |

## 提醒

QQ 官方私聊发送 `提醒 <时间或周期>`，由 AI 解析时间，再发送事项内容。`提醒`、`提醒列表` 均支持带或不带 `/`，要求 `test.reminder.use` 权限

`reminder-list`、`提醒列表` 注册为 `remind` 的别名，由同一执行器按 `label` 进入列表，统一使用提醒指令的配置

```text
用户：提醒 明早八点
机器人：提醒时间：10-05 08:00（北京时间）。请发送需要提醒的文字内容……
用户：领取游戏奖励
机器人：已设置事项ID #1。
```

到期后引用「领取游戏奖励」这条消息发送提醒。首次触发时间在第一步确定，补充内容不会改变相对时间或周期起点

内容限 200 字，设置请求两分钟内有效。收到可引用的文字内容并通过审查后启用任务；其他指令不作为内容接收。等待时发送 `取消` 或 `/提醒 取消` 退出，未完成的输入状态在重启后清除

`提醒列表` 使用 Markdown 显示任务 ID、状态和时间，每项提供「开启」「关闭」「删除」按钮。也可发送 `提醒列表 删除 <任务ID>`，支持带或不带 `/`。关闭保留任务，删除释放任务名额。再次开启周期任务时跳过已错过的周期，过期的一次性任务需要重新设置。旧的单次输入创建、修改及 `自定义提醒` 入口已移除

默认每人最多保留 10 项任务，每 30 分钟最多发起 30 次设置请求；`test.reminder.unlimit` 豁免这两项限制。沿用现有提醒表，无需修改数据库结构

## 今日钓鱼

QQ 官方群聊、私聊使用 `/fish`、`/今日钓鱼` 或无前缀的 `今日钓鱼`。每名用户每天一次，按北京时间零点刷新，跨群与私聊共用次数。重复调用返回当天已保存的渔获，不重复发放金粒

统一按海之眷顾 III、玩家幸运值 0、开放水域计算，鱼类、杂物、宝藏的权重为 82、4、11。先抽取钓点和类别，再合并公共池与对应钓点追加池抽取一个条目。钓点包含 24 组，群系追加项除丛林竹子外均为自定义掉落规则

奖励表见 [daily-fishing.json](../src/main/resources/games/daily-fishing.json)，随程序打包加载。`gold` 是整个条目的金粒数，不乘物品数量；非诅咒附魔每级增加 5 金粒，经验修补额外增加 30 金粒，附魔加成最多 100 金粒。耐久仅展示，不影响兑换。渔获直接兑换，不加入物品卡背包

`daily_fishing` 表按用户和日期保存完整结果，包括钓点、物品、附魔、耐久及奖励。渔获、金粒余额、来源日志和累计游戏统计在同一事务中提交；数据库唯一键与用户行锁防止并发重复领取。发送失败后再次调用可读取已结算结果，重启不会清除记录

`official_users.game_data.fishing.items` 按物品 ID 累计产出数量，`biomes` 按钓点 ID 累计钓鱼次数，供后续任务条件使用。这些累计记录不代表背包库存

结算后通过 UGC 接口目录中的 `bot.fishing` 生成 960 × 190 横幅，显示原版物品图标与渔获名称，插入 Markdown 回复。生图失败时回退文字结果，不影响已经提交的金粒和次数；再次查看可重新生成横幅

## 小游戏统计

用户累计指标保存在 `official_users.game_data`，通过 [UserGameDataRepository](../src/main/java/top/yzljc/atribot/database/repo/UserGameDataRepository.java) 读取和更新，无需新增字段或数据表。游戏键及指标如下

| 游戏键 | 指标 |
| --- | --- |
| `minesweeper` | `participations` 参与次数；`plays` 结算场次；`wins` 通关次数；`losses` 失败次数；`operations` 总操作次数；`digs` 挖格次数；`mineHits` 中雷次数；`flagsPlaced` 插旗次数；`flagsRemoved` 拔旗次数 |
| `sound` | `operations` 答题次数；`correct` 答对次数；`wrong` 答错次数 |
| `fishing` | `plays` 钓鱼次数；`fish` 鱼类次数；`junk` 杂物次数；`treasure` 宝藏次数；`gold` 累计兑换金粒；`enchanted` 附魔产物次数 |
| `connect4` | `participations` 开局参与次数；`plays` 结算场次；`wins` 胜场；`losses` 败场；`draws` 平局；`moves` 落子次数；`normalWins` 连成四子获胜次数；`timeoutWins` 对手超时获胜次数；`timeoutPlays` 因超时结束的参与场次；`moveTimeouts` 本人落子超时次数 |
| `roulette` | `participations` 开局参与次数；`plays` 结算场次；`wins` 幸存次数；`losses` 淘汰次数；`actions` 手动操作次数；`selfTargets` 手动指定自己次数；`otherTargets` 手动指定他人次数；`timeouts` 超时自动操作次数 |
| `rsp` | `participations` 出拳参与次数；`plays` 结算场次；`wins` 胜场；`losses` 败场；`draws` 平局；`rock` 石头次数；`scissors` 剪刀次数；`paper` 布次数 |
| `reaction` | `plays` 结算场次；`wins` 完成次数；`hits` 正确点击次数；`misses` 失误次数；`abandoned` 主动放弃次数；`timeouts` 超时次数；`timedCompletions` 有耗时记录的完成次数；`totalCompletedMs` 对应完成耗时之和；`bestMs`、`bestMisses`、`bestDate` 个人最佳成绩及其失误数、日期 |

扫雷在用户每局首次有效操作时累计参与次数，结算时为所有有效参与者记录通关或失败。中雷只记到挖中地雷的用户；挖格按有效挖格操作计数，自动展开的格子不额外计数。重复挖开已打开格子、挖插旗格子等无效操作不累计

四子棋和轮盘在满员开局时累计参与次数，等待加入后取消不计入。四子棋超时结束场次计入双方，本人落子超时只计入超时的一方。轮盘自动操作不计入手动操作和手动目标选择次数。猜拳在接受有效出拳时记录拳型，随后超时取消仍保留出拳记录，但不增加结算场次

反应力数据按发起者归属，在完成、超时或主动放弃时结算正确点击和失误次数。平均完成耗时使用 `totalCompletedMs / timedCompletions`，分母为零时无平均值；不使用含历史数据的 `wins` 作分母

旧扫雷数据缺少 `digs` 时，读取时将原 `operations` 全部归入 `digs`，后续写入时保存。旧听声辨物的 `wrong` 由 `operations - correct` 补齐。其余新增指标从更新后开始累计，旧胜场与场次保留；历史败场、平局和超时不作推定。原 Miniapp 使用的字段继续保留

任务系统可在任务开始时保存用户指标基线，用当前累计值减去基线计算进度，例如 `minesweeper.digs`、`sound.correct`、`connect4.normalWins`。目前没有按日重置的计数或任务领奖逻辑。发生进程重启时，未结算游戏的内存状态不会恢复；已写入的操作计数保留

`user_game_stat_events` 按游戏、事件和用户去重，多人记录使用同一事务。该表同时包含操作、开局和结算事件，不能直接把全部事件行数当作局数；新增结算事件使用无后缀的本局 ID，开局使用 `:start`，四子棋落子使用 `:move:行:列`，轮盘操作使用 `:turn:序号`，猜拳出拳使用 `:choice`，扫雷操作使用 `:序号`。听声辨物无后缀事件表示一次有效答题

## 必要配置

Napcat 群功能由 `GroupConfigManager` 和 `groupconfig.json` 管理，各功能按键启用。QQ 官方群功能与订阅由对应业务配置和 WebUI 管理，不与 Napcat 开关共用

音效功能使用 `sound.resource-base-url`、`sound.index-path` 和 `sound.answer-seconds`。资源服务需提供索引及其引用的音频文件

AI 功能需配置所用提供方；生图和新版业务查询需配置 `ugc-api-url`。其余专用服务地址和密钥见 [配置模板](../src/main/resources/config.yml)

页面功能与权限边界见 [WebUI 与 Miniapp](web.md)。新增业务功能时，注册方式见 [指令](framework/commands.md)、[事件](framework/events.md) 和 [运行时服务](framework/runtime.md)
