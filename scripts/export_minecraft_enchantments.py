import argparse
import http.client
import json
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path


def read_json(url):
    for attempt in range(3):
        try:
            with urllib.request.urlopen(url, timeout=30) as response:
                return json.load(response)
        except (OSError, http.client.HTTPException):
            if attempt == 2:
                raise


def export(version, destination, ugc_destination=None):
    root = "https://raw.githubusercontent.com/misode/mcmeta/"
    data_root = f"{root}{version}-data/data/minecraft/"
    registry_url = f"{root}{version}-summary/registries/data.json"
    components_url = f"{root}{version}-summary/item_components/data.json"
    language_url = f"{root}{version}-assets/assets/minecraft/lang/zh_cn.json"
    registry = read_json(registry_url)
    language = read_json(language_url)
    components = read_json(components_url)
    ids = registry["enchantment"]
    tag_ids = registry["tag/enchantment"]
    urls = [f"{data_root}enchantment/{identifier}.json" for identifier in ids]
    urls += [f"{data_root}tags/enchantment/{identifier}.json" for identifier in tag_ids]
    with ThreadPoolExecutor(max_workers=6) as executor:
        files = list(executor.map(read_json, urls))
    definitions = dict(zip(ids, files[:len(ids)]))
    tags = dict(zip(tag_ids, files[len(ids):]))
    item_tags = {}

    def resolve_items(values, visited=frozenset()):
        if isinstance(values, str):
            values = [values]
        result = set()
        for value in values:
            if isinstance(value, dict):
                value = value["id"]
            if value.startswith("#minecraft:"):
                tag = value.removeprefix("#minecraft:")
                if tag in visited:
                    raise ValueError(f"Cyclic item tag: {tag}")
                if tag not in item_tags:
                    item_tags[tag] = read_json(f"{data_root}tags/item/{tag}.json")
                result.update(resolve_items(item_tags[tag]["values"], visited | {tag}))
            else:
                result.add(value)
        return result

    def resolve(values, visited=frozenset()):
        if isinstance(values, str):
            values = [values]
        result = []
        for value in values:
            if isinstance(value, dict):
                value = value["id"]
            if value.startswith("#minecraft:"):
                tag = value.removeprefix("#minecraft:")
                if tag in visited:
                    raise ValueError(f"Cyclic enchantment tag: {tag}")
                result.extend(resolve(tags[tag]["values"], visited | {tag}))
            else:
                result.append(value)
        return list(dict.fromkeys(result))

    curses = resolve(tags["curse"]["values"])
    treasures = resolve(tags["treasure"]["values"])
    table_enchantments = resolve(tags["in_enchanting_table"]["values"])
    entries = {}
    for identifier, definition in definitions.items():
        full_id = f"minecraft:{identifier}"
        entries[full_id] = {
            "name": language[definition["description"]["translate"]],
            "curse": full_id in curses,
            "treasure": full_id in treasures,
            "incompatible_with": sorted(resolve(definition.get("exclusive_set", []))),
            "supported_items": sorted(resolve_items(definition["supported_items"])),
            "primary_items": sorted(resolve_items(definition.get("primary_items", definition["supported_items"]))),
            "definition": definition,
        }
    unknown = {value for entry in entries.values() for value in entry["incompatible_with"]} - entries.keys()
    if unknown:
        raise ValueError(f"Unknown enchantments: {sorted(unknown)}")
    material_types = ("pickaxe", "axe", "shovel", "hoe", "sword", "spear", "helmet", "chestplate", "leggings", "boots")
    preferred = {kind: f"minecraft:diamond_{kind}" for kind in material_types}
    groups = {}
    for item in sorted({item for entry in entries.values() for item in entry["supported_items"]}):
        name = item.removeprefix("minecraft:")
        kind = next((kind for kind in material_types if name.endswith("_" + kind)), name)
        if name.endswith(("_head", "_skull")):
            kind = "head"
        if kind in {"head", "carved_pumpkin"}:
            continue
        if "minecraft:enchantable" not in components[name]:
            continue
        if kind not in groups or item == preferred.get(kind):
            groups[kind] = item
    order = (*material_types, "bow", "crossbow", "trident", "mace", "fishing_rod", "shears", "flint_and_steel",
             "brush", "shield", "elytra", "carrot_on_a_stick", "warped_fungus_on_a_stick")
    presets = [{"key": "book", "id": "minecraft:enchanted_book", "name": language["item.minecraft.enchanted_book"],
                "enchantability": components["book"]["minecraft:enchantable"]["value"]}]
    for kind in sorted(groups, key=lambda kind: (order.index(kind) if kind in order else len(order), kind)):
        item = groups[kind]
        name = item.removeprefix("minecraft:")
        translated = language.get(f"item.minecraft.{name}") or language[f"block.minecraft.{name}"]
        presets.append({"key": kind, "id": item, "name": translated,
                        "enchantability": components[name]["minecraft:enchantable"]["value"]})
    document = {
        "minecraft_version": version,
        "sources": {"registry": registry_url, "data": data_root, "language": language_url, "components": components_url},
        "enchantments": entries,
        "presets": presets,
        "table_enchantments": table_enchantments,
        "tags": tags,
        "item_tags": item_tags,
    }
    encoded = json.dumps(document, ensure_ascii=False, indent=2) + "\n"
    for target in [destination] + ([ugc_destination] if ugc_destination else []):
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(encoded, encoding="utf-8")
    print(f"Exported {len(entries)} enchantments to {destination}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Export versioned Minecraft Java enchantment data")
    parser.add_argument("--version", default="26.3")
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parents[1]
                        / "src/main/resources/minecraft/enchantments-26.3.json")
    parser.add_argument("--ugc-output", type=Path)
    options = parser.parse_args()
    export(options.version, options.output, options.ugc_output)
