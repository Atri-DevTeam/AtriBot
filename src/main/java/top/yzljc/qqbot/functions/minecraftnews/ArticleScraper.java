package top.yzljc.qqbot.functions.minecraftnews;

import net.dankito.readability4j.Article;
import net.dankito.readability4j.Readability4J;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

public class ArticleScraper {

    private static final Logger log = LoggerFactory.getLogger(ArticleScraper.class);

    private static final RestClient restClient = RestClient.builder()
            .requestFactory(new JdkClientHttpRequestFactory(
                    HttpClient.newBuilder()
                            .version(HttpClient.Version.HTTP_2)
                            .followRedirects(HttpClient.Redirect.NORMAL)
                            .connectTimeout(Duration.ofSeconds(10))
                            .build()
            ))
            .defaultHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .defaultHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .defaultHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .build();

    public static String fetchPureText(String articleUrl) {
        if (articleUrl == null || articleUrl.isEmpty()) return "";

        try {
            String html = restClient.get()
                    .uri(articleUrl)
                    .retrieve()
                    .body(String.class);

            Readability4J readability4J = new Readability4J(articleUrl, html);
            Article article = readability4J.parse();

            String pureText = article.getTextContent();

            if (pureText == null || pureText.trim().isEmpty()) {
                pureText = Jsoup.parse(html).body().text();
            }

            pureText = pureText.replaceAll("(?m)^[ \t]*\r?\n", "");

            return pureText.trim();

        } catch (Exception e) {
            log.warn("ArticleScraper抓取文章纯文本失败: {}", articleUrl, e);
            return "";
        }
    }
}