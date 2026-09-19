package top.yzljc.atribot.webui;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;

import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
public class SseBroadcaster {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int MAX_PENDING_EVENTS = 256;
    private static final String CLOSE_EVENT = "__CLOSE__";
    private static final Set<Subscription> clients = ConcurrentHashMap.newKeySet();

    public static void handle(Context ctx) {
        String sessionId = ctx.cookie(WebUISessionManager.SESSION_COOKIE);
        if (!WebUISessionManager.verifySession(sessionId)) {
            ctx.status(401).json(Result.fail(401, "未授权"));
            return;
        }

        Subscription subscription = new Subscription(sessionId);
        clients.add(subscription);
        log.info("[!] 当前网页管理端消息订阅当前连接数: {}", clients.size());

        try {
            // 先注册再复验，覆盖握手期间发生的退出或服务关闭。
            if (!WebUISessionManager.verifySession(sessionId)) {
                ctx.status(401).json(Result.fail(401, "未授权"));
                return;
            }
            var resp = ctx.res();
            resp.setContentType("text/event-stream");
            resp.setHeader("Cache-Control", "no-store");
            resp.setHeader("Connection", "keep-alive");
            PrintWriter w = resp.getWriter();
            w.write(": ok\n\n");
            w.flush();

            while (true) {
                String data = subscription.poll();
                // 检查不续期；空闲连接也会在下一次心跳时结束过期会话。
                if (!WebUISessionManager.verifySession(sessionId)) subscription.expire();
                if (subscription.sessionExpired) {
                    w.write("event: session-expired\ndata: {}\n\n");
                    w.flush();
                    break;
                }
                if (data != null) {
                    if (CLOSE_EVENT.equals(data)) break;
                    w.write("data: " + data + "\n\n");
                } else {
                    w.write(": hb\n\n"); // 心跳
                }
                if (w.checkError()) break;
                w.flush();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.debug("网页消息订阅连接已断开", e);
        } finally {
            subscription.close();
            clients.remove(subscription);
            log.info("[!] 有连接断开，当前网页管理端消息订阅当前连接数: {}", clients.size());
        }
    }

    public static void closeAll() {
        clients.forEach(Subscription::expire);
    }

    public static void closeSession(String sessionId) {
        clients.forEach(subscription -> {
            if (subscription.sessionId.equals(sessionId)) subscription.expire();
        });
    }

    public static void broadcast(String data) {
        clients.forEach(subscription -> subscription.offer(data));
    }

    /** 合并排队中的相同刷新通知，慢客户端重连后从数据库重新同步状态。 */
    private static final class Subscription {
        private final String sessionId;
        private final BlockingQueue<String> queue = new ArrayBlockingQueue<>(MAX_PENDING_EVENTS);
        private final Set<String> pending = new HashSet<>();
        private volatile boolean sessionExpired;
        private boolean closed;

        private Subscription(String sessionId) {
            this.sessionId = sessionId;
        }

        private synchronized void offer(String data) {
            if (closed || pending.contains(data)) return;
            if (!queue.offer(data)) {
                log.warn("网页消息订阅积压超过上限，关闭连接等待客户端重新同步");
                close();
                return;
            }
            pending.add(data);
        }

        private String poll() throws InterruptedException {
            String data = queue.poll(15, TimeUnit.SECONDS);
            synchronized (this) {
                pending.remove(data);
                return closed ? CLOSE_EVENT : data;
            }
        }

        private synchronized void close() {
            closed = true;
            pending.clear();
            queue.clear();
            queue.offer(CLOSE_EVENT);
        }

        private synchronized void expire() {
            sessionExpired = true;
            close();
        }
    }

    public static void broadcastC2CPushStatus(String userOpenId, boolean enabled) {
        try {
            var payload = objectMapper.createObjectNode();
            payload.put("type", "c2c_push_status");
            payload.put("userOpenId", userOpenId);
            payload.put("enabled", enabled);
            broadcast(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            log.warn("上报C2C主动消息更换状态失败: {}", e.getMessage());
        }
    }
}
