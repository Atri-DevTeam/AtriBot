package top.yzljc.atribot.service.textreview;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * @Author YZ_Ljc_
 * @ClassName LocalWordMatcher
 * @Created_at 2026/09/23
 * @Project AtriMeow
 * @Package top.yzljc.atribot.service.textreview
 * @Description 一次加载本地词库，标准化匹配并保留原文替换位置
 */
final class LocalWordMatcher {
    private final List<String> words;

    LocalWordMatcher(Path file) throws IOException {
        words = Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                .map(line -> line.replace("\uFEFF", "").trim())
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .map(line -> normalize(line, null, null)).filter(word -> !word.isEmpty()).distinct().toList();
    }

    List<TextRange> find(String text) {
        List<Integer> starts = new ArrayList<>();
        List<Integer> ends = new ArrayList<>();
        String normalized = normalize(text, starts, ends);
        List<TextRange> matches = new ArrayList<>();
        for (String word : words) {
            int from = 0;
            while (from < normalized.length()) {
                int index = normalized.indexOf(word, from);
                if (index < 0) break;
                matches.add(new TextRange(starts.get(index), ends.get(index + word.length() - 1)));
                from = index + 1;
            }
        }
        return matches;
    }

    private static String normalize(String text, List<Integer> starts, List<Integer> ends) {
        StringBuilder result = new StringBuilder();
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            int end = offset + Character.charCount(codePoint);
            String normalized = Normalizer.normalize(new String(Character.toChars(codePoint)), Normalizer.Form.NFKC)
                    .toUpperCase(Locale.ROOT);
            for (int index = 0; index < normalized.length();) {
                int value = normalized.codePointAt(index);
                if (!Character.isWhitespace(value) && Character.getType(value) != Character.FORMAT) {
                    result.appendCodePoint(value);
                    if (starts != null && ends != null) {
                        for (int unit = 0; unit < Character.charCount(value); unit++) {
                            starts.add(offset);
                            ends.add(end);
                        }
                    }
                }
                index += Character.charCount(value);
            }
            offset = end;
        }
        return result.toString();
    }
}
