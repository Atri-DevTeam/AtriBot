# YZ_Ljc_ QQ Bot Edition

这是一个基于 Java 开发的模块化 QQ 机器人项目，专为 Minecraft 服务器管理、资讯推送及群组活跃度统计而设计。

本项目通过 HTTP 协议与 [NapCatQQ](https://github.com/NapNeko/NapCatQQ) (OneBot 11) 进行通信，并通过 Socket 与 Minecraft 服务器插件进行交互，实现跨平台的服务器状态监控与指令下发。

---

## 📁 项目结构

经过重构，项目采用清晰的分层架构：

```text
src/main/java/top/yzljc/qqbot/
├── App.java                 # 程序入口，负责初始化配置、启动定时任务和Socket/HTTP服务
├── messages/                # 消息处理核心层
│   ├── MessageReceiver.java # HTTP 服务端，监听 NapCat 上报的事件
│   ├── MessageSender.java   # HTTP 客户端，统一负责向 NapCat 发送消息/图片
│   └── MessageProcessor.java# 业务分发中心，将消息路由至不同模块
├── socket/                  # Socket 通信层
│   └── SocketManager.java   # 管理与 Minecraft 服务器的 Socket 连接、心跳及指令下发
├── minecraft/               # Minecraft 相关业务
│   ├── SendCommand.java     # 处理 /rc 远程指令的鉴权与逻辑
│   └── StatusReporter.java  # 生成服务器状态图片并推送
├── news/                    # 资讯推送模块
│   ├── MinecraftNews.java   # MC 官方新闻抓取 (JSON源)
│   └── HypixelNews.java     # Hypixel 官网新闻抓取 (Jsoup爬虫)
├── command/                 # 群指令模块
│   ├── MessageStats.java    # 发言统计 (每日自动推送/主动查询)
│   ├── AnnounceGroup.java   # 群公告处理
│   └── RollbackMessages.java# 消息撤回工具
├── tools/                   # 实用工具箱
│   ├── AutoSign.java        # 自动签到
│   ├── AutoAccept.java      # 自动同意好友/群请求
│   ├── ElectricCheck.java   # 天津财经大学电费查询
│   ├── CheckBilibili.java   # B站视频信息解析
│   ├── LikeUser.java        # "赞我"功能
│   └── AnnoyUser.java       # 趣味功能
└── img/                     # 图片生成
    ├── ManosabaDate.java    # 项目进度倒计时图片生成
    └── MinecraftStatusImage.java # 服务器状态图绘图逻辑
```

---

## ✨ 功能特性

### 🎮 Minecraft 服务器管理

- **状态监控**：通过 Socket 实时接收服务器上线/离线心跳，自动生成状态图片推送到指定群。
- **远程控制 (RCON)**：支持在群内使用 `/rc <ServerID> <Command>` 下发指令到服务器控制台，并实时回显控制台日志。
- **鉴权系统**：基于 `adminuser.json` 和 `server-secret.json` 的双重鉴权，确保指令安全。

### 📰 资讯自动推送

- **Minecraft 官方新闻**：每小时检查 Java版/基岩版 更新及官网新闻，支持图文推送。
- **Hypixel 新闻**：自动抓取 Hypixel 论坛公告。
- **项目进度**：Manosaba 项目开发天数每日自动打卡。

### 🛠️ 群组实用工具

- **发言统计**：每日自动生成群发言排行榜，支持 `/stats` 查询个人或全群数据。
- **B站解析**：发送 `/bl BV...` 自动解析视频详情（封面、数据、在线人数）。
- **电费查询**：特定群组支持查询宿舍电费。
- **自动签到**：每日自动在手机端模拟打卡/签到。
- **其他**：自动同意好友请求、防撤回/撤回操作、点赞互通等。

---

## 🚀 部署说明

### 1. 环境要求

- JDK 11+ (推荐 JDK 17)
- NapCatQQ (运行在 HTTP 模式)
- 数据库（用于存储群消息记录，配置在 RecordGroupMessage 中）

### 2. 配置文件

在程序运行目录下需准备以下文件：

#### serverlist.txt (Socket连接配置)

```text
群号/服务器名称/IP/端口/ServerID
123456/生存服/127.0.0.1/25565/survival#987654/创造服/192.168.1.1/25566/creative
```

#### adminuser.json (远程指令权限)

```json
[
  {
    "user": "管理员QQ",
    "group": "授权群号",
    "server-id": "survival"
  }
]
```

#### server-secret.json (Socket通信密钥)

```json
[
  {
    "server-id": "survival",
    "secret-key": "your_secure_token"
  }
]
```

### 3. 端口配置

- Bot HTTP 监听端口: `8851`（在 `App.java` 中修改 `QQ_BOT_PORT`）—— 用于接收 NapCat 的上报。
- Socket 监听端口: `37142`（在 `App.java` 中修改 `LISTEN_PORT`）—— 用于接收 MC 服务器的连接。
- NapCat 发送地址: 默认为 `http://106.14.23.232:8848`（在 `MessageSender.java` 中修改）。

### 4. 启动

编译项目并运行 `top.yzljc.qqbot.App` 类的 main 方法。  
注意：需添加 `-Djava.awt.headless=true` 启动参数以支持在无头服务器上生成图片。

---

## 📝 指令列表

| 指令                | 描述                        | 权限         |
|---------------------|-----------------------------|--------------|
| `/rc <ID> <Cmd>`    | 向指定服务器发送控制台指令  | 配置管理员   |
| `/stats`            | 查看今日群发言统计          | 所有人       |
| `/statsoverall`     | 查看历史群发言统计          | 所有人       |
| `/bl BVxxxx`        | 查询B站视频详情             | 所有人       |
| 赞我 / likeme       | 给发送者名片点赞 10 次      | 所有人       |
| manodate            | 查看项目开发进度图片        | 所有人       |
| 电表 / db           | 查询宿舍电费 (仅限特定群)   | 所有人       |
| testformc           | 手动触发 MC 新闻检查        | 超级管理员   |
| testforhyp          | 手动触发 Hypixel 新闻检查   | 超级管理员   |

---

## ⚠️ 免责声明

本项目仅供学习交流使用。使用 QQ 机器人可能违反腾讯服务协议，由此产生的封号风险由使用者自行承担。