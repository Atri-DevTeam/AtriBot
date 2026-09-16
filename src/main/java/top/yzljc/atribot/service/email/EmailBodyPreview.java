package top.yzljc.atribot.service.email;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @Author YZ_Ljc_
 * @ClassName EmailBodyPreview
 * @Created_at 2026/09/11
 * @Project AtriMeow
 * @Package top.yzljc.atribot.service.email
 */
public final class EmailBodyPreview {
    private static final Set<String> BLOCKS = Set.of("p", "div", "section", "article", "h1", "h2", "h3",
            "h4", "h5", "h6", "li", "ul", "ol", "tr", "table", "blockquote", "pre", "hr");
    private static final Pattern HIDDEN = Pattern.compile(
            "(?i)(?:^|;)\\s*(?:display\\s*:\\s*none|visibility\\s*:\\s*hidden|mso-hide\\s*:\\s*all)\\s*(?:!important\\s*)?(?:;|$)");

    private EmailBodyPreview() {}

    public record Block(String text, String imageSource, String alt) {
        public boolean isImage() { return imageSource != null; }
        public static Block text(String value) { return new Block(value, null, null); }
    }

    public static List<Block> parse(String html, String plain) {
        if (html == null || html.isBlank()) return List.of(Block.text(plain == null ? "" : plain));
        var document = Jsoup.parse(html);
        document.select("head,style,script,noscript,template,iframe,object,[hidden]").remove();
        for (Element element : document.select("[style]")) {
            if (HIDDEN.matcher(element.attr("style")).find()) element.remove();
        }
        List<Block> blocks = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        NodeTraversor.traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int depth) {
                if (node instanceof TextNode t) {
                    boolean pre = t.parent() instanceof Element parent && (parent.normalName().equals("pre")
                            || parent.parents().stream().anyMatch(e -> e.normalName().equals("pre")));
                    text.append(pre ? t.getWholeText() : t.getWholeText().replaceAll("\\s+", " "));
                } else if (node instanceof Element e) {
                    String tag = e.normalName();
                    if (BLOCKS.contains(tag) || tag.equals("br")) text.append('\n');
                    if (tag.equals("li")) text.append("• ");
                    if (tag.equals("img")) {
                        if (tiny(e.attr("width")) && tiny(e.attr("height"))) return;
                        String source = e.attr("src");
                        if (source.isBlank()) source = e.attr("data-src");
                        if (source.isBlank()) source = e.attr("data-original");
                        if (source.isBlank() && e.hasAttr("srcset")) source = e.attr("srcset").strip().split("[\\s,]+", 2)[0];
                        flush(text, blocks);
                        if (source.isBlank()) blocks.add(Block.text("[图片无地址：" + e.attr("alt") + "]"));
                        else blocks.add(new Block(null, source, e.attr("alt")));
                    }
                }
            }

            @Override
            public void tail(Node node, int depth) {
                if (!(node instanceof Element e)) return;
                String tag = e.normalName();
                if (tag.equals("a")) {
                    String href = e.attr("href").strip();
                    if (href.matches("(?is)^(https?://|mailto:).*") && !href.equals(e.text().strip())) {
                        text.append(" （").append(href).append("）");
                    }
                }
                if (tag.equals("td") || tag.equals("th")) text.append(" | ");
                if (BLOCKS.contains(tag)) text.append('\n');
            }
        }, document.body());
        flush(text, blocks);
        if (blocks.isEmpty() && plain != null && !plain.isBlank()) blocks.add(Block.text(plain));
        return List.copyOf(blocks);
    }

    private static boolean tiny(String value) {
        return value.matches("[012](?:px)?");
    }

    private static void flush(StringBuilder text, List<Block> blocks) {
        String value = text.toString().replace('\u00a0', ' ').replaceAll("(?m)[ \\t]+$", "")
                .replaceAll("\\n{3,}", "\n\n").strip();
        if (!value.isBlank()) blocks.add(Block.text(value));
        text.setLength(0);
    }
}
