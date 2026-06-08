package top.yzljc.atribot.webui.official;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class WebUISessionManager {

    private static final AtomicBoolean active = new AtomicBoolean(false);

    public static void start() {
        active.set(true);
        log.info("WebUI 服务已开启");
    }

    public static void stop() {
        active.set(false);
        SseBroadcaster.closeAll();
        log.info("WebUI 服务已关闭，所有连接已断开");
    }

    public static boolean isActive() {
        return active.get();
    }
}
