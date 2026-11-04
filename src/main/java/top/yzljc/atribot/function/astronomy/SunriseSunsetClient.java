package top.yzljc.atribot.function.astronomy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;

/**
 * @Author YZ_Ljc_
 * @ClassName SunriseSunsetClient
 * @Created_at 2026/10/28
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.astronomy
 */
public final class SunriseSunsetClient {
    static final ObjectMapper JSON = new ObjectMapper();
    private final URI endpoint;
    private final HttpClient http;

    public SunriseSunsetClient() {
        this(URI.create("https://api.sunrise-sunset.org/v2"),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    SunriseSunsetClient(URI endpoint, HttpClient http) { this.endpoint = endpoint; this.http = http; }

    public SunriseSunsetResponse query(AstronomyRegion region, LocalDate date) throws IOException, InterruptedException {
        var uri = URI.create(endpoint + "?lat=" + region.getLat() + "&lng=" + region.getLng()
                + "&date=" + date + "&tz=Asia%2FShanghai");
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json").header("User-Agent", "AtriBot-Astronomy/1.0").GET().build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("天象接口 HTTP " + response.statusCode());
        if (response.body().length() > 32768) throw new IOException("天象接口响应过大");
        var tree = JSON.readTree(response.body());
        if (tree == null || !tree.isObject() || !tree.has("sunrise") || !tree.has("moon_phase") || tree.has("error"))
            throw new IOException("天象接口响应无效");
        var result = JSON.treeToValue(tree, SunriseSunsetResponse.class);
        result.normalize(date, region);
        return result;
    }
}
