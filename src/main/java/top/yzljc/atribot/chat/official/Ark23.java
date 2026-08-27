package top.yzljc.atribot.chat.official;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** QQ Ark template 23 payload. */
public record Ark23(String description, String prompt, List<Item> items) {

    public Ark23 {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Ark23 description must not be blank");
        }
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Ark23 prompt must not be blank");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Ark23 items must not be empty");
        }
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

        public Item {
            if (description == null || description.isBlank()) {
                throw new IllegalArgumentException("Ark23 item description must not be blank");
            }
        }

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
