package top.yzljc.atribot.event.events;

import jakarta.mail.Address;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeUtility;
import lombok.Getter;
import org.jsoup.Jsoup;
import top.yzljc.atribot.event.Event;
import top.yzljc.atribot.service.email.EmailBodyPreview;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @Author YZ_Ljc_
 * @ClassName EmailMessageEvent
 * @Created_at 2026/07/11
 * @Project AtriMeow
 * @Package top.yzljc.atribot.event.events
 */
@Getter
public class EmailMessageEvent extends Event {

    private static final int SUMMARY_MAX_LENGTH = 600;
    private static final int MAX_INLINE_IMAGE_BYTES = 6 * 1024 * 1024;
    private static final int MAX_INLINE_TOTAL_BYTES = 16 * 1024 * 1024;
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private final Message message;

    private final String subject;
    private final Set<String> authors;
    private final Set<String> toRecipients;
    private final Set<String> ccRecipients;
    private final Set<String> bccRecipients;
    private final Date sentDate;
    private final Date receivedDate;
    private final String contentType;
    private final boolean multipart;
    private final String plainText;
    private final String htmlText;
    private final String contentSummary;
    private final List<String> attachmentFileNames;
    private final Map<String, byte[]> inlineImages;
    private final List<EmailBodyPreview.Block> bodyBlocks;
    private final String readError;

    public EmailMessageEvent(Message message) {
        this.message = message;
        this.subject = readSubject(message);
        this.authors = readAddresses(message::getFrom);
        this.toRecipients = readAddresses(() -> message.getRecipients(Message.RecipientType.TO));
        this.ccRecipients = readAddresses(() -> message.getRecipients(Message.RecipientType.CC));
        this.bccRecipients = readAddresses(() -> message.getRecipients(Message.RecipientType.BCC));
        this.sentDate = readDate(message::getSentDate);
        this.receivedDate = readDate(message::getReceivedDate);
        this.contentType = readContentType(message);

        MailContent content = new MailContent();
        String error = null;
        try {
            collectContent(message, content);
        } catch (Exception e) {
            error = e.getMessage();
        }

        this.multipart = content.multipart;
        this.plainText = joinContent(content.plainTextParts);
        this.htmlText = joinContent(content.htmlTextParts);
        this.contentSummary = buildSummary(plainText, htmlText);
        this.attachmentFileNames = Collections.unmodifiableList(content.attachmentFileNames);
        this.inlineImages = Collections.unmodifiableMap(content.inlineImages);
        this.bodyBlocks = List.copyOf(content.bodyBlocks);
        this.readError = error != null ? error : content.error;
    }

    public Date getSentDate() {
        return sentDate == null ? null : new Date(sentDate.getTime());
    }

    public Date getReceivedDate() {
        return receivedDate == null ? null : new Date(receivedDate.getTime());
    }

    private static String readSubject(Message message) {
        try {
            String subject = message.getSubject();
            return subject == null ? "" : subject;
        } catch (MessagingException e) {
            return "";
        }
    }

    private static Set<String> readAddresses(AddressReader reader) {
        try {
            Address[] addresses = reader.read();
            if (addresses == null || addresses.length == 0) {
                return Collections.emptySet();
            }

            Set<String> result = new LinkedHashSet<>();
            for (Address address : addresses) {
                if (address != null) {
                    result.add(formatAddress(address));
                }
            }
            return Collections.unmodifiableSet(result);
        } catch (MessagingException e) {
            return Collections.emptySet();
        }
    }

    private static String formatAddress(Address address) {
        if (address instanceof InternetAddress internetAddress) {
            String emailAddress = internetAddress.getAddress();
            String personal = decodeText(internetAddress.getPersonal());
            if (personal == null || personal.isBlank()) {
                return emailAddress == null ? "" : emailAddress;
            }
            if (emailAddress == null || emailAddress.isBlank()) {
                return personal;
            }
            return personal + " <" + emailAddress + ">";
        }
        return decodeText(address.toString());
    }

    private static Date readDate(DateReader reader) {
        try {
            Date date = reader.read();
            return date == null ? null : new Date(date.getTime());
        } catch (MessagingException e) {
            return null;
        }
    }

    private static String readContentType(Part part) {
        try {
            String contentType = part.getContentType();
            return contentType == null ? "" : contentType;
        } catch (MessagingException e) {
            return "";
        }
    }

    private static void collectContent(Part part, MailContent content) throws MessagingException, IOException {
        String fileName = decodeFileName(part.getFileName());
        boolean attachment = Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition());
        String[] ids = part.getHeader("Content-ID");
        String[] locations = part.getHeader("Content-Location");
        boolean inlineImage = !attachment && part.isMimeType("image/*")
                && ((ids != null && ids.length > 0) || (locations != null && locations.length > 0));
        if (!fileName.isBlank() && !inlineImage) {
            content.attachmentFileNames.add(fileName);
        }

        if (attachment) return;

        if (inlineImage) {
            try (var input = part.getInputStream()) {
                int limit = Math.min(MAX_INLINE_IMAGE_BYTES, MAX_INLINE_TOTAL_BYTES - content.imageBytes);
                byte[] bytes = input.readNBytes(limit + 1);
                if (bytes.length > limit) {
                    content.error = "部分内嵌图片超过大小限制，正文仍可查看";
                } else {
                    content.imageBytes += bytes.length;
                    if (ids != null) for (String id : ids) content.inlineImages.put(normalizeContentId(id), bytes);
                    if (locations != null) for (String location : locations) content.inlineImages.put(location.trim(), bytes);
                }
            }
            return;
        }

        if (part.isMimeType("multipart/*")) {
            content.multipart = true;
            Object rawContent = part.getContent();
            if (rawContent instanceof Multipart multipartContent) {
                List<String> selectedPlain = new ArrayList<>();
                List<String> selectedHtml = new ArrayList<>();
                List<EmailBodyPreview.Block> selectedPlainBlocks = List.of();
                List<EmailBodyPreview.Block> selectedHtmlBlocks = List.of();
                for (int i = 0; i < multipartContent.getCount(); i++) {
                    try {
                        if (part.isMimeType("multipart/alternative")) {
                            MailContent alternative = new MailContent();
                            collectContent(multipartContent.getBodyPart(i), alternative);
                            if (!alternative.plainTextParts.isEmpty()) {
                                selectedPlain.clear();
                                selectedPlain.addAll(alternative.plainTextParts);
                                selectedPlainBlocks = alternative.bodyBlocks;
                            }
                            if (!alternative.htmlTextParts.isEmpty()) {
                                selectedHtml.clear();
                                selectedHtml.addAll(alternative.htmlTextParts);
                                selectedHtmlBlocks = alternative.bodyBlocks;
                            }
                            if (content.imageBytes + alternative.imageBytes <= MAX_INLINE_TOTAL_BYTES) {
                                content.inlineImages.putAll(alternative.inlineImages);
                                content.imageBytes += alternative.imageBytes;
                            } else content.error = "部分内嵌图片超过大小限制";
                            content.attachmentFileNames.addAll(alternative.attachmentFileNames);
                            if (alternative.error != null) content.error = alternative.error;
                        } else collectContent(multipartContent.getBodyPart(i), content);
                    } catch (Exception e) {
                        content.error = "部分邮件内容读取失败: " + e.getMessage();
                    }
                }
                content.plainTextParts.addAll(selectedPlain);
                content.htmlTextParts.addAll(selectedHtml);
                content.bodyBlocks.addAll(selectedHtmlBlocks.isEmpty() ? selectedPlainBlocks : selectedHtmlBlocks);
            }
            return;
        }

        if (part.isMimeType("text/plain")) {
            String text = String.valueOf(part.getContent());
            content.plainTextParts.add(text);
            content.bodyBlocks.add(EmailBodyPreview.Block.text(text));
            return;
        }

        if (part.isMimeType("text/html")) {
            String html = String.valueOf(part.getContent());
            content.htmlTextParts.add(html);
            content.bodyBlocks.addAll(EmailBodyPreview.parse(html, ""));
            return;
        }

        if (part.isMimeType("message/rfc822") && part.getContent() instanceof Part nestedPart) {
            collectContent(nestedPart, content);
        }
    }

    private static String decodeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }
        return decodeText(fileName);
    }

    private static String decodeText(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        try {
            return MimeUtility.decodeText(text);
        } catch (Exception e) {
            return text;
        }
    }

    private static String joinContent(List<String> parts) {
        return String.join("\n", parts).trim();
    }

    private static String buildSummary(String plainText, String htmlText) {
        String source = !plainText.isBlank() ? plainText : stripHtml(htmlText);
        String summary = WHITESPACE_PATTERN.matcher(source).replaceAll(" ").trim();
        if (summary.length() <= SUMMARY_MAX_LENGTH) {
            return summary;
        }
        return summary.substring(0, SUMMARY_MAX_LENGTH);
    }

    private static String stripHtml(String html) {
        var document = Jsoup.parse(html);
        document.select("head,style,script,noscript,template,[hidden]").remove();
        return document.body().text();
    }

    public static String normalizeContentId(String value) {
        String id = value.trim();
        if (id.regionMatches(true, 0, "cid:", 0, 4)) id = id.substring(4);
        try {
            id = java.net.URLDecoder.decode(id.replace("+", "%2B"), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {}
        if (id.startsWith("<") && id.endsWith(">")) id = id.substring(1, id.length() - 1);
        return id;
    }

    @FunctionalInterface
    private interface AddressReader {
        Address[] read() throws MessagingException;
    }

    @FunctionalInterface
    private interface DateReader {
        Date read() throws MessagingException;
    }

    private static final class MailContent {
        private final List<String> plainTextParts = new ArrayList<>();
        private final List<String> htmlTextParts = new ArrayList<>();
        private final List<String> attachmentFileNames = new ArrayList<>();
        private final Map<String, byte[]> inlineImages = new LinkedHashMap<>();
        private final List<EmailBodyPreview.Block> bodyBlocks = new ArrayList<>();
        private int imageBytes;
        private String error;
        private boolean multipart;
    }
}
