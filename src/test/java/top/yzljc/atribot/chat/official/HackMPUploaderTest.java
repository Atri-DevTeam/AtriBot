package top.yzljc.atribot.chat.official;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @Author YZ_Ljc_
 * @ClassName HackMPUploaderTest
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atribot.chat.official
 */
class HackMPUploaderTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String FILES = "https://api.example.test/v2/groups/test-group/files";
    private static final URI UPLOAD = URI.create("https://storage.example.test/image?signature=upload");
    private static final String RAW = "https://storage.example.test/image?q-signature=a%2Fb%2Bc&x=1";

    @Test
    void placeholderIsCompletedBeforeActualImageOverwritesSameAddress() throws Exception {
        byte[] image = png();
        List<String> order = new ArrayList<>();
        List<Map<String, Object>> bodies = new ArrayList<>();
        String url = HackMPUploader.upload(image, FILES, (endpoint, body) -> {
            bodies.add(body);
            if (endpoint.endsWith("/upload_prepare")) {
                order.add("prepare");
                assertEquals(1, body.get("file_type"));
                assertEquals("image.webp", body.get("file_name"));
                return prepared();
            }
            if (endpoint.endsWith("/upload_part_finish")) {
                assertEquals("placeholder", order.getLast());
                assertEquals(1, body.get("part_index"));
                assertEquals("upload-id", body.get("upload_id"));
                order.add("finish");
                return MAPPER.createObjectNode();
            }
            assertEquals(FILES, endpoint);
            assertEquals("finish", order.getLast());
            assertEquals(false, body.get("srv_send_msg"));
            order.add("merge");
            return MAPPER.valueToTree(Map.of("raw_url", RAW));
        }, (endpoint, data, scene) -> {
            assertEquals(UPLOAD, endpoint);
            if (order.getLast().equals("prepare")) {
                assertEquals("image/webp", HackMPUploader.imageContentType(data));
                assertEquals(bodies.getFirst().get("file_size"), data.length);
                assertEquals(bodies.getFirst().get("md5"), hash("MD5", data));
                assertEquals(bodies.getFirst().get("sha1"), hash("SHA-1", data));
                assertFalse(java.util.Arrays.equals(image, data));
                order.add("placeholder");
            } else {
                assertEquals("merge", order.getLast());
                assertArrayEquals(image, data);
                order.add("actual");
            }
            return 200;
        });
        assertEquals(List.of("prepare", "placeholder", "finish", "merge", "actual"), order);
        assertEquals(RAW + "&response-content-type=image%2Fpng", url);
    }

    @Test
    void failedOverwriteDoesNotReturnPlaceholderUrl() throws Exception {
        AtomicInteger puts = new AtomicInteger();
        assertThrows(IOException.class, () -> HackMPUploader.upload(png(), FILES, (endpoint, body) -> {
            if (endpoint.endsWith("/upload_prepare")) return prepared();
            return endpoint.endsWith("/files") ? MAPPER.valueToTree(Map.of("raw_url", RAW)) : MAPPER.createObjectNode();
        }, (endpoint, image, scene) -> puts.incrementAndGet() == 1 ? 200 : 403));
        assertEquals(2, puts.get());
    }

    @Test
    void missingDownloadUrlStopsBeforeOverwrite() throws Exception {
        AtomicInteger puts = new AtomicInteger();
        assertThrows(IOException.class, () -> HackMPUploader.upload(png(), FILES, (endpoint, body) -> {
            if (endpoint.endsWith("/upload_prepare")) return prepared();
            return MAPPER.createObjectNode();
        }, (endpoint, image, scene) -> { puts.incrementAndGet(); return 200; }));
        assertEquals(1, puts.get());
    }

    @Test
    void failedPlaceholderDoesNotConfirmOrMerge() throws Exception {
        AtomicInteger posts = new AtomicInteger();
        assertThrows(IOException.class, () -> HackMPUploader.upload(png(), FILES, (endpoint, body) -> {
            assertEquals(1, posts.incrementAndGet());
            assertTrue(endpoint.endsWith("/upload_prepare"));
            return prepared();
        }, (endpoint, image, scene) -> 500));
    }

    @Test
    void downloadContentTypeReplacementPreservesSignatureEncoding() throws Exception {
        assertEquals(RAW + "&response-content-type=image%2Fwebp#fragment",
                HackMPUploader.downloadUrl(RAW + "&response-content-type=old#fragment", "image/webp"));
        assertEquals("https://storage.example.test/image?response-content-type=image%2Fjpeg",
                HackMPUploader.downloadUrl("https://storage.example.test/image", "image/jpeg"));
        assertThrows(IOException.class, () -> HackMPUploader.downloadUrl("file:///tmp/image", "image/png"));
    }

    @Test
    void invalidInputIsRejectedBeforeAnyRequests() {
        for (byte[] invalid : List.of(new byte[0], new byte[]{1, 2, 3}, new byte[20 * 1024 * 1024 + 1])) {
            assertThrows(IllegalArgumentException.class, () -> HackMPUploader.upload(invalid, FILES,
                    (endpoint, body) -> fail("无效图片不应申请地址"),
                    (endpoint, image, scene) -> fail("无效图片不应上传")));
        }
        assertNull(HackMPUploader.upload(null));
    }

    @Test
    void webpInputUsesWebpDownloadType() throws Exception {
        byte[] webp = Base64.getDecoder().decode("UklGRhoAAABXRUJQVlA4TA4AAAAvAAAAAAcQEf0PRET/Aw==");
        String result = HackMPUploader.upload(webp, FILES, (endpoint, body) -> {
            if (endpoint.endsWith("/upload_prepare")) return prepared();
            return endpoint.endsWith("/files") ? MAPPER.valueToTree(Map.of("raw_url", RAW)) : MAPPER.createObjectNode();
        }, (endpoint, image, scene) -> 200);
        assertTrue(result.endsWith("response-content-type=image%2Fwebp"));
    }

    private static JsonNode prepared() {
        return MAPPER.valueToTree(Map.of("upload_id", "upload-id", "block_size", 5_000_000,
                "parts", List.of(Map.of("index", 0, "presigned_url", UPLOAD.toString()))));
    }

    private static byte[] png() throws IOException {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        return output.toByteArray();
    }

    private static String hash(String algorithm, byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(bytes));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
