package top.yzljc.atribot.function.background;

import java.util.Set;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Lock;

import com.google.common.util.concurrent.Striped;
import lombok.extern.slf4j.Slf4j;

import top.yzljc.atricore.command.QQCommandSender;
import top.yzljc.atribot.database.repo.BackgroundRepository;
import top.yzljc.atribot.function.background.BackgroundSettings.Submission;
import top.yzljc.atribot.utils.HashUtils;
import top.yzljc.atribot.utils.FormatTools;
import top.yzljc.atribot.utils.tools.Alert;

/**
 * @Author YZ_Ljc_
 * @ClassName BackgroundService
 * @Created_at 2026/10/11
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.background
 */
@Slf4j
public final class BackgroundService {
    private static final Striped<Lock> LOCKS = Striped.lock(64);
    private static final Semaphore UPLOADS = new Semaphore(2);

    private BackgroundService() {
    }

    public static void submit(QQCommandSender sender, String url) throws Exception {
        if (!UPLOADS.tryAcquire()) throw new IllegalArgumentException("背景图片处理中，请稍后重试");
        String userId = sender.getUserId();
        Lock lock = LOCKS.get(userId);
        lock.lock();
        try {
            BackgroundSettings old = BackgroundRepository.get(userId);
            if (old.submission() != null && "PENDING".equals(old.submission().status()))
                throw new IllegalArgumentException("已有背景等待审核，请等待审核后再提交");
            String id = UUID.randomUUID().toString();
            var image = BackgroundClient.upload(userId, id, url);
            Submission submission = new Submission(id, "PENDING", sender.getUsername(), sender.getGroupId(),
                    sender.getPlatform().name(), image.path("width").asInt(), image.path("height").asInt(),
                    image.path("size").asLong(), System.currentTimeMillis(), 0, null, null);
            BackgroundSettings next = BackgroundRepository.update(userId, current -> current.submit(submission));
            cleanup(userId, old, next);
            try {
                Alert.notify("收到背景投稿: 编号 " + id +
                        " 来自用户: " + submission.uploaderName() +
                        " (" + submission.platform() + ": " + userId + ")" +
                        (submission.groupId() != null && !submission.groupId().isBlank() ? " 群聊: " + submission.groupId() : "") +
                        " 尺寸: " + submission.width() + "x" + submission.height() +
                        " 提交时间: " + FormatTools.formatTimestampMilli(submission.createdAt()));
            } catch (Exception e) {
                log.warn("发送背景投稿提醒失败: user={}, id={}", userId, id, e);
            }
            sender.sendMessage("背景已提交审核，编号：" + id.substring(0, 8) + "。发送 /bg 可查看审核状态。");
        } finally {
            lock.unlock();
            UPLOADS.release();
        }
    }

    public static void review(String userId, String id, String status, String remark) throws Exception {
        Lock lock = LOCKS.get(userId);
        lock.lock();
        try {
            BackgroundSettings old = BackgroundRepository.get(userId);
            if (old.submission() != null && old.submission().id().equals(id) && old.submission().status().equals(status))
                return;
            BackgroundSettings next = BackgroundRepository.update(userId, value -> value.review(id, status, remark, "WebUI"));
            cleanup(userId, old, next);
            log.info("背景审核: user={}, id={}, status={}", userId, id, status);
        } finally {
            lock.unlock();
        }
    }

    public static void delete(String userId, String id) throws Exception {
        Lock lock = LOCKS.get(userId);
        lock.lock();
        try {
            BackgroundSettings old = BackgroundRepository.get(userId);
            BackgroundSettings next = BackgroundRepository.update(userId, value -> value.delete(id));
            cleanup(userId, old, next);
        } finally {
            lock.unlock();
        }
    }

    public static void clear(String userId) throws Exception {
        Lock lock = LOCKS.get(userId);
        lock.lock();
        try {
            BackgroundSettings old = BackgroundRepository.get(userId);
            BackgroundSettings next = BackgroundRepository.update(userId, value -> BackgroundSettings.EMPTY);
            cleanup(userId, old, next);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 仅返回当前生效的已审核背景标识。
     *
     * @param userId 官机用户 OpenID
     * @return 已通过背景 UUID，未设置时为 null
     * @throws Exception 用户设置读取失败
     */
    public static String approvedId(String userId) throws Exception {
        return BackgroundRepository.get(userId).activeId();
    }

    /**
     * 向图片请求写入已审核背景引用；未设置或读取失败时保留默认背景
     *
     * @param request 本次请求的可变参数
     * @param userId  官机用户 OpenID；为 null 时不读取个人设置
     */
    public static void applyApprovedBackground(Map<String, Object> request, String userId) {
        request.remove("background");
        if (userId == null || userId.isBlank()) return;
        try {
            String id = approvedId(userId);
            if (id != null) request.put("background", Map.of(
                    "owner", HashUtils.sha256Hex(userId.getBytes(StandardCharsets.UTF_8)), "id", id));
        } catch (Exception e) {
            log.warn("读取个人背景失败，本次使用默认背景: user={}", userId, e);
        }
    }

    private static void cleanup(String userId, BackgroundSettings old, BackgroundSettings next) {
        Set<String> retained = next.retainedIds();
        for (String id : old.retainedIds()) {
            if (retained.contains(id)) continue;
            try {
                BackgroundClient.delete(userId, id);
            } catch (Exception e) {
                log.warn("背景文件清理失败，将在后续操作时重试: user={}, id={}", userId, id, e);
            }
        }
        try {
            BackgroundClient.retain(userId, retained);
        } catch (Exception e) {
            log.warn("背景存储引用同步失败: user={}", userId, e);
        }
    }
}
