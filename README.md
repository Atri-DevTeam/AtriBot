# YZ_Ljc QQ Bot

一个基于 Java 的模块化 QQ 机器人项目，聚焦于：
- **QQ 消息/事件处理**（对接 NapCat / OneBot 11）
- **Minecraft 侧联动**（Socket 心跳/事件与远程控制）
- **定时任务与推送**（新闻、打卡、统计等）
- **可配置的群功能开关**（按群启用/禁用特性）

本 README 采用“**大纲式说明**”：描述稳定的模块边界与配置入口，尽量避免罗列每条具体指令/功能，减少后续维护成本。

---

## 系统概览

- **入口**：`top.yzljc.qqbot.YzLjcBot`（负责加载配置、启动各模块定时任务与服务端）
- **QQ 对接**：HTTP 接收 NapCat 上报 + HTTP 调用 NapCat API 发送消息（`botkits/request` + `botkits/message`）
- **MC 对接**：`socket/SocketManager` 监听并维护 Minecraft 侧连接（端口见 `config.yml`）
- **配置中心**：`src/main/resources/config.yml`（运行时一般会从工作目录读取/覆盖，具体取决于 `config/Config` 的实现）

---

## 目录结构

```
src/main/java/top/yzljc/qqbot/
├── botkits/          # 底层工具包
│   ├── clock/        # 定时任务调度
│   ├── image/        # 图片处理
│   ├── message/      # 消息处理
│   ├── request/      # HTTP请求处理
│   ├── thread/       # 线程管理
│   ├── tools/        # 工具类
│   └── userinfo/     # 用户信息查询
├── command/          # 命令系统
├── config/           # 配置管理
│   └── groups/       # 群配置
├── debug/            # 调试工具
├── feature/          # 功能模块
│   ├── github/       # GitHub集成
│   ├── minecraft/    # Minecraft相关
│   ├── news/         # 新闻推送
│   └── schedule/     # 定时任务
├── socket/           # Socket通信
└── utils/            # 工具类

src/main/resources/   # 资源文件
├── config.yml        # 配置文件
├── logback.xml       # 日志配置
└── OneText-Library.json  # 数据文件

bot-python/           # Python脚本 (Hypixel相关)
website/              # Web界面 (PHP)
target/               # 编译输出
```

## 环境要求

- JDK 22 (启用preview features)
- Maven 3.x
- MySQL (可选，用于数据存储)
- NapCat 或 OneBot 11 兼容的QQ客户端

## 构建和运行

### 构建
```bash
mvn clean package
```

### 运行
```bash
java --enable-preview -jar target/Yzljc-qq-bot-2.6.1-RELEASE.jar
```

## 配置

编辑 `src/main/resources/config.yml` 或运行目录下的 `config.yml`:

### 基本配置
- `napcat-data-url`: NapCat API地址
- `qq-bot-port`: 接收QQ事件的端口
- `listen-port`: Minecraft Socket监听端口
- `admin-uids`: 管理员QQ号列表
- `bot-uid`: 机器人QQ号
- `debug-group-id`: 调试群号

### 可选配置
- `mysql`: 数据库连接信息
- `github-webhook-port`: GitHub Webhook端口
- `github-webhook-secret`: Webhook密钥
- `bilibili-cookie`: B站Cookie

## 主要功能

### 命令系统
- `/reload`: 重新加载配置
  - 无参数: 重新加载所有
  - `g`: 刷新群配置
  - `f`: 更新好友列表
  - `cfg`: 重新加载全局配置

### 功能模块
- **Minecraft集成**: 服务器状态监控、RCON控制、MOTD显示
- **新闻推送**: Hypixel和Minecraft新闻
- **GitHub集成**: Webhook接收和推送
- **互动功能**: 自动回复、戳一戳、点赞等
- **定时任务**: 每日签到、新闻推送等

### 群功能开关
支持按群启用/禁用特定功能，通过 `config/groups/` 配置。

## 开发

### 依赖
主要依赖见 `pom.xml`，包括:
- Jackson: JSON处理
- MySQL Connector: 数据库
- HikariCP: 连接池
- Jsoup: HTML解析
- Java-WebSocket: WebSocket支持

### 扩展
- 实现 `CommandExecutor` 接口添加新命令
- 在 `feature/` 下添加新功能模块
- 使用 `GroupConfigManager` 管理群配置

## 许可证

[请添加许可证信息]

## 贡献

欢迎提交Issue和Pull Request。

当前活跃分支: dev-doge
活跃PR: [BUG-FIX] 修改config.java，修复reload.java

- **默认开关注册**：见 `top.yzljc.qqbot.YzLjcBot` 中的 `GroupConfigManager.registerFeature(...)`
- **群配置管理**：见 `config/groups/` 目录（具体落盘格式/位置以实现为准）

---

## 集成说明

### NapCat / OneBot 11

- 需要在 NapCat 侧配置：将事件上报到本程序的 `qq-bot-port`
- 本程序发送消息会通过 `napcat-data-url` 调用 NapCat API

### Minecraft Socket

- Minecraft 侧需要有配套的插件/程序与本项目 `SocketManager` 协议一致
- 端口由 `listen-port` 控制

---

## 开发与扩展

- **新增一个“业务特性”**：通常放在 `feature/`，包含
    - 初始化/定时任务入口（scheduler）
    - 对消息事件的处理（从 `botkits/request` 分发而来）
    - 输出（文本/图片）通过 `botkits/message` 与 `botkits/image`
- **新增一个“命令式能力”**：放在 `command/`，并在总体分发处接入（具体分发逻辑以实现为准）
- **增加群开关**：在 `YzLjcBot` 中注册一个新的 `registerFeature(key, defaultValue)`，并在对应功能读取该开关

---

## 免责声明

本项目仅供学习交流使用。使用 QQ 机器人可能违反相关平台服务协议，由此产生的风险由使用者自行承担。