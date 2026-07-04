package top.yzljc.atribot.webui.impl;

import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;

import java.io.PrintWriter;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Slf4j
public class SseBroadcaster {

    private static final Set<BlockingQueue<String>> clients = ConcurrentHashMap.newKeySet();

    public static void handle(Context ctx) {
        var resp = ctx.res();
        resp.setContentType("text/event-stream");
        resp.setHeader("Cache-Control", "no-cache");
        resp.setHeader("Connection", "keep-alive");
        try { resp.flushBuffer(); } catch (Exception ignored) {}

        BlockingQueue<String> queue = new LinkedBlockingQueue<>();
        clients.add(queue);
        log.debug("SSE 客户端连接, 当前连接数: {}", clients.size());

        try {
            PrintWriter w = resp.getWriter();
            w.write(": ok\n\n");
            w.flush();

            while (true) {
                String data = queue.poll(15, TimeUnit.SECONDS);
                if (data != null) {
                    if ("__CLOSE__".equals(data)) break;
                    w.write("data: " + data + "\n\n");
                } else {
                    w.write(": hb\n\n"); // 心跳
                }
                if (w.checkError()) break;
                w.flush();
            }
        } catch (Exception _) {
        } finally {
            clients.remove(queue);
            log.debug("SSE 客户端断开, 当前连接数: {}", clients.size());
        }
    }

    public static void closeAll() {
        for (BlockingQueue<String> queue : clients) {
            queue.offer("__CLOSE__");
        }
    }

    public static void broadcast(String data) {
        for (BlockingQueue<String> queue : clients) {
            queue.offer(data);
        }
    }
}
