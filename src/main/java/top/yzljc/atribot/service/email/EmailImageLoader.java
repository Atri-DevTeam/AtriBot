package top.yzljc.atribot.service.email;

import top.yzljc.atribot.event.events.EmailMessageEvent;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.util.Base64;
import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName EmailImageLoader
 * @Created_at 2026/09/11
 * @Project AtriMeow
 * @Package top.yzljc.atribot.service.email
 */
public final class EmailImageLoader {
    public static final int MAX_IMAGE_BYTES = 6 * 1024 * 1024;

    private EmailImageLoader() {}

    public static byte[] load(String source, Map<String, byte[]> inlineImages) throws IOException {
        byte[] bytes = inlineImages.get(EmailMessageEvent.normalizeContentId(source));
        if (bytes == null) bytes = inlineImages.get(source);
        if (bytes == null) {
            if (source.regionMatches(true, 0, "cid:", 0, 4)) throw new IOException("内嵌图片缺失或过大");
            if (source.regionMatches(true, 0, "data:", 0, 5)) {
                int comma = source.indexOf(',');
                if (comma < 0 || !source.substring(0, comma).toLowerCase(java.util.Locale.ROOT).matches("data:image/[^;]+;base64")
                        || source.length() - comma > (MAX_IMAGE_BYTES * 4L / 3) + 4) {
                    throw new IOException("不支持或过大的内嵌图片");
                }
                try { bytes = Base64.getDecoder().decode(source.substring(comma + 1)); }
                catch (IllegalArgumentException e) { throw new IOException("内嵌图片编码无效", e); }
            } else {
                URI uri;
                try { uri = URI.create(source.startsWith("//") ? "https:" + source : source); }
                catch (IllegalArgumentException e) { throw new IOException("图片地址无效", e); }
                bytes = download(uri);
            }
        }
        validateImage(bytes);
        return bytes;
    }

    static byte[] download(URI uri) throws IOException {
        for (int redirects = 0; redirects <= 4; redirects++) {
            validateRemoteUri(uri);
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("Accept", "image/*");
            connection.setRequestProperty("User-Agent", "AtriMeow-MailPreview/1.0");
            try {
                int status = connection.getResponseCode();
                if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                    String target = connection.getHeaderField("Location");
                    if (target == null) throw new IOException("图片跳转地址缺失");
                    try { uri = uri.resolve(target); }
                    catch (IllegalArgumentException e) { throw new IOException("图片跳转地址无效", e); }
                    continue;
                }
                if (status != 200) throw new IOException("图片下载失败（HTTP " + status + "）");
                if (connection.getContentLengthLong() > MAX_IMAGE_BYTES) throw new IOException("图片超过 6 MiB");
                try (var input = connection.getInputStream()) {
                    var output = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[8192];
                    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (output.size() + count > MAX_IMAGE_BYTES) throw new IOException("图片超过 6 MiB");
                        if (System.nanoTime() > deadline) throw new IOException("图片下载超时");
                        output.write(buffer, 0, count);
                    }
                    return output.toByteArray();
                }
            } finally { connection.disconnect(); }
        }
        throw new IOException("图片跳转次数过多");
    }

    static void validateRemoteUri(URI uri) throws IOException {
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null) throw new IOException("不支持的图片地址");
        for (InetAddress address : InetAddress.getAllByName(uri.getHost())) {
            byte[] raw = address.getAddress();
            boolean privateRange = raw.length == 16 && (raw[0] & 0xfe) == 0xfc;
            privateRange |= raw.length == 4 && ((raw[0] & 0xff) == 100 && (raw[1] & 0xc0) == 64);
            if (privateRange || address.isAnyLocalAddress() || address.isLoopbackAddress()
                    || address.isLinkLocalAddress() || address.isSiteLocalAddress() || address.isMulticastAddress()) {
                throw new IOException("不读取本机或内网图片地址");
            }
        }
    }

    static void validateImage(byte[] bytes) throws IOException {
        if (bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) throw new IOException("图片为空或超过 6 MiB");
        boolean png = bytes.length >= 8 && bytes[0] == (byte) 137 && bytes[1] == 80 && bytes[2] == 78 && bytes[3] == 71;
        boolean jpeg = bytes.length >= 3 && bytes[0] == (byte) 255 && bytes[1] == (byte) 216 && bytes[2] == (byte) 255;
        String signature = new String(bytes, 0, Math.min(bytes.length, 12), java.nio.charset.StandardCharsets.ISO_8859_1);
        boolean gif = signature.startsWith("GIF87a") || signature.startsWith("GIF89a");
        boolean webp = signature.startsWith("RIFF") && signature.endsWith("WEBP");
        boolean bmp = signature.startsWith("BM");
        if (!(png || jpeg || gif || webp || bmp)) throw new IOException("图片格式不支持或下载内容不是图片");
    }
}
