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
| 频道第二账号 | 频道内容、成员与通知操作 | [腾讯频道第二账号](integrations/tencent-channel.md) |

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

## 必要配置

Napcat 群功能由 `GroupConfigManager` 和 `groupconfig.json` 管理，各功能按键启用。QQ 官方群功能与订阅由对应业务配置和 WebUI 管理，不与 Napcat 开关共用

音效功能使用 `sound.resource-base-url`、`sound.index-path` 和 `sound.answer-seconds`。资源服务需提供索引及其引用的音频文件

AI 功能需配置所用提供方；生图和新版业务查询需配置 `ugc-api-url`。其余专用服务地址和密钥见 [配置模板](../src/main/resources/config.yml)

页面功能与权限边界见 [WebUI 与 Miniapp](web.md)。新增业务功能时，注册方式见 [指令](framework/commands.md)、[事件](framework/events.md) 和 [运行时服务](framework/runtime.md)
