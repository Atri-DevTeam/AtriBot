package top.yzljc.atribot.utils.notify;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.database.PendingNoticeDTO;
import top.yzljc.atribot.database.repo.PendingNoticeRepository;

import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/** 群留言始终等待新消息被动发送，不尝试主动消息。 */
@Slf4j
public final class GroupLeaveMessageService {
    public static final int MAX_CONTENT_LENGTH = 16000;
    public static final int MAX_PENDING = 20;
    private static final ReentrantLock[] LOCKS = new ReentrantLock[256];
    private static final AtomicLong LAST_ORDER = new AtomicLong();

    static {
        for (int i = 0; i < LOCKS.length; i++) LOCKS[i] = new ReentrantLock();
    }

    private static ReentrantLock lockFor(String groupId) {
        return LOCKS[Math.floorMod(groupId.hashCode(), LOCKS.length)];
    }

    public static List<LeaveMessage> list(String groupId) {
        return PendingNoticeRepository.listGroupLeaveMessages(groupId).stream()
                .map(note -> new LeaveMessage(note.getId(), note.getContent(), note.getCreateTime(), note.getAttempts()))
                .toList();
    }

    public static String add(String groupId, String content) {
        if (content == null || content.isBlank()) throw new IllegalArgumentException("留言内容不能为空");
        if (content.length() > MAX_CONTENT_LENGTH) throw new IllegalArgumentException("留言不能超过 16000 字符");
        var lock = lockFor(groupId);
        lock.lock();
        try {
            if (list(groupId).size() >= MAX_PENDING) throw new IllegalArgumentException("每群最多保留 20 条待发送留言");
            PendingNoticeDTO note = new PendingNoticeDTO();
            note.setTargetType("OFFICIAL_GROUP");
            note.setTargetId(groupId);
            note.setSource(PendingNoticeRepository.GROUP_LEAVE_MESSAGE_SOURCE);
            // DATETIME 只有秒精度，同秒新增的留言也按保存顺序发送。
            note.setSourceId(String.format("%019d", LAST_ORDER.updateAndGet(previous -> Math.max(previous + 1, System.currentTimeMillis()))));
            note.setContent(content);
            String id = PendingNoticeRepository.enqueue(note);
            if (id == null) throw new IllegalStateException("保存留言失败");
            return id;
        } finally {
            lock.unlock();
        }
    }

    public static boolean delete(String groupId, String id) {
        var lock = lockFor(groupId);
        lock.lock();
        try {
            return PendingNoticeRepository.deleteGroupLeaveMessage(groupId, id);
        } finally {
            lock.unlock();
        }
    }

    /** 同群并发消息仅一个负责发送；发送期间新增和删除也使用同一把锁。 */
    public static void deliver(String groupId, Function<Markdown, String> reply) {
        var lock = lockFor(groupId);
        lock.lock();
        try {
            for (PendingNoticeDTO note : PendingNoticeRepository.listGroupLeaveMessages(groupId)) {
                try {
                    String messageId = reply.apply(new Markdown(note.getContent()));
                    if (messageId == null || messageId.isBlank()) throw new IllegalStateException("被动发送未返回消息 ID");
                    if (!PendingNoticeRepository.markDelivered(note.getId())) {
                        log.error("群留言已发送但送达状态保存失败: id={}", note.getId());
                        break;
                    }
                } catch (Exception e) {
                    PendingNoticeRepository.markAttemptFailed(note.getId(), e.getMessage());
                    log.warn("群留言发送失败，保留待下次消息重试: id={}", note.getId(), e);
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("读取群留言待发送队列失败: group={}", groupId, e);
        } finally {
            lock.unlock();
        }
    }

    public record LeaveMessage(String id, String content, Timestamp createdAt, int attempts) {}
}
