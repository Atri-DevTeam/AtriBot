# 配置

[文档目录](../README.md)

## 文件位置

`CoreConfig` 默认读取进程工作目录中的 `atri-core.yml`，首次读取时从 [默认模板](../src/main/resources/atri-core.yml) 创建文件，已有文件不覆盖

```java
var config = top.yzljc.atricore.core.CoreConfig.getInstance();
var settings = config.getSettings();
var path = config.getPath();
```

`new CoreConfig(path)` 可读取独立配置文件，适合测试或工具，不会替换默认单例

## 通用字段

| 字段 | 默认值 | 含义 |
| --- | --- | --- |
| `listen-port` | `1234` | 共享 HTTP 端口，范围为 0～65535，0 表示临时端口 |
| `command-prefix` | `/` | 显式文本指令前缀 |
| `debug-mode` | `false` | 框架调试开关 |

## QQ 官方机器人

| 字段 | 默认值 | 含义 |
| --- | --- | --- |
| `qq.enabled` | `false` | QQ 平台开关，Provider 据此初始化连接 |
| `qq.app-id` | 空字符串 | 机器人 AppID |
| `qq.client-secret` | 空字符串 | 机器人访问凭据 |
| `qq.api-base-url` | `https://api.bot.qq.com` | QQ API 根地址 |
| `qq.connection-mode` | `ws` | `ws` 或 `webhook` |
| `qq.webhook-path` | `/qq/webhook` | 共享 HTTP 服务上的 QQ 回调路径 |
| `qq.debug-group-openId` | 空字符串 | 调试目标群 OpenID |
| `qq.webhook-queue-capacity` | `1024` | QQ Webhook 待处理事件容量 |

连接模式忽略大小写，`wh` 转换为 `webhook`，未知值回退到 `ws`

Webhook 路径必须以 `/` 开头且不能等于 `/`，无效值回退到默认路径

## Napcat

| 字段 | 默认值 | 含义 |
| --- | --- | --- |
| `napcat.enabled` | `false` | 供运行服务控制 Napcat 相关业务初始化的开关 |
| `napcat.server-url` | `http://127.0.0.1:3000` | Napcat HTTP API 地址 |
| `napcat.token` | 空字符串 | HTTP API 访问 Token |
| `napcat.bot-uin` | 空字符串 | 机器人 QQ 号码 |
| `napcat.debug-group-uin` | 空字符串 | 调试群 QQ 号码 |
| `napcat.admin-uins` | `[]` | Napcat 群聊场景认可的机器人管理员 QQ 号码 |
| `napcat.message-spy-groups` | `[]` | 提供给相关业务判断的群列表 |
| `napcat.queue-capacity` | `1024` | 待处理事件数量上限 |
| `napcat.queue-bytes` | `16777216` | 待处理事件字节预算 |
| `napcat.workers` | `8` | 消费工作线程数，范围为 1～64 |

::: important Napcat 开关与回调路由
`napcat.enabled` 不控制 Provider 的 `POST /` 路由是否挂载，该路由始终用于接收 Napcat HTTP 上报
:::

## 线程与队列

| 字段 | 默认值 | 含义 |
| --- | --- | --- |
| `thread.max-concurrency` | `0` | 工作并发上限，0 使用自动值 |
| `thread.queue-capacity` | `4096` | 工作等待队列容量 |
| `thread.schedule-capacity` | `4096` | 尚未交给工作池的延迟任务容量 |

自动并发值为 CPU 数量 × 8，限制在 16～128，配置的队列容量必须大于零

对应 JVM 属性优先于配置文件：

| JVM 属性 | 配置字段 |
| --- | --- |
| `atribot.thread.maxConcurrency` | `thread.max-concurrency` |
| `atribot.thread.queueCapacity` | `thread.queue-capacity` |
| `atribot.thread.scheduleCapacity` | `thread.schedule-capacity` |
| `atribot.qq.webhook.queueCapacity` | `qq.webhook-queue-capacity` |
| `atribot.napcat.queueCapacity` | `napcat.queue-capacity` |
| `atribot.napcat.queueBytes` | `napcat.queue-bytes` |
| `atribot.napcat.workers` | `napcat.workers` |

## 重载配置

```java
CoreConfig.getInstance().reload();
var latest = AtriProvider.getInstance().getSettings();
```

全部解析成功后替换配置对象，读取或校验失败时抛出 `IllegalStateException` 并保留旧对象，重复 YAML 键不被接受

::: note 配置对象与文件
`AtriSettings` 是可修改的数据对象，链式设置只影响内存，不保存到 YAML，也不会重建既有服务，调用方不应长期缓存重载前的对象
:::

::: warning 重载不等于重启
连接参数、端口、平台开关、回调路径及线程队列容量变更后需要重启，配置重载本身不切换现有连接或路由
:::

Provider 默认返回 `CoreConfig` 的当前对象，线程与事件队列也直接读取默认配置；只覆盖 `getSettings()` 不能替代所有底层配置来源
