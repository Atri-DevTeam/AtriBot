package top.yzljc.atribot.function.admin.update;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 在独立工作目录验证更新通知的持久化、并发去重及入群任务失效处理
 *
 * @Author YZ_Ljc_
 * @ClassName UpdateNoticeDeliveryTest
 * @Created_at 2026/11/03
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.admin.update
 */
class UpdateNoticeDeliveryTest {
    @TempDir Path directory;

    @Test
    void shareDeliveryRecordAcrossTriggers() throws Exception {
        String classpath = Stream.of(UpdateNoticeDeliveryTest.class, UpdateNoticeRecord.class,
                        ObjectMapper.class, JsonFactory.class, JsonIgnoreProperties.class, LoggerFactory.class)
                .map(type -> {
                    try { return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString(); }
                    catch (Exception e) { throw new IllegalStateException(e); }
                }).distinct().collect(Collectors.joining(File.pathSeparator));
        Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", classpath, Probe.class.getName()).directory(directory.toFile()).redirectErrorStream(true).start();
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "发送记录验证超时");
            assertEquals(0, process.exitValue(), new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        } finally {
            process.destroyForcibly();
        }
    }

    public static class Probe {
        public static void main(String[] args) throws Exception {
            require(!UpdateNoticeRecord.isJoinEventPushEnabled(), "入群推送应默认关闭");
            require(!UpdateNoticeRecord.isPassiveMessagePushEnabled(), "被动消息推送应默认关闭");
            var content = new UpdateNoticeContent("更新", "TEXT", List.of());
            UpdateNoticeRecord.setContent(content);
            UpdateNoticeRecord.setJoinEventPush(true);
            var trigger = UpdateNoticeRecord.getJoinTrigger("group");
            require(trigger != null, "启用后应创建触发凭据");
            UpdateNoticeRecord.deliverPassiveOnce("group", (notice, time) -> { throw new AssertionError("被动消息开关关闭时不得发送"); });
            UpdateNoticeRecord.setSettings(null, null, true);
            require(trigger.equals(UpdateNoticeRecord.getJoinTrigger("group")), "切换被动推送不得使入群延迟任务失效");
            AtomicInteger calls = new AtomicInteger();
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
                var first = workers.submit(() -> UpdateNoticeRecord.deliverOnce("group", trigger, (notice, time) -> {
                    calls.incrementAndGet();
                    started.countDown();
                    await(release);
                    return "message";
                }));
                await(started);
                var others = java.util.stream.IntStream.range(0, 32).mapToObj(i -> workers.submit(() -> {
                    if (i % 3 == 0) {
                        UpdateNoticeRecord.deliverPassiveOnce("group", (notice, time) -> {
                            calls.incrementAndGet();
                            return "duplicate-passive";
                        });
                    } else {
                        UpdateNoticeRecord.deliverOnce("group", i % 2 == 0 ? null : trigger, (notice, time) -> {
                            calls.incrementAndGet();
                            return "duplicate";
                        });
                    }
                })).toList();
                for (var other : others) other.get();
                release.countDown();
                first.get();
            }
            require(calls.get() == 1, "并发消息和事件只能发送一次");
            require(UpdateNoticeRecord.isNotified("group"), "发送成功应记录群");
            UpdateNoticeRecord.deliverOnce("group", null, (notice, time) -> { throw new AssertionError("事件发送后消息不得重复发送"); });

            var messageFirst = UpdateNoticeRecord.getJoinTrigger("message-first");
            UpdateNoticeRecord.deliverOnce("message-first", null, (notice, time) -> "message");
            UpdateNoticeRecord.deliverOnce("message-first", messageFirst, (notice, time) -> { throw new AssertionError("消息发送后事件不得重复发送"); });
            long publishedAt = UpdateNoticeRecord.getCreatedAt();
            var disabledTrigger = UpdateNoticeRecord.getJoinTrigger("disabled");
            UpdateNoticeRecord.setJoinEventPush(false);
            UpdateNoticeRecord.setJoinEventPush(true);
            UpdateNoticeRecord.deliverOnce("disabled", disabledTrigger, (notice, time) -> { throw new AssertionError("关闭后旧任务不得恢复"); });
            require(UpdateNoticeRecord.getCreatedAt() == publishedAt && UpdateNoticeRecord.getNotifiedGroups().size() == 2,
                    "切换开关不得重置通知记录");
            var pausedTrigger = UpdateNoticeRecord.getJoinTrigger("paused");
            UpdateNoticeRecord.setSettings(false, null);
            require(UpdateNoticeRecord.getJoinTrigger("paused") == null, "关闭总开关后不应创建延迟任务");
            UpdateNoticeRecord.deliverOnce("paused", null, (notice, time) -> { throw new AssertionError("关闭总开关后不得由消息发送"); });
            UpdateNoticeRecord.deliverPassiveOnce("paused", (notice, time) -> { throw new AssertionError("关闭总开关后不得由被动消息发送"); });
            UpdateNoticeRecord.deliverOnce("paused", pausedTrigger, (notice, time) -> { throw new AssertionError("关闭总开关后不得由事件发送"); });
            UpdateNoticeRecord.reload();
            require(!UpdateNoticeRecord.isEnabled(), "重载后应保留停用状态");
            UpdateNoticeRecord.setSettings(true, null);
            UpdateNoticeRecord.deliverOnce("paused", pausedTrigger, (notice, time) -> { throw new AssertionError("重新启用不得恢复旧任务"); });
            require(UpdateNoticeRecord.isNotified("group") && UpdateNoticeRecord.getCreatedAt() == publishedAt,
                    "总开关不得清空通知记录或更改发布时间");
            UpdateNoticeRecord.deliverOnce("group", null, (notice, time) -> { throw new AssertionError("重新启用后不得重发"); });
            var layoutTrigger = UpdateNoticeRecord.getJoinTrigger("layout");
            var buttons = List.of(List.of(new UpdateNoticeContent.NoticeButton("今日问题", "/question", "COMMAND", "BLUE", true)));
            UpdateNoticeRecord.setButtons(buttons);
            require(UpdateNoticeRecord.getCreatedAt() == publishedAt && UpdateNoticeRecord.getNotifiedGroups().size() == 2,
                    "保存默认按钮不得发布新通知");
            UpdateNoticeRecord.setContent(content);
            require(UpdateNoticeRecord.isNotified("group"), "正文及类型不变不得重置群记录");
            var plainLayout = new UpdateNoticeContent.MessageLayout("新标题", "新落款");
            UpdateNoticeRecord.setLayouts(plainLayout, UpdateNoticeContent.MessageLayout.defaults());
            require(UpdateNoticeRecord.getCreatedAt() == publishedAt && UpdateNoticeRecord.getNotifiedGroups().size() == 2,
                    "保存标题与落款不得发布新通知");
            UpdateNoticeRecord.deliverOnce("group", null, (notice, time) -> { throw new AssertionError("修改标题后不得重发已通知群"); });
            UpdateNoticeRecord.deliverOnce("layout", layoutTrigger, (notice, time) -> {
                require(notice.render("机器人", "时间").equals("新标题\n\n更新\n\n新落款"), "后续发送应采用新的标题与落款");
                require(notice.buttons().equals(buttons), "后续发送应采用默认按钮");
                return "layout-message";
            });

            UpdateNoticeRecord.deliverOnce("retry", null, (notice, time) -> null);
            require(!UpdateNoticeRecord.isNotified("retry"), "无消息ID不得标记成功");
            try {
                UpdateNoticeRecord.deliverOnce("retry", null, (notice, time) -> { throw new IllegalStateException("发送失败"); });
            } catch (IllegalStateException ignored) {}
            UpdateNoticeRecord.deliverOnce("retry", null, (notice, time) -> "success");
            require(UpdateNoticeRecord.isNotified("retry"), "失败后应释放发送占位");
            UpdateNoticeRecord.reload();
            require(UpdateNoticeRecord.isJoinEventPushEnabled() && UpdateNoticeRecord.isNotified("group"), "重载应保留开关和群记录");

            var oldTrigger = UpdateNoticeRecord.getJoinTrigger("old");
            UpdateNoticeRecord.setContent(new UpdateNoticeContent("新通知", "TEXT", List.of()));
            require(UpdateNoticeRecord.getContent().textLayout().equals(plainLayout), "保存正文不得覆盖独立标题与落款");
            UpdateNoticeRecord.deliverOnce("old", oldTrigger, (notice, time) -> { throw new AssertionError("旧入群事件不得发送新通知"); });
            UpdateNoticeRecord.deliverOnce("revision", null, (notice, time) -> {
                try { UpdateNoticeRecord.setContent(content); }
                catch (Exception e) { throw new IllegalStateException(e); }
                return "old-message";
            });
            require(!UpdateNoticeRecord.isNotified("revision"), "旧通知发送结果不得记入新通知");
            UpdateNoticeRecord.deliverOnce("type", null, (notice, time) -> "plain-message");
            UpdateNoticeRecord.setContent(new UpdateNoticeContent(content.text(), "MARKDOWN", List.of()));
            require(!UpdateNoticeRecord.isNotified("type"), "类型切换应作为新通知");
            UpdateNoticeRecord.clear();
            UpdateNoticeRecord.reload();
            require(!UpdateNoticeRecord.hasNotice() && UpdateNoticeRecord.isJoinEventPushEnabled(), "清空通知应保留推送设置");
            require(UpdateNoticeRecord.getContent().textLayout().equals(plainLayout), "清空和重载后应保留独立标题与落款");
            require(UpdateNoticeRecord.getContent().buttons().equals(buttons), "切换类型、保存通知、清空和重载后应保留默认按钮");
            require(UpdateNoticeRecord.isPassiveMessagePushEnabled(), "保存、清空及重载不得丢失被动推送设置");
            UpdateNoticeRecord.setContent(content);
            UpdateNoticeRecord.deliverPassiveOnce("passive-first", (notice, time) -> "passive-message");
            require(UpdateNoticeRecord.isNotified("passive-first"), "被动发送成功应记录群");
            UpdateNoticeRecord.deliverOnce("passive-first", null, (notice, time) -> { throw new AssertionError("被动发送后指令不得重发"); });
            long passivePublishedAt = UpdateNoticeRecord.getCreatedAt();
            UpdateNoticeRecord.setSettings(null, null, false);
            UpdateNoticeRecord.reload();
            require(!UpdateNoticeRecord.isPassiveMessagePushEnabled(), "被动推送关闭状态应持久化");
            UpdateNoticeRecord.deliverPassiveOnce("disabled-passive", (notice, time) -> { throw new AssertionError("重载后被动推送仍应关闭"); });
            UpdateNoticeRecord.deliverOnce("command-only", null, (notice, time) -> "command-message");
            require(UpdateNoticeRecord.isNotified("command-only"), "被动推送关闭不得影响原指令入口");
            UpdateNoticeRecord.setSettings(null, null, true);
            require(UpdateNoticeRecord.getCreatedAt() == passivePublishedAt && UpdateNoticeRecord.isNotified("passive-first"), "切换被动推送不得重置版本和群记录");
            UpdateNoticeRecord.deliverPassiveOnce("passive-first", (notice, time) -> { throw new AssertionError("重新开启被动推送不得重发"); });
        }

        private static void await(CountDownLatch latch) {
            try { require(latch.await(5, TimeUnit.SECONDS), "等待发送超时"); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
        }

        private static void require(boolean condition, String message) {
            if (!condition) throw new AssertionError(message);
        }
    }
}
