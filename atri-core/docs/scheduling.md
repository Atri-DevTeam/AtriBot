# 任务调度

[文档目录](../README.md)

## 周期计划

Provider 持有一个 `TaskScheduler`，运行服务获取实例注册任务

```java
import top.yzljc.atricore.AtriProvider;
import top.yzljc.atricore.service.taskscheduler.ScheduleMode;
import top.yzljc.atricore.service.taskscheduler.TaskPlan;
import java.time.LocalTime;

var handle = AtriProvider.getInstance().getTaskScheduler().schedule(
        () -> System.getLogger("daily-task").log(System.Logger.Level.INFO, "执行每日任务"),
        new TaskPlan(ScheduleMode.daily, LocalTime.of(8, 0)));
```

也可以将业务与计划定义在同一个类中：

```java
import top.yzljc.atricore.service.taskscheduler.ScheduledTask;
import top.yzljc.atricore.service.taskscheduler.TaskSchedule;
import top.yzljc.atricore.service.taskscheduler.TaskPlan;
import top.yzljc.atricore.service.taskscheduler.ScheduleMode;
import java.time.LocalTime;

public final class DailyTask implements ScheduledTask {
    @Override
    public TaskSchedule schedule() {
        return new TaskPlan(ScheduleMode.daily, LocalTime.of(8, 0));
    }

    @Override
    public void run() {
        System.getLogger(DailyTask.class.getName())
                .log(System.Logger.Level.INFO, "执行每日任务");
    }
}
```

注册使用 `provider.getTaskScheduler().schedule(new DailyTask())`

::: tip 同一任务类的多个计划
`ScheduledTask` 重载按任务类去重，同一类多次注册会返回原句柄，即使原句柄已取消也不会创建新任务，需要独立实例计划时使用 `schedule(Runnable, TaskSchedule)` 重载
:::

任务采用显式注册，由运行服务统一维护注册清单

## 调度模式

| 模式 | `time` 含义 | 示例 |
| --- | --- | --- |
| `daily` | 每日本地时刻 | `08:30:00` 为每日八点半 |
| `hourly` | 每小时内偏移，小于 1 小时 | `00:10:00` 为每小时第十分钟 |
| `half_hour` | 每半小时内偏移，小于 30 分钟 | `00:05:00` 为每小时第 5、35 分钟 |
| `a_quarter` | 每刻钟内偏移，小于 15 分钟 | `00:02:00` 为每小时第 2、17、32、47 分钟 |
| `minutely` | 每分钟内偏移，小于 1 分钟 | `00:00:10` 为每分钟第十秒 |

按进程本地时间计算下一执行时点，恰好位于计划时点时选择下一周期，本次执行结束后再计算下一次，不重叠执行或补跑错过的时点

无参 `TaskPlan` 尚未设置模式，注册前必须调用 `setMode()`，默认时间为零时刻或零偏移

计划对象可修改但不提供并发更新保障，注册后不应从其他线程修改

## 取消与关闭

`handle.cancel(false)` 取消后续执行但不强制中断正在执行的任务，`handle.cancel(true)` 会请求中断，任务是否停止取决于其对中断的处理

取消返回值来自当前 Future，不表示运行中的代码一定已退出，`handle.close()` 等同于 `cancel(false)`

Provider 的 `close()` 会关闭共享计划调度器，关闭后拒绝新注册，已经开始的任务完成后不再安排下次执行

## 即时与固定间隔任务

`service.Scheduler` 用于即时、延迟和固定间隔执行，由 Provider 创建并管理

```java
var scheduler = AtriProvider.getInstance().getScheduler();
scheduler.runTaskLater(() -> System.getLogger("reminder")
        .log(System.Logger.Level.INFO, "延迟任务执行"), 1_000);
```

业务复用该实例，Provider 关闭时取消其待执行和重复任务，已开始的任务不受中断

| 方法 | 含义 |
| --- | --- |
| `runTask(task)` | 提交即时任务 |
| `runTaskLater(task, delayMillis)` | 延迟执行一次，单位为毫秒 |
| `runTaskTimer(task, delayMillis, periodMillis)` | 延迟开始并按间隔重复执行 |
| `cancelTask(handle)` | 取消指定任务，不中断运行中的调用 |
| `shutdown()` | 关闭当前调度器并取消其任务，共享实例由 Provider 调用 |

名称带 `Asynchronously` 的方法是对应方法的别名，所有任务均交给共享工作线程执行，执行线程为框架工作线程

间隔任务以计划时点推进，执行耗时超过间隔时跳过错过的时点，不集中补跑，`periodMillis` 必须大于零

## ThreadManager

| 方法 | 返回与失败行为 |
| --- | --- |
| `execute(Runnable)` | 无返回值，队列满或关闭时抛出拒绝异常 |
| `setExecute(Runnable)` | `Future<?>`，执行异常会记录并保存在结果中 |
| `submit(Callable<T>)` | `Future<T>`，用于等待结果或取消 |
| `supplyAsync(Supplier<T>)` | `CompletableFuture<T>`，执行或提交失败时异常完成 |
| `setSchedule(task, delay, unit)` | 覆盖等待及实际执行阶段的 `ScheduledFuture<?>` |
| `schedule(task, delay, unit)` | 延迟执行，不返回句柄 |
| `getExecutor()` | 共享执行器，业务模块不应自行关闭 |

工作线程使用虚拟线程，并发数、工作队列和定时容量均有上限，配置详见 [框架配置](configuration.md)

延迟任务只在交给工作池前占用定时容量，工作池拥塞时保留待调度任务并重试

已经在框架工作线程中调用 `supplyAsync()` 时可能直接在当前线程完成，不应假定它总会切换线程

::: warning 共享线程服务的关闭
`ThreadManager.shutdown()` 用于整个运行服务退出，不属于单个业务模块的清理操作，关闭后不能重新启动
:::

## 限流工具

`SlidingWindowRateLimiter` 使用单调时钟计算滑动窗口，`tryAcquire()` 立即返回，`acquire()` 阻塞等待额度，多个等待线程之间不保证顺序

需要有序执行时使用 `RateLimitedTaskQueue`：

```java
import top.yzljc.atricore.chat.official.GroupChat;
import top.yzljc.atricore.utils.concurrent.RateLimitedTaskQueue;
import top.yzljc.atricore.utils.concurrent.SlidingWindowRateLimiter;
import java.time.Duration;

var limiter = new SlidingWindowRateLimiter(10, Duration.ofSeconds(1));
var queue = new RateLimitedTaskQueue("group-notice", 256, limiter);
var result = queue.submit(() -> GroupChat.sendMessage(groupOpenId, "通知内容"));
```

`groupOpenId` 为目标 QQ 群 OpenID，队列应在服务启动时创建并复用，在服务关闭时调用 `queue.close()`

队列按 FIFO 串行执行，256 是等待容量，不包含当前执行的一个任务，队列满或关闭时返回异常完成的 Future

复用同一个限流器实例可共享额度，取消 Future 可跳过尚未执行的操作，但不保证中断已经开始的操作，使用结束后关闭队列
