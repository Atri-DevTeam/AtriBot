package top.yzljc.atribot.function.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.chat.napcat.impl.MessageSegment;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.NapcatGroupMessageEvent;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigManager;
import top.yzljc.atribot.service.runtime.ThreadManager;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NeteaseMusicResolver implements Listener {
    private static final Logger log = LoggerFactory.getLogger(NeteaseMusicResolver.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> MUSIC_HOSTS = Set.of(
            "music.163.com", "y.music.163.com", "m.music.163.com", "www.music.163.com");
    private static final Pattern LINK = Pattern.compile(
            "(?<![a-zA-Z0-9_./:@?=&%+-])(?:https?://)?(?:163cn\\.tv|(?:(?:y|m|www)\\.)?music\\.163\\.com)/[a-zA-Z0-9_~:/?#\\[\\]@!$&'*+,;=%.-]*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SONG_ID = Pattern.compile("[1-9][0-9]{0,18}");
    private static final Cache<String, Boolean> RECENT_SONGS = CacheBuilder.newBuilder()
            .maximumSize(4096).expireAfterWrite(Duration.ofMinutes(5)).build();

    @EventHandler
    public void onGroupMessage(NapcatGroupMessageEvent event) {
        if (!GroupConfigManager.isFeatureEnabled(event.getGroupId(), "netease_music")) return;
        if (event.getUser() != null && event.getUser().isBot()) return;

        URI link = findLink(event.getMessage().getContent());
        if (link == null) return;
        String groupId = event.getGroupId();
        ThreadManager.execute(() -> {
            String key = null;
            boolean sent = false;
            try {
                String songId = resolveSongId(link, NeteaseMusicResolver::redirectLocation);
                if (songId == null) return;
                key = groupId + ":" + songId;
                if (RECENT_SONGS.asMap().putIfAbsent(key, Boolean.TRUE) != null) {
                    key = null;
                    return;
                }
                List<MessageSegment> message = createMessage(fetchSong(songId), songId);
                if (GroupConfigManager.isFeatureEnabled(groupId, "netease_music")) {
                    sent = GroupMessage.chatMessage(groupId, message) != null;
                }
            } catch (Exception e) {
                log.warn("网易云音乐解析失败，群 {}: {}", groupId, e.getMessage());
            } finally {
                // 失败后允许再次分享重试，成功的歌曲在同群内短时间去重。
                if (!sent && key != null) RECENT_SONGS.invalidate(key);
            }
        });
    }

    static URI findLink(String content) {
        if (content == null || content.isBlank()) return null;
        String normalized = content.replace("\\/", "/")
                .replace("\\u0026", "&").replace("&amp;", "&")
                .replace("&#91;", "[").replace("&#93;", "]")
                .replace("&#44;", ",").replace("&amp;", "&");
        Matcher matcher = LINK.matcher(normalized);
        while (matcher.find()) {
            String value = matcher.group().replaceAll("[.,;!\\]]+$", "");
            if (!value.regionMatches(true, 0, "http", 0, 4)) value = "https://" + value;
            try {
                URI uri = URI.create(value);
                if (isAllowedLink(uri) && ("163cn.tv".equalsIgnoreCase(uri.getHost())
                        || songIdFromUri(uri) != null)) return uri;
            } catch (IllegalArgumentException ignored) {
                // 一条消息中的坏链接不应影响后续有效歌曲链接。
            }
        }
        return null;
    }

    static boolean isAllowedLink(URI uri) {
        String host = uri.getHost();
        return host != null && (MUSIC_HOSTS.contains(host.toLowerCase(Locale.ROOT))
                || "163cn.tv".equalsIgnoreCase(host))
                && ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                && uri.getUserInfo() == null
                && (uri.getPort() == -1 || (uri.getPort() == 443 && "https".equalsIgnoreCase(uri.getScheme()))
                || (uri.getPort() == 80 && "http".equalsIgnoreCase(uri.getScheme())));
    }

    static String songIdFromUri(URI uri) {
        if (!isAllowedLink(uri) || !MUSIC_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT))) return null;
        URI route = uri;
        if (uri.getRawFragment() != null && uri.getRawFragment().startsWith("/")) {
            route = URI.create(uri.getRawFragment());
        }
        String path = route.getPath();
        if (path == null) return null;
        if (path.matches("/(?:m/)?song/[1-9][0-9]{0,18}/?")) {
            return path.replaceAll("/$", "").substring(path.lastIndexOf("song/") + 5);
        }
        if (!path.matches("/(?:m/)?song/?") || route.getRawQuery() == null) return null;
        for (String part : route.getRawQuery().split("&")) {
            String[] pair = part.split("=", 2);
            if (pair.length != 2) continue;
            String name = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
            if ("id".equals(name) && SONG_ID.matcher(value).matches()) return value;
        }
        return null;
    }

    @FunctionalInterface
    interface RedirectLookup {
        String location(URI uri) throws IOException;
    }

    static String resolveSongId(URI link, RedirectLookup lookup) throws IOException {
        URI current = link;
        for (int redirects = 0; redirects <= 5; redirects++) {
            if (!isAllowedLink(current)) return null;
            String id = songIdFromUri(current);
            if (id != null) return id;
            if (redirects == 5) return null;
            String location = lookup.location(current);
            if (location == null || location.isBlank()) return null;
            current = current.resolve(location);
        }
        return null;
    }

    private static HttpURLConnection openConnection(URI uri) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        connection.setRequestProperty("Referer", "https://music.163.com/");
        return connection;
    }

    private static String redirectLocation(URI uri) throws IOException {
        HttpURLConnection connection = openConnection(uri);
        try {
            int status = connection.getResponseCode();
            return switch (status) {
                case 301, 302, 303, 307, 308 -> connection.getHeaderField("Location");
                default -> null;
            };
        } finally {
            connection.disconnect();
        }
    }

    private static JsonNode fetchSong(String songId) throws IOException {
        URI uri = URI.create("https://music.163.com/api/song/detail/?id=" + songId + "&ids=%5B" + songId + "%5D");
        HttpURLConnection connection = openConnection(uri);
        try {
            if (connection.getResponseCode() != 200) throw new IOException("歌曲详情 HTTP " + connection.getResponseCode());
            try (var input = connection.getInputStream()) {
                return readSong(MAPPER.readTree(input), songId);
            }
        } finally {
            connection.disconnect();
        }
    }

    static JsonNode readSong(JsonNode root, String songId) throws IOException {
        if (root != null && root.path("code").asInt() == 200 && root.path("songs").isArray()) {
            for (JsonNode song : root.path("songs")) {
                if (songId.equals(song.path("id").asText()) && !song.path("name").asText("").isBlank()) return song;
            }
        }
        throw new IOException("未获取到歌曲详情：" + songId);
    }

    static List<MessageSegment> createMessage(JsonNode song, String songId) {
        JsonNode album = song.path("album");
        List<String> artists = new ArrayList<>();
        for (JsonNode artist : song.path("artists")) {
            String name = artist.path("name").asText("").strip();
            if (!name.isBlank()) artists.add(name);
        }
        long seconds = Math.max(0, song.path("duration").asLong()) / 1000;
        String text = "歌曲：" + song.path("name").asText() + "\n"
                + "歌手：" + (artists.isEmpty() ? "未知歌手" : String.join(" / ", artists)) + "\n"
                + "专辑：" + album.path("name").asText("未知专辑") + "\n"
                + "时长：" + String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60) + "\n"
                + "链接：https://music.163.com/song?id=" + songId + "\n";
        List<MessageSegment> message = new ArrayList<>();
        message.add(new MessageSegment("text", Map.of("text", text)));
        String cover = album.path("picUrl").asText("").strip();
        if (cover.startsWith("http://")) cover = "https://" + cover.substring(7);
        if (cover.startsWith("//")) cover = "https:" + cover;
        if (cover.startsWith("https://")) message.add(new MessageSegment("image", Map.of("file", cover)));
        return message;
    }
}
