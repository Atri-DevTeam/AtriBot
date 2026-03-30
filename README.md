# AtriBot (YZ_Ljc QQ Bot)

一个基于 Java 22 的模块化 QQ 机器人项目，聚焦于：
- **QQ 消息/事件处理**（对接 NapCat / OneBot 11）
- **Minecraft 侧联动**（Socket 心跳/事件与远程控制）
- **定时任务与推送**（新闻、打卡、日历、统计等）
- **可配置的群功能开关**（按群启用/禁用特性）

---

## 系统概览

- **入口**：`top.yzljc.qqbot.YzLjcBot`（负责加载配置、启动各模块定时任务与服务端）
- **QQ 对接**：HTTP 接收 NapCat 上报 + HTTP 调用 NapCat API 发送消息（`botservice/request` + `botservice/message`）
- **MC 对接**：`socket/SocketManager` 监听并维护 Minecraft 侧连接（端口见 `config.yml`）
- **配置中心**：`src/main/resources/config.yml`（运行时从工作目录读取，不存在时自动从 classpath 复制默认配置）

---

## 目录结构

```
src/main/java/top/yzljc/qqbot/
├── botservice/       # 底层工具包
│   ├── clock/        # 定时任务调度
│   ├── image/        # 图片处理
│   ├── message/      # 消息处理
│   ├── request/      # HTTP 请求处理（NapCat 上报接收与 API 调用）
│   ├── thread/       # 线程管理
│   ├── tools/        # 工具类
│   └── userinfo/     # 用户/项目信息查询
├── command/          # 命令系统（CommandManager / CommandMap / CommandExecutor 接口）
│   └── impl/         # 内置命令实现（reboot、rollback、search 等）
├── config/           # 配置管理（Config / Settings / Reload）
│   └── groups/       # 群功能开关管理
├── debug/            # 调试工具（Broadcast 等）
├── feature/          # 功能模块
│   ├── github/       # GitHub Webhook 接收与推送
│   ├── minecraft/    # Minecraft 集成（MOTD、RCON、Hypixel 等）
│   ├── news/         # 新闻推送（Minecraft / Hypixel）
│   └── schedule/     # 定时任务（签到、日历、起床、课程提醒等）
├── socket/           # Socket 通信（SocketManager）
└── utils/            # 通用工具（AtriHelp、AutoAccept、FindRecall 等）

src/main/resources/   # 资源文件
├── config.yml        # 配置文件（首次运行自动生成）
├── logback.xml       # 日志配置
├── OneText-Library.json  # 一言数据库
└── ...               # 图片/字体资源

bot-python/           # Python 脚本（HypixelReward 相关）
website/              # Web 界面（PHP）
```

---

## 环境要求

- JDK 22（需启用 `--enable-preview`）
- Maven 3.x
- MySQL（可选，用于消息统计等数据存储）
- NapCat 或其他 OneBot 11 兼容的 QQ 客户端

---

## 构建和运行

### 构建
```bash
mvn clean package
```

### 运行
```bash
java --enable-preview -jar target/Yzljc-qq-bot-2.6.2-Release.jar
```

---

## 配置

首次运行时会自动在工作目录生成 `config.yml`，编辑其中的各项配置后重启即可。

### 主要配置项

| 配置键 | 说明 | 默认值 |
|--------|------|--------|
| `command-prefix` | 指令前缀 | `/` |
| `napcat-data-url` | NapCat HTTP API 地址 | `http://localhost:12345` |
| `qq-bot-port` | 接收 NapCat 上报的端口 | `1234` |
| `websocket-port` | WebSocket 服务监听地址 | `ws://localhost:1111` |
| `listen-port` | Minecraft Socket 监听端口 | `25566` |
| `admin-uids` | 管理员 QQ 号列表 | — |
| `bot-uid` | 机器人自身 QQ 号 | — |
| `debug-group-id` | 调试群号 | — |
| `message-spy-groups` | 启用消息监听的群号列表 | — |
| `recall-ignore-user` | 撤回监听屏蔽用户列表 | — |
| `debug-mode` | 调试模式开关 | `false` |
| `ttf-file-name` | 图片生成使用的字体文件名 | `default.ttf` |
| `wakeup-image-link` | 起床表情包自定义表情链接 | — |

### 可选配置项

| 配置键 | 说明 |
|--------|------|
| `mysql` | 数据库连接信息（`host` / `port` / `database` / `username` / `password`） |
| `github-webhook-port` | GitHub Webhook 接收端口（默认 `54321`） |
| `github-webhook-secret` | GitHub Webhook 密钥 |
| `bilibili-cookie` | B 站 Cookie（用于视频信息查询） |
| `manosaba-group-id` | ManoDate 推送群号 |
| `keywords-hitokoto` | 触发"一言"功能的关键词列表 |
| `keywords-like-user` | 触发"点赞"功能的关键词列表 |
| `autolike-uids` | 自动点赞目标 QQ 列表（`/autolike add` 会写入此处） |

---

## 指令列表

| 指令 | 说明 | 群功能开关 |
|------|------|-----------|
| `/atrihelp` | 显示帮助菜单 | — |
| `/reload [all\|cfg\|f\|g]` | 重新加载配置（全部/全局配置/好友列表/群配置） | — |
| `/groupinfo` | 查看本群功能开启情况 | — |
| `/stats [y\|overall\|@user]` | 消息统计（当日/昨日/总计/指定用户） | — |
| `/search "关键词" [-u QQ] [-m p/a]` | 搜索聊天记录 | — |
| `/recall` | 撤回 Bot 上一条消息 | — |
| `/rollback [-n 数量] [-u QQ号]` | 批量撤回消息 | — |
| `/mojang` | 查询 Mojang 验证服务器状态 | `mojang_status` |
| `/motd <ip[:port]>` | 查询 MC 服务器 MOTD | `motd` |
| `/cl <Hypixel签到链接>` | 领取 Hypixel 每日奖励 | `get_hypixel_reward` |
| `/bwc <玩家名>` 或 `/bwc api <Key>` | 起床战争每日挑战查询 | `bedwars_challenge` |
| `/checkmcnews` | 手动获取 Minecraft 新闻 | `mc_news` |
| `/checkhypnews` | 手动获取 Hypixel 新闻 | `hyp_news` |
| `/calendar` | 发送今日日历 | `calendar` |
| `/emj <normal/medium/insane/animation> [User]` | 表情轰炸 | `annoy_user` |
| `/bc <内容>` | 全服广播 | `broadcast` |
| `/signall` | 触发自动群打卡 | `auto_sign` |
| `/wakeup` | 发送起床表情包 | `wakeup_send` |
| `/manodate` | Manosaba 日期查询 | — |
| `/github [群号...]` | 手动触发 GitHub 推送 | `github_info` |
| `/autolike add\|remove\|list [User]` | 管理自动点赞列表 | — |
| `/tufe` | 查询下节课上课地点 | `tufe_class_alert` |
| `/reboot` | 重启 Bot（管理员） | — |
| `/happynewyear` | 发送新年快乐 | `new_year` |
| 发送 `"一言"` / `"hitokoto"` | 获取随机一言 | `one_text` |
| 发送 `"赞我"` / `"点赞"` | 获取名片赞 | `like_user` |

---

## 群功能开关

所有群功能开关通过 `GroupConfigManager` 管理，在 `YzLjcBot.main` 中统一注册默认值，可在运行时通过管理指令按群修改。

| 功能键 | 说明 | 默认值 |
|--------|------|--------|
| `auto_sign` | 每日自动群打卡 | 开启 |
| `mc_news` | Minecraft 新闻推送 | 开启 |
| `hyp_news` | Hypixel 新闻推送 | 开启 |
| `electric_check` | 电费查询 | 关闭 |
| `annoy_user` | 表情轰炸 | 开启 |
| `new_year` | 新年快乐 | 开启 |
| `one_text` | 随机一言 | 开启 |
| `repeat_msg` | 自动复读 | 开启 |
| `send_poke` | 自动回复戳一戳 | 开启 |
| `like_user` | 名片赞 | 开启 |
| `mojang_status` | Mojang 状态查询 | 开启 |
| `motd` | MOTD 查询 | 关闭 |
| `github_info` | GitHub 推送 | 关闭 |
| `bv_check` | B 站视频解析 | 关闭 |
| `wakeup_send` | 每日起床表情包 | 关闭 |
| `broadcast` | 全服广播 | 开启 |
| `calendar` | 每日日历推送 | 开启 |
| `get_hypixel_reward` | Hypixel 每日签到 | 关闭 |
| `bedwars_challenge` | 起床战争挑战查询 | 开启 |
| `tufe_class_alert` | 课程提醒 | 关闭 |

---

## 开发与扩展

### 依赖（主要）

| 依赖 | 说明 |
|------|------|
| Jackson 2.x | JSON 处理 |
| SnakeYAML 2.x | YAML 配置解析 |
| MySQL Connector/J 9.x | 数据库驱动 |
| HikariCP 7.x | 数据库连接池 |
| Jsoup 1.x | HTML 解析（新闻抓取） |
| Java-WebSocket 1.x | WebSocket 支持 |
| Logback 1.x | 日志框架 |
| Lombok 1.x | 代码生成 |
| lunar 1.x（6tail） | 农历/节气计算 |
| commons-text 1.x | 文本工具 |

### 新增指令
1. 实现 `CommandExecutor` 接口，编写命令逻辑
2. 在 `CommandManager` 的静态初始化块中调用 `register(...)` 注册

### 新增功能模块
1. 在 `feature/` 下创建新包，编写功能逻辑
2. 若需定时任务，在 `feature/schedule/` 中添加调度类，并在 `RunScheduleTask` 中注册
3. 若需群功能开关，在 `YzLjcBot.main` 中调用 `GroupConfigManager.registerFeature(key, defaultValue)`

---

## 集成说明

### NapCat / OneBot 11

- 在 NapCat 侧配置 HTTP 上报地址：`http://<Bot所在IP>:<qq-bot-port>`
- Bot 发送消息通过 `napcat-data-url` 调用 NapCat API

### Minecraft Socket

- Minecraft 侧需要有配套插件/程序，与 `SocketManager` 协议保持一致
- 监听端口由 `listen-port` 配置项控制

### GitHub Webhook

- 在 GitHub 仓库设置中将 Webhook URL 指向 `http://<Bot所在IP>:<github-webhook-port>`
- 配置对应的 `github-webhook-secret` 密钥

---

## 免责声明

本项目仅供学习交流使用。使用 QQ 机器人可能违反相关平台服务协议，由此产生的风险由使用者自行承担。
