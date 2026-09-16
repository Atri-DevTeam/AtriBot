package top.yzljc.atribot.function.games.sound;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.random.RandomGenerator;

/** Immutable, validated catalog. Runtime only contacts the configured resource origin. */
public final class SoundCatalog {
    public record Variant(String path, String sha1, int weight) {}
    public record Sound(String id, String name, List<Variant> variants, Set<String> fingerprints) {
        public Sound {
            variants = List.copyOf(variants);
            fingerprints = Set.copyOf(fingerprints);
        }
    }
    public record Pool(String name, int difficulty, Sound anchor, List<List<Sound>> choices) {}
    public record Question(String group, int difficulty, List<Sound> options, int answer, Variant audio) {
        public Question {
            options = List.copyOf(options);
            if (options.size() != 4 || answer < 0 || answer >= options.size())
                throw new IllegalArgumentException("Invalid sound question options or answer");
            if (audio == null || !options.get(answer).variants().contains(audio))
                throw new IllegalArgumentException("Sound question audio does not belong to the correct answer");
            if (!compatible(options))
                throw new IllegalArgumentException("Sound question has ambiguous options");
        }
    }

    private final URI base;
    private final List<Pool> pools;

    private SoundCatalog(URI base, List<Pool> pools) {
        this.base = base;
        this.pools = List.copyOf(pools);
    }

    public static URI resource(URI base, String relative) {
        if (relative == null || !relative.matches("[a-zA-Z0-9_-]+(?:[./][a-zA-Z0-9_-]+)*")
                || relative.contains("..")) throw new IllegalArgumentException("Invalid resource path");
        return base.resolve(relative);
    }

    public static URI baseUri(String prefix) {
        URI uri = URI.create(prefix.endsWith("/") ? prefix : prefix + "/");
        if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                || uri.getHost() == null || uri.getRawQuery() != null || uri.getFragment() != null
                || uri.getUserInfo() != null) throw new IllegalArgumentException("Invalid sound resource prefix");
        return uri;
    }

    public static SoundCatalog download(String prefix, String indexPath) throws IOException, InterruptedException {
        URI base = baseUri(prefix);
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER).build()) {
            var request = HttpRequest.newBuilder(resource(base, indexPath)).timeout(Duration.ofSeconds(25)).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (var input = response.body()) {
                if (response.statusCode() != 200) throw new IOException("Sound index HTTP " + response.statusCode());
                byte[] bytes = input.readNBytes(16 * 1024 * 1024 + 1);
                if (bytes.length > 16 * 1024 * 1024) throw new IOException("Sound index too large");
                return parse(base, new ObjectMapper().readTree(bytes));
            }
        }
    }

    public static SoundCatalog parse(URI base, JsonNode root) {
        if (root == null || root.path("schemaVersion").asInt() != 1
                || !"26.3-rc-3".equals(root.path("minecraftVersion").asText())) {
            throw new IllegalArgumentException("Unsupported sound catalog version");
        }
        Map<String, Sound> sounds = new HashMap<>();
        for (JsonNode node : root.path("events")) {
            String id = node.path("id").asText();
            String name = node.path("name").asText();
            if (id.isBlank() || name.isBlank() || name.length() > 100 || sounds.containsKey(id)) {
                throw new IllegalArgumentException("Invalid or duplicate sound event");
            }
            List<Variant> variants = new ArrayList<>();
            Set<String> hashes = new HashSet<>();
            for (JsonNode v : node.path("variants")) {
                String path = v.path("playbackPath").asText(v.path("path").asText());
                String hash = v.path("playbackSha1").asText(v.path("sha1").asText());
                int weight = v.path("weight").asInt(1);
                resource(base, path);
                if (!path.endsWith(".ogg") || !path.startsWith("minecraft/26.3-rc-3/")
                        || !hash.matches("[0-9a-f]{40}") || weight < 1 || weight > 10000) {
                    throw new IllegalArgumentException("Invalid sound variant");
                }
                variants.add(new Variant(path, hash, weight));
                hashes.add(hash);
                if (v.has("pcmSha256")) {
                    String pcmHash = v.path("pcmSha256").asText();
                    if (!pcmHash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid PCM fingerprint");
                    hashes.add("pcm:" + pcmHash);
                }
            }
            if (variants.isEmpty() && !node.path("playable").asBoolean(true)) continue;
            if (variants.isEmpty() || variants.size() > 1024) throw new IllegalArgumentException("Invalid variants count");
            sounds.put(id, new Sound(id, name, List.copyOf(variants), Set.copyOf(hashes)));
        }
        List<Pool> pools = new ArrayList<>();
        for (JsonNode group : root.path("groups")) {
            int difficulty = group.path("difficulty").asInt();
            if (difficulty < 1 || difficulty > 3) throw new IllegalArgumentException("Invalid difficulty");
            List<Sound> members = new ArrayList<>();
            for (JsonNode id : group.path("events")) {
                Sound sound = sounds.get(id.asText());
                if (sound == null || members.contains(sound)) throw new IllegalArgumentException("Invalid group member");
                members.add(sound);
            }
            if (members.size() > 32) throw new IllegalArgumentException("Group too large");
            List<List<Sound>> choices = new ArrayList<>();
            Sound anchor = group.has("anchor") ? sounds.get(group.path("anchor").asText()) : null;
            if (group.has("anchor") && (anchor == null || !members.contains(anchor))) throw new IllegalArgumentException("Invalid anchor");
            for (int a = 0; a < members.size(); a++)
                for (int b = a + 1; b < members.size(); b++)
                    for (int c = b + 1; c < members.size(); c++)
                        for (int d = c + 1; d < members.size(); d++) {
                            List<Sound> four = List.of(members.get(a), members.get(b), members.get(c), members.get(d));
                            if ((anchor == null || four.contains(anchor)) && compatible(four)) choices.add(four);
                        }
            if (!choices.isEmpty()) pools.add(new Pool(group.path("name").asText(), difficulty, anchor, List.copyOf(choices)));
        }
        if (pools.isEmpty()) throw new IllegalArgumentException("No playable sound groups");
        return new SoundCatalog(base, pools);
    }

    public static boolean compatible(List<Sound> sounds) {
        Set<String> names = new HashSet<>(), hashes = new HashSet<>();
        for (Sound sound : sounds) {
            if (!names.add(sound.name())) return false;
            for (String hash : sound.fingerprints()) if (!hashes.add(hash)) return false;
        }
        return true;
    }

    public Question question(int difficulty, RandomGenerator random) {
        List<Pool> eligible = pools.stream().filter(p -> difficulty == 0 || p.difficulty() == difficulty).toList();
        if (eligible.isEmpty()) throw new IllegalArgumentException("No questions at this difficulty");
        Pool pool = eligible.get(random.nextInt(eligible.size()));
        List<Sound> options = new ArrayList<>(pool.choices().get(random.nextInt(pool.choices().size())));
        for (int i = options.size() - 1; i > 0; i--) Collections.swap(options, i, random.nextInt(i + 1));
        int answer = pool.anchor() == null ? random.nextInt(4) : options.indexOf(pool.anchor());
        List<Variant> variants = options.get(answer).variants();
        int ticket = random.nextInt(variants.stream().mapToInt(Variant::weight).sum());
        for (Variant variant : variants) {
            ticket -= variant.weight();
            if (ticket < 0) return new Question(pool.name(), pool.difficulty(), List.copyOf(options), answer, variant);
        }
        throw new IllegalStateException("Invalid sound weights");
    }

    public String audioUrl(Question question) { return resource(base, question.audio().path()).toString(); }
}
