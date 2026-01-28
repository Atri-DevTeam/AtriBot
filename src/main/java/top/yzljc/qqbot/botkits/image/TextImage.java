package top.yzljc.qqbot.botkits.image;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextImage {

    public record Result(String textMessage, String imgBase64) {
    }

    public static Result parseTextImage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return new Result(null, null);
        }

        String text;
        String imgBase64 = null;

        Pattern imgPattern = Pattern.compile("\\[CQ:image,([^]]+)]");
        Matcher matcher = imgPattern.matcher(message);
        if (matcher.find()) {
            String imgContent = matcher.group(1);

            imgContent = imgContent.replaceAll(",file_size=\\d+", "");
            imgContent = imgContent.replaceAll("summary=[^,]*,?", "");
            imgContent = imgContent.replaceAll("sub_type=\\d+,?", "");

            Pattern urlPattern = Pattern.compile("url=([^,\\]]+)");
            Matcher urlMatcher = urlPattern.matcher(imgContent);
            String imageUrl = null;
            if (urlMatcher.find()) {
                imageUrl = urlMatcher.group(1)
                        .replace("&amp;", "&");
            }

            if (imageUrl != null && !imageUrl.isEmpty()) {
                imgBase64 = downloadImageAsBase64(imageUrl);
            }

            text = matcher.replaceFirst("").trim();
            if (text.isEmpty()) text = null;
        } else {
            text = message.trim();
        }

        return new Result(text, imgBase64);
    }

    private static String downloadImageAsBase64(String imageUrl) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            HttpURLConnection conn = (HttpURLConnection) new URI(imageUrl).toURL().openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(10000);
            conn.setRequestMethod("GET");
            try (InputStream is = conn.getInputStream()) {
                byte[] buf = new byte[4096];
                int len;
                while ((len = is.read(buf)) > 0) {
                    baos.write(buf, 0, len);
                }
            }
            byte[] imgBytes = baos.toByteArray();
            return Base64.getEncoder().encodeToString(imgBytes);
        } catch (Exception e) {
            return null;
        }
    }
}