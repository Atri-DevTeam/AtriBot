package top.yzljc.atribot.service.email;

import jakarta.mail.*;
import jakarta.mail.event.MessageCountEvent;
import jakarta.mail.event.MessageCountListener;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.angus.mail.imap.IMAPFolder;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.event.EventManager;
import top.yzljc.atribot.event.events.EmailMessageEvent;
import top.yzljc.atribot.service.runtime.ThreadManager;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @Author YZ_Ljc_
 * @ClassName IMAP
 * @Created_at 2026/07/11
 * @Project AtriMeow
 * @Package top.yzljc.atribot.service.email
 */
@Slf4j
public class IMAP {

    /** 重连前等待秒数 */
    private static final int RECONNECT_DELAY_SECONDS = 1;

    /** Session 属性（仅创建一次，全生命周期复用） */
    private final Properties sessionProperties;

    /**
     * Session —— 仅创建一次，全生命周期复用。
     * 即使发生重连也不会重新创建。
     */
    private volatile Session session;

    /**
     * Store —— 每次连接/重连时重新创建。
     * 不允许复用已关闭的 Store。
     */
    private volatile Store store;

    /**
     * IMAP Folder —— 每次连接/重连时重新获取并打开。
     * 不允许复用已关闭的 Folder。
     */
    private volatile IMAPFolder folder;

    /** IDLE 循环运行标志 */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** IDLE 监听任务 */
    private volatile Future<?> idleTask;

    /**
     * 构造 IMAP 邮件监听器。
     */
    public IMAP() {
        this.sessionProperties = Config.getInstance().getEmailProperties();
    }

    private Session getSession() {
        if (session == null) {
            synchronized (this) {
                if (session == null) {
                    session = Session.getInstance(sessionProperties);
                    session.setDebug(false);
                    log.info("Jakarta Mail Session 已创建");
                }
            }
        }
        return session;
    }

    public synchronized void connect() throws MessagingException {
        closeResources();

        // 创建新的 Store
        Store newStore = getSession().getStore(sessionProperties.getProperty("mail.store.protocol", "imap"));
        try {
            Config config = Config.getInstance();
            newStore.connect(config.getEmailUsername(), config.getEmailPassword());
            log.info("IMAP Store 连接成功: {}", config.getEmailUsername());
        } catch (MessagingException e) {
            try {
                newStore.close();
            } catch (Exception ignored) {
            }
            throw e;
        }
        this.store = newStore;

        IMAPFolder newFolder;
        try {
            newFolder = (IMAPFolder) newStore.getFolder("INBOX");
            newFolder.open(Folder.READ_WRITE);
            log.info("INBOX 已打开 (READ_WRITE)");
        } catch (MessagingException e) {
            closeResources();
            throw e;
        }
        this.folder = newFolder;
        registerMessageCountListener(newFolder);

        log.info("IMAP 连接建立完成，当前邮件数: {}", newFolder.getMessageCount());
    }

    private void registerMessageCountListener(IMAPFolder targetFolder) {
        targetFolder.addMessageCountListener(new MessageCountListener() {
            @Override
            public void messagesAdded(MessageCountEvent event) {
                Message[] messages = event.getMessages();
                log.info("IMAP 收到 {} 封新邮件，正在派发", messages.length);
                for (Message message : messages) {
                    EventManager.getInstance().callEvent(new EmailMessageEvent(message));
                }
            }

            @Override
            public void messagesRemoved(MessageCountEvent event) {
                // 邮件删除不派发业务事件
            }
        });
    }

    public synchronized void start() {
        if (!running.compareAndSet(false, true)) {
            log.warn("IMAP 监听已在运行中，忽略重复启动");
            return;
        }

        log.info("正在启动 IMAP IDLE 监听...");
        try {
            idleTask = ThreadManager.setExecute(this::idleLoop);
        } catch (RuntimeException e) {
            running.set(false);
            throw e;
        }
        log.info("IMAP IDLE 监听已启动");
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) {
            log.warn("IMAP 监听未在运行中，忽略重复停止");
            return;
        }

        log.info("正在停止 IMAP IDLE 监听...");

        // 关闭资源（会触发 idle() 抛出异常从而退出阻塞）
        closeResources();

        Future<?> task = idleTask;
        if (task != null) {
            task.cancel(true);
            idleTask = null;
        }

        log.info("IMAP IDLE 监听已停止");
    }

    public void close() {
        stop();
        closeResources();
        synchronized (this) {
            session = null;
        }
        log.info("IMAP 所有资源已完全释放");
    }

    private void idleLoop() {
        log.info("IDLE 监听循环开始");

        while (running.get()) {
            try {
                // 确保已连接
                if (folder == null || !folder.isOpen()) {
                    connect();
                    if (!running.get()) {
                        break;
                    }
                }

                folder.idle();
                // idle() 正常返回（非异常），可能原因：收到新邮件、服务器心跳等

            } catch (FolderClosedException | StoreClosedException e) {
                closeResources();
                if (running.get()) {
                    sleepBeforeReconnect();
                }

            } catch (MessagingException e) {
                log.warn("IMAP 协议异常，将尝试重连", e);
                closeResources();
                if (running.get()) {
                    sleepBeforeReconnect();
                }

            } catch (Exception e) {
                // 检查是否为连接相关异常（SocketException、EOFException、SSL 异常等）
                Throwable root = unwrapCause(e);
                if (isConnectionException(root)) {
                    log.warn("网络连接异常 ({}): {}，将尝试重连",
                            root.getClass().getSimpleName(), root.getMessage());
                } else {
                    log.error("IDLE 循环中发生未预期的异常", e);
                }
                closeResources();
                if (running.get()) {
                    sleepBeforeReconnect();
                }
            }
        }

        closeResources();
        log.info("IDLE 监听退出");
    }

    private synchronized void closeResources() {
        // 先关闭 Folder
        if (folder != null) {
            try {
                if (folder.isOpen()) {
                    folder.close(false);
                }
            } catch (Exception e) {
                log.debug("关闭 Folder 时异常（可忽略）: {}", e.getMessage());
            }
            folder = null;
        }

        // 再关闭 Store
        if (store != null) {
            try {
                if (store.isConnected()) {
                    store.close();
                }
            } catch (Exception ignored) {
            }
            store = null;
        }
    }

    private boolean isConnectionException(Throwable t) {
        if (t == null) {
            return false;
        }
        return t instanceof IOException || t.getClass().getName().contains("SSL");
    }

    private Throwable unwrapCause(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    private void sleepBeforeReconnect() {
        try {
            Thread.sleep(RECONNECT_DELAY_SECONDS * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("重连等待被中断");
        }
    }
}
