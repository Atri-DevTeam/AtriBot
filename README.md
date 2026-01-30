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

以下结构以 `src/main/java/top/yzljc/qqbot/` 为根：

- **`botkits/`**：机器人底层能力（可复用的“工具箱”）
  - **`botkits/request/`**：HTTP 接收与请求处理（事件进入点、数据预处理、类型分发）
  - **`botkits/message/`**：消息发送/过滤/记录等通用组件
  - **`botkits/image/`**：图片绘制抽象与具体实现（文本图、状态图等）
  - **`botkits/findinfo/`**：用户/群信息查询辅助
- **`feature/`**：具体业务特性（通常是“某个定时任务 + 某类消息触发处理 + 推送/图片”）
  - **`feature/minecraft/`**：Minecraft 状态、RCON、MOTD 等
  - **`feature/news/`**：各类资讯抓取/推送
  - **`feature/github/`**：GitHub Webhook 与消息展示
  - **其他特性**：如自动签到、复读、互动类功能等
- **`command/`**：偏“命令式”的交互入口（例如总控/回滚/搜索类指令）
- **`config/`**：配置读取与群配置管理
  - **`config/groups/`**：群维度配置、群列表、群模式（以及功能开关）
- **`socket/`**：与 Minecraft 侧 Socket 通信（连接管理、心跳、指令/数据通道）
- **`utils/`**：与业务强相关但不适合放入 `botkits/` 的通用工具（统计、回溯等）
- **`web/`**：Web Dashboard / API（供管理与查看）
- **`debug/`**：调试相关工具与事件结构
- **`deprecated/`**：历史实现/弃用模块（仅供参考）

资源文件位于 `src/main/resources/`（`config.yml`、图片、`logback.xml` 等）。

---

## 运行与构建

### 环境要求

- **JDK 22**（项目 `pom.xml` 使用 `maven.compiler.source/target=22` 且启用了 `--enable-preview`）
- **NapCat / OneBot 11**：提供 HTTP 上报与 HTTP API
- **可选：MySQL**（用于消息记录/统计等能力，见 `config.yml` 的 `mysql` 配置段）

### 本地构建

```bash
mvn clean package
```

构建完成后会在 `target/` 生成可运行的 fat jar（shade），主类为 `top.yzljc.qqbot.YzLjcBot`。

### 运行

```bash
java --enable-preview -jar target/Yzljc-qq-bot-*.jar
```

说明：
- 程序启动时会设置 `java.awt.headless=true`，以支持在无图形界面环境生成图片。

---

## 配置（`config.yml`）

`src/main/resources/config.yml` 提供了默认模板（实际部署通常会在运行目录放一份同名配置以覆盖默认值）。

### 必配项

- **`napcat-data-url`**：NapCat HTTP API 地址（必须是 `http://host:port`）
- **`qq-bot-port`**：本程序用于接收 NapCat 上报事件的端口
- **`listen-port`**：Minecraft 侧连接到本程序的 Socket 监听端口
- **`admin-uids` / `bot-uid` / `debug-group-id`**：管理员、机器人自身 QQ、调试群
- **`message-spy-groups`**：启用消息监听/统计的群列表

### 可选项

- **`mysql`**：消息记录与统计相关（host/port/database/username/password）
- **`github-webhook-port` / `github-webhook-secret`**：GitHub Webhook 端口与签名密钥
- **`web-dashboard-port`**：Web Dashboard 服务端口
- **`bilibili-cookie`**：B 站查询相关（需要登录态时）
- **关键字触发**：例如 `keywords-hitokoto`、`keywords-like-user` 等

---

## 群功能开关

本项目支持“**按群维度**”控制特性开关（例如自动签到、新闻推送、互动功能等）。

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
