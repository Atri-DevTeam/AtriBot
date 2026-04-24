package top.yzljc.qqbot.functions.minecraftnews;

import net.dankito.readability4j.Article;
import net.dankito.readability4j.Readability4J;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArticleScraper {
    private static final Logger log = LoggerFactory.getLogger(ArticleScraper.class);

    public static String fetchPureText(String articleUrl) {
        if (articleUrl == null || articleUrl.isEmpty()) return "";

        try {
            String html = Jsoup.connect(articleUrl)
                    .timeout(30000)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .header("Connection", "keep-alive")
                    .header("Accept-Encoding", "gzip, deflate, br")
                    .ignoreHttpErrors(true)
                    .ignoreContentType(true)
                    .execute()
                    .body();

            Readability4J readability4J = new Readability4J(articleUrl, html);
            Article article = readability4J.parse();

            String pureText = article.getTextContent();

            if (pureText == null || pureText.trim().isEmpty()) {
                pureText = Jsoup.parse(html).body().text();
            }

            pureText = pureText.replaceAll("(?m)^[ \t]*\r?\n", "");

            if (pureText.length() > 30000000) {
                pureText = pureText.substring(0, 30000000);
            }

            return pureText.trim();

        } catch (Exception e) {
            log.warn("Readability抓取文章纯文本失败: {}", articleUrl, e);
            return "";
        }
    }
}