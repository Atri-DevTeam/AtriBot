package top.yzljc.atribot.function.utils.personal;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.chat.napcat.PrivateMessage;
import top.yzljc.atribot.chat.napcat.impl.MessageSegment;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.EmailMessageEvent;
import top.yzljc.atribot.service.email.EmailImageLoader;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.RejectedExecutionException;

/**
 * @Author YZ_Ljc_
 * @ClassName EmailNotify
 * @Created_at 2026/07/11
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.utils.personal
 */
@Slf4j
public class EmailNotify implements Listener {

    private static final String OWNER = "3199590352";
    // 邮件串行处理，图片下载不阻塞 IMAP IDLE 线程，也不无限堆积图片任务。
    private static final ThreadPoolExecutor WORKER = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(16), Thread.ofPlatform().daemon(true).name("email-preview").factory());
    private static final Sender QQ = new Sender() {
        public String message(List<MessageSegment> content) { return PrivateMessage.chatMessage(OWNER, content); }
        public String forward(List<MessageSegment> nodes, String subject, String summary) {
            return PrivateMessage.forwardMessage(OWNER, nodes, "邮件：" + subject, summary, subject);
        }
    };

    @EventHandler
    public void onEmailReceived(EmailMessageEvent event) {
        try {
            WORKER.execute(() -> {
                try { deliver(event, source -> EmailImageLoader.load(source, event.getInlineImages()), QQ); }
                catch (Exception e) {
                    log.warn("邮件图文通知失败", e);
                    QQ.message(List.of(text(header(event) + "\n" + event.getContentSummary())));
                }
            });
        } catch (RejectedExecutionException e) {
            log.warn("邮件预览队列已满，发送文字摘要");
            QQ.message(List.of(text(header(event) + "\n" + event.getContentSummary())));
        }
    }

    @FunctionalInterface
    interface ImageResolver { byte[] load(String source) throws Exception; }

    interface Sender {
        String message(List<MessageSegment> content);
        String forward(List<MessageSegment> nodes, String subject, String summary);
    }

    static String header(EmailMessageEvent event) {
        return """
                主题: %s
                发件人: %s
                收件人: %s
                接收时间: %s
                抄送: %s
                密信: %s
                附件数量: %s
                """.formatted(
                event.getSubject(),
                event.getAuthors().toString(),
                event.getToRecipients().toString(),
                event.getReceivedDate() == null ? (event.getSentDate() == null ? "未知" : event.getSentDate()) : event.getReceivedDate(),
                event.getCcRecipients(),
                event.getBccRecipients(),
                event.getAttachmentFileNames().size())
                + (event.getAttachmentFileNames().isEmpty() ? "" : "附件: " + String.join("、", event.getAttachmentFileNames()) + "\n")
                + (event.getReadError() == null ? "" : "读取提示: " + event.getReadError() + "\n");
    }

    static List<MessageSegment> prepare(EmailMessageEvent event, ImageResolver resolver) {
        List<MessageSegment> segments = new ArrayList<>();
        addText(segments, header(event));
        Map<String, MessageSegment> imageCache = new HashMap<>();
        int imageBytes = 0;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        var blocks = event.getBodyBlocks();
        for (var block : blocks) {
            if (!block.isImage()) {
                addText(segments, block.text());
                continue;
            }
            String source = block.imageSource();
            MessageSegment image = imageCache.get(source);
            if (image == null) {
                try {
                    if (imageCache.size() >= 12 || imageBytes >= 16 * 1024 * 1024 || System.nanoTime() > deadline) {
                        throw new IllegalStateException("本封邮件图片数量、总大小或下载时间已达上限");
                    }
                    byte[] bytes = resolver.load(source);
                    if (imageBytes + bytes.length > 16 * 1024 * 1024) throw new IllegalStateException("图片总大小超过 16 MiB");
                    imageBytes += bytes.length;
                    image = new MessageSegment("image", Map.of("file", "base64://" + Base64.getEncoder().encodeToString(bytes)));
                } catch (Exception e) {
                    image = text("[图片未显示：" + e.getMessage() + "]"
                            + (source.matches("(?is)^https?://.*") ? "\n" + source : ""));
                }
                // 超限后不继续缓存任意数量的新地址。
                if (imageCache.size() < 12) imageCache.put(source, image);
            }
            if (block.alt() != null && !block.alt().isBlank()) addText(segments, "[图片：" + block.alt() + "]");
            segments.add(image);
        }
        if (segments.size() == 1) segments.add(text("（无可显示正文）"));
        return segments;
    }

    static void deliver(EmailMessageEvent event, ImageResolver resolver, Sender sender) {
        List<MessageSegment> segments = prepare(event, resolver);
        int textLength = segments.stream().filter(s -> s.type().equals("text"))
                .mapToInt(s -> s.data().get("text").toString().length()).sum();
        long imageCount = segments.stream().filter(s -> s.type().equals("image")).count();
        if (textLength <= 2500 && imageCount <= 1) {
            if (sent(sender.message(segments))) return;
            sendSeparately(segments, sender);
            return;
        }
        for (int start = 0; start < segments.size();) {
            List<MessageSegment> batch = new ArrayList<>();
            int weight = 0;
            while (start < segments.size() && batch.size() < 20) {
                MessageSegment segment = segments.get(start);
                int nextWeight = segment.data().values().stream().mapToInt(v -> v.toString().length()).sum();
                if (!batch.isEmpty() && weight + nextWeight > 9 * 1024 * 1024) break;
                batch.add(segment);
                weight += nextWeight;
                start++;
            }
            List<MessageSegment> nodes = batch.stream().map(s -> new MessageSegment("node",
                    Map.<String, Object>of("uin", OWNER, "name", "邮件通知", "content", List.of(s)))).toList();
            if (!sent(sender.forward(nodes, event.getSubject(), event.getContentSummary()))) sendSeparately(batch, sender);
        }
    }

    private static void sendSeparately(List<MessageSegment> segments, Sender sender) {
        for (MessageSegment segment : segments) {
            if (!sent(sender.message(List.of(segment)))) {
                log.warn("邮件通知消息发送失败，类型: {}", segment.type());
                if (segment.type().equals("image")) sender.message(List.of(text("[图片发送失败，其余正文继续发送]")));
            }
        }
    }

    private static boolean sent(String messageId) { return messageId != null && !messageId.isBlank(); }

    private static MessageSegment text(String text) { return new MessageSegment("text", Map.of("text", text)); }

    private static void addText(List<MessageSegment> segments, String value) {
        if (value == null || value.isBlank()) return;
        for (int start = 0; start < value.length();) {
            int end = Math.min(start + 1500, value.length());
            if (end < value.length() && Character.isHighSurrogate(value.charAt(end - 1))) end--;
            segments.add(text(value.substring(start, end)));
            start = end;
        }
    }
}
