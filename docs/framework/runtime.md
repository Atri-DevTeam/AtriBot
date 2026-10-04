# 运行时服务

[文档目录](../README.md) · [架构与生命周期](architecture.md)

## 异步任务

[ThreadManager](../../src/main/java/top/yzljc/atribot/service/runtime/ThreadManager.java) 提供共享执行入口：

| 方法 | 返回 |
| --- | --- |
| `execute(Runnable)` | 直接提交 |
| `setExecute(Runnable)` | `Future<?>` |
| `submit(Callable<T>)` | `Future<T>` |
| `supplyAsync(Supplier<T>)` | `CompletableFuture<T>` |
| `setSchedule(Runnable, delay, unit)` | `ScheduledFuture<?>` |

工作线程使用虚拟线程，但并发和队列仍有上限：

| JVM 属性 | 默认值 |
| --- | --- |
| `atribot.thread.maxConcurrency` | CPU 数量 × 8，限制在 16～128 |
| `atribot.thread.queueCapacity` | 4096 |
| `atribot.thread.scheduleCapacity` | 4096 |

通过 `-D属性名=值` 在启动时调整。队列满或服务关闭时会拒绝任务，调用方需处理 `RejectedExecutionException`；`supplyAsync` 将拒绝作为失败 Future 返回

调度容量仅计算尚未交给工作池的任务，交接后由工作池容量约束。已在托管任务中调用 `supplyAsync` 时可能直接在当前线程完成，不应依赖它一定切换线程

## 周期调度

[TaskScheduler](../../src/main/java/top/yzljc/atribot/service/taskscheduler/TaskScheduler.java) 按时间边界重复调度，支持：

| `ScheduleMode` | 周期 |
| --- | --- |
| `daily` | 每日 |
| `hourly` | 每小时 |
| `half_hour` | 每半小时 |
| `a_quarter` | 每刻钟 |
| `minutely` | 每分钟 |

`ScheduledTask` 同时提供 `run()` 和 `schedule()`。也可提交 `Runnable` 与 `TaskSchedule`，返回的 `ScheduledTaskHandle` 支持取消和关闭

```java
TaskScheduler scheduler = new TaskScheduler();
ScheduledTaskHandle handle = scheduler.schedule(
        () -> logger.info("周期任务"),
        new TaskPlan(ScheduleMode.daily, LocalTime.of(8, 0))
);
```

按本地时间计算下一次执行；本次任务结束后再安排下次执行。`schedule(ScheduledTask)` 按任务类去重，直接提交 `Runnable` 的重载不做此去重

核心集中注册入口为 [TaskSchedulerRegistry](../../src/main/java/top/yzljc/atribot/service/taskscheduler/TaskSchedulerRegistry.java)。项目另有 `service.Scheduler` 与 `service.timer` 调度实现，已有业务分别使用各自入口

插件可使用上下文的专用调度器，也可将自建 `TaskScheduler` 交给 `context.manage(...)` 管理

## 数据库

[DatabaseManager](../../src/main/java/top/yzljc/atribot/database/DatabaseManager.java) 使用 HikariCP 管理 MySQL 连接：

```java
try (Connection connection = DatabaseManager.getConnection()) {
    // 使用 PreparedStatement 执行所需操作
}
```

关闭借用的 `Connection` 会归还连接池。Repository 负责表结构初始化和具体查询，多个写操作需要原子性时由调用方明确管理事务

配置来自 `mysql.*`，不是每次调用时重新创建连接池。插件通过 `PluginContext.getConnection()` 使用同一连接来源

## 文件配置与缓存

部署配置由 `Config` 读取，业务状态分布在 MySQL、运行目录文件和 `data/`。集中路径常量位于 [Properties](../../src/main/java/top/yzljc/atribot/configuration/Properties.java)

读入内存的数据不一定随文件修改实时刷新。修改前需检查对应服务是否提供重载方法；直接修改缓存文件可能被运行中的进程覆盖

## 语言资源

[I18N](../../src/main/java/top/yzljc/atribot/i18n/I18N.java) 从 `src/main/resources/lang/<语言>.json` 读取 UTF-8 扁平字符串对象：

```java
String text = I18N.text("common.no_permission");
String cooldown = I18N.text("common.cooldown", Map.of("seconds", 30));
String english = I18N.textFor("en_us", "common.no_permission");
```

默认语言为 `zh_cn`。目标语言缺失时回退中文，中文也缺失时返回 `[key]`。组件只返回文本，未自动接管全部业务回复和命令描述

参数格式为 `{name}`，只替换一遍；缺失参数保留占位符。组件不执行 Markdown、HTML 或平台标签转义，由输出层处理

语言资源首次使用时缓存，修改后重新构建并重启。`setDefaultLanguage(...)` 只修改进程默认值；按用户或群指定语言时使用 `textFor(...)`

## HTTP 与外部服务

普通 HTTP 工具位于 `service.request`。新版业务服务使用 `Requests` 与 `BizResponse`，接口目录使用 `OpenApi`，详见 [图片与业务接口](../integrations/images.md)

网络调用的超时、重试和业务成功判断由对应客户端定义。事件系统和指令框架不自动为网络操作提供重试
