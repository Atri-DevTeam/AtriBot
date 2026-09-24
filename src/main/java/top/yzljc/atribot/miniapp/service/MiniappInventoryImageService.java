package top.yzljc.atribot.miniapp.service;

import top.yzljc.atribot.function.impl.drawitem.LootDao;
import top.yzljc.atribot.function.impl.drawitem.LootService;
import top.yzljc.atribot.miniapp.MiniappSessions;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.function.Function;

/**
 * @Author YZ_Ljc_
 * @ClassName MiniappInventoryImageService
 * @Created_at 2026/09/19
 * @Project AtriMeow
 * @Package top.yzljc.atribot.miniapp
 */
public final class MiniappInventoryImageService {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final int MAX_BYTES = 25 * 1024 * 1024;
    private static final Set<String> TYPES = Set.of("image/png", "image/jpeg", "image/webp", "image/gif");
    private final Function<String, LootDao> renderer;
    private final Semaphore rendering = new Semaphore(2);

    public record Picture(byte[] bytes, String contentType) {
    }

    public MiniappInventoryImageService() {
        this(LootService::renderOverviewCard);
    }

    public MiniappInventoryImageService(Function<String, LootDao> renderer) {
        this.renderer = renderer;
    }

    public Picture render(MiniappSessions.Identity identity) throws IOException, InterruptedException {
        if (!rendering.tryAcquire()) throw new io.javalin.http.TooManyRequestsResponse("INVENTORY_RENDER_BUSY");
        try {
            LootDao result = renderer.apply(identity.userId());
            if (result == null || !result.success() || result.image() == null || result.image().isError()
                    || result.image().url() == null) throw new IOException("Inventory render failed");
            URI uri;
            try {
                uri = URI.create(result.image().url());
            } catch (IllegalArgumentException e) {
                throw new IOException("Invalid image URL", e);
            }
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null) throw new IOException("Invalid image URL");

            var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(25)).GET().build();
            var response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());
            String type = response.headers().firstValue("Content-Type").orElse("").split(";", 2)[0].trim().toLowerCase(java.util.Locale.ROOT);
            byte[] bytes = response.body();
            if (response.statusCode() != 200 || !TYPES.contains(type) || bytes.length == 0 || bytes.length > MAX_BYTES)
                throw new IOException("Invalid inventory image response");
            return new Picture(bytes, type);
        } finally {
            rendering.release();
        }
    }
}
