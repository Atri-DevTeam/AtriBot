package top.yzljc.atribot.service.textreview;

import java.net.IDN;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @Author YZ_Ljc_
 * @ClassName LinkMatcher
 * @Created_at 2026/09/23
 * @Project AtriMeow
 * @Package top.yzljc.atribot.service.textreview
 * @Description 提取文本链接，以解析后的完整域名匹配白名单
 */
final class LinkMatcher {
    private static final String TAIL = "[^\\s<>\"'\\[\\]{}，。；！？、（）【】]";
    private static final Pattern LINKS = Pattern.compile(
            "(?i)(?:[a-z][a-z0-9+.-]*://|(?<![:/])//)(?:\\[[0-9a-f:.%]+\\]" + TAIL + "*|" + TAIL + "+)"
                    + "|(?:mailto:|javascript:|data:|tel:)" + TAIL + "+"
                    + "|(?<![a-z0-9_@.-])(?:[a-z0-9_-]+\\.)+(?:xn--[a-z0-9-]{2,59}|[a-z]{2,63})"
                    + "(?::[0-9]+)?(?:@" + TAIL + "+)?(?:[/?#]" + TAIL + "*)?"
                    + "|(?<![a-z0-9_.])(?:[0-9]{1,3}\\.){3}[0-9]{1,3}(?::[0-9]+)?(?:[/?#]" + TAIL + "*)?");

    private LinkMatcher() {}

    static List<Link> find(String text, Collection<String> allowedDomains) {
        Set<String> allowed = new HashSet<>();
        if (allowedDomains != null) {
            for (String entry : allowedDomains) {
                if (entry == null || entry.isBlank()) throw new IllegalArgumentException("链接白名单不能包含空域名");
                String domain = host(entry.trim());
                if (domain == null) throw new IllegalArgumentException("链接白名单包含无效域名");
                allowed.add(domain);
            }
        }
        List<Link> links = new ArrayList<>();
        var matcher = LINKS.matcher(text);
        while (matcher.find()) {
            int end = trimEnd(text, matcher.start(), matcher.end());
            if (end <= matcher.start()) continue;
            String value = text.substring(matcher.start(), end);
            String domain = host(value);
            links.add(new Link(matcher.start(), end, domain != null && allowed.contains(domain)));
        }
        return links;
    }

    private static String host(String value) {
        try {
            String input = value.startsWith("//") ? "https:" + value
                    : value.matches("(?i)^[a-z][a-z0-9+.-]*://.*") ? value : "https://" + value;
            URI uri = URI.create(input);
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) return null;
            String authority = uri.getRawAuthority();
            if (authority == null || authority.isBlank() || authority.contains("@") || authority.contains("%")
                    || authority.contains("\\") || authority.contains("[") || authority.contains("]")) return null;
            String hostname = authority;
            int colon = authority.lastIndexOf(':');
            if (colon >= 0) {
                String port = authority.substring(colon + 1);
                if (!port.matches("[0-9]+") || Integer.parseInt(port) > 65535) return null;
                hostname = authority.substring(0, colon);
            }
            if (hostname.endsWith(".")) hostname = hostname.substring(0, hostname.length() - 1);
            if (hostname.isEmpty()) return null;
            return IDN.toASCII(hostname, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static int trimEnd(String text, int start, int end) {
        while (end > start) {
            char last = text.charAt(end - 1);
            if (".,;:!?".indexOf(last) >= 0) {
                end--;
            } else if (last == ')' && count(text, start, end, ')') > count(text, start, end, '(')) {
                end--;
            } else {
                break;
            }
        }
        return end;
    }

    private static int count(String text, int start, int end, char value) {
        int count = 0;
        for (int index = start; index < end; index++) if (text.charAt(index) == value) count++;
        return count;
    }

    /**
     * @Author YZ_Ljc_
     * @ClassName Link
     * @Created_at 2026/09/23
     * @Project AtriMeow
     * @Package top.yzljc.atribot.service.textreview
     */
    record Link(int start, int end, boolean allowed) {}
}
