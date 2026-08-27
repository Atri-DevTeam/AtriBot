package top.yzljc.atribot.chat.official;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName Ark23
 * @Created_at 2026/08/27
 * @Project AtriBot
 * @Package top.yzljc.atribot.chat.official
 */
public record Ark23(String description, String prompt, List<Item> items) {

    public Ark23 {
        if (items.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Ark23 items must not contain null");
        }
        items = List.copyOf(items);
    }

    public Map<String, Object> toPayload() {
        List<Map<String, Object>> list = new ArrayList<>(items.size());
        for (Item item : items) {
            List<Map<String, String>> itemValues = new ArrayList<>(2);
            itemValues.add(Map.of("key", "desc", "value", item.description()));
            if (item.link() != null && !item.link().isBlank()) {
                itemValues.add(Map.of("key", "link", "value", item.link()));
            }
            list.add(Map.of("obj_kv", itemValues));
        }

        List<Map<String, Object>> values = new ArrayList<>(3);
        values.add(Map.of("key", "#DESC#", "value", description));
        values.add(Map.of("key", "#PROMPT#", "value", prompt));
        values.add(Map.of("key", "#LIST#", "obj", list));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("template_id", 23);
        payload.put("kv", values);
        return payload;
    }

    public record Item(String description, String link) {

        public static Item text(String description) {
            return new Item(description, null);
        }

        public static Item link(String description, String link) {
            if (link == null || link.isBlank()) {
                throw new IllegalArgumentException("Ark23 item link must not be blank");
            }
            return new Item(description, link);
        }
    }
}
