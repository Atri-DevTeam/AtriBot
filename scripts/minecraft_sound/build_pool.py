"""Build a version-pinned, self-hosted Minecraft sound quiz pool (Python stdlib only)."""
import argparse
import concurrent.futures
import hashlib
import http.client
import itertools
import json
from pathlib import Path
import re
import time
import urllib.request

MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
OBJECTS = "https://resources.download.minecraft.net/"
DISPLAY_NAMES = json.loads(Path(__file__).with_name("display_names.json").read_text(encoding="utf-8"))
SOURCE_NAMES = json.loads(Path(__file__).with_name("source_names.json").read_text(encoding="utf-8"))


def source_name(category, source, lang):
    key = category + '.' + source
    if key in SOURCE_NAMES:
        return SOURCE_NAMES[key]
    direct = lang.get(category + '.minecraft.' + source)
    if direct:
        return direct
    if category == 'block':
        normalized = source.replace('bamboo_wood_', 'bamboo_').replace('cherry_wood_', 'cherry_')
        direct = lang.get('block.minecraft.' + normalized)
        if direct:
            return direct
        if source.startswith('nether_wood_'):
            suffix = {'door': '门', 'trapdoor': '活板门', 'button': '按钮', 'pressure_plate': '压力板',
                      'fence_gate': '栅栏门', 'hanging_sign': '悬挂式告示牌'}.get(source.removeprefix('nether_wood_'))
            if suffix:
                return '下界木质' + suffix
    if category == 'entity' and source.startswith('baby_'):
        adult = lang.get('entity.minecraft.' + source.removeprefix('baby_'))
        if adult:
            return '幼年' + adult
    return None


def display_name(event_id, original, lang):
    if event_id in DISPLAY_NAMES:
        return DISPLAY_NAMES[event_id]
    parts = event_id.split('.')
    if len(parts) < 3:
        return original
    if event_id.startswith('entity.parrot.imitate.'):
        target = source_name('entity', parts[-1], lang)
        if target:
            return '鹦鹉：模仿' + target + '叫声'
    if event_id.startswith('block.note_block.'):
        instrument = {'banjo': '班卓琴', 'basedrum': '底鼓', 'bass': '贝斯', 'bell': '钟声', 'bit': '电子音',
                      'chime': '风铃', 'cow_bell': '牛铃', 'didgeridoo': '迪吉里杜管', 'flute': '长笛',
                      'guitar': '吉他', 'harp': '竖琴', 'hat': '踩镲', 'iron_xylophone': '铁木琴',
                      'pling': '电钢琴', 'snare': '军鼓', 'xylophone': '木琴', 'trumpet': '小号',
                      'trumpet_exposed': '小号（斑驳铜）', 'trumpet_weathered': '小号（锈蚀铜）',
                      'trumpet_oxidized': '小号（氧化铜）'}.get(parts[-1])
        if instrument:
            return '音符盒：' + instrument
    source = source_name(parts[0], parts[1], lang)
    if parts[0] == 'block' and source:
        action = {'break': '被破坏', 'place': '被放置', 'hit': '被敲击', 'fall': '物体落在其上',
                  'step': '脚步声', 'open': '打开', 'close': '关闭'}.get(parts[-1])
        if parts[-1] in ('click_on', 'click_off'):
            action = ('触发' if parts[-1] == 'click_on' else '复位') if 'pressure_plate' in parts[1] else (
                '按下' if parts[-1] == 'click_on' else '弹起')
        if action:
            return source + '：' + action
    if parts[0] == 'entity' and source and original in ('脚步声', '受伤', '死亡'):
        return source + '：' + original
    return original


def refresh_display_names(catalog, lang=None):
    """Keep original labels for review; only change display labels, never audio or candidates."""
    changed = 0
    for event in catalog["events"]:
        name = display_name(event['id'], event.get('originalName', event['name']), lang or {}).replace("死亡", "被击败")
        if event["name"] != name:
            event.setdefault("originalName", event["name"])
            changed += 1
            event["name"] = name
    names = {event["id"]: event["name"] for event in catalog["events"]}
    for group in catalog["groups"]:
        if group.get("anchor") in names:
            group["name"] = names[group["anchor"]]
    return changed


def fetch(url):
    for attempt in range(3):
        try:
            with urllib.request.urlopen(url, timeout=40) as response:
                return response.read()
        except (OSError, http.client.IncompleteRead):
            if attempt == 2:
                raise
            time.sleep(attempt + 1)


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def asset_bytes(entry, cache):
    digest = entry["hash"]
    target = cache / digest
    if target.exists():
        data = target.read_bytes()
        if hashlib.sha1(data).hexdigest() == digest:
            return data
    data = fetch(OBJECTS + digest[:2] + "/" + digest)
    if len(data) != entry["size"] or hashlib.sha1(data).hexdigest() != digest:
        raise ValueError("Asset checksum mismatch: " + digest)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(data)
    return data


def flatten(events, event_id, stack=(), pitch=1.0, volume=1.0):
    if event_id in stack:
        raise ValueError("Cyclic sound reference: " + event_id)
    result = []
    for raw in events.get(event_id, {}).get("sounds", []):
        sound = {"name": raw} if isinstance(raw, str) else raw
        p = pitch * sound.get("pitch", 1.0)
        v = volume * sound.get("volume", 1.0)
        if sound.get("type") == "event":
            children = flatten(events, sound["name"].removeprefix("minecraft:"),
                               (*stack, event_id), p, v)
            # An event reference's effective weight includes the referenced event's weights.
            result.extend({**child, "weight": child["weight"] * sound.get("weight", 1)}
                          for child in children)
        else:
            namespace, _, name = sound["name"].partition(":")
            if not name:
                namespace, name = "minecraft", namespace
            result.append({"asset": f"{namespace}/sounds/{name}.ogg", "pitch": p,
                           "volume": v, "weight": sound.get("weight", 1)})
    return result


def compatible(events):
    return (len({e["name"] for e in events}) == len(events)
            and all(not set(a["fingerprints"]) & set(b["fingerprints"])
                    for a, b in itertools.combinations(events, 2)))


def make_catalog(sounds, lang, assets, prefix):
    events = {}
    excluded = {}
    for event_id, definition in sounds.items():
        subtitle = lang.get(definition.get("subtitle"))
        variants = flatten(sounds, event_id)
        if any(v["asset"] not in assets for v in variants):
            raise ValueError("Missing asset for " + event_id)
        parts = event_id.split(".")
        source = lang.get(parts[0] + ".minecraft." + parts[1]) if len(parts) > 1 else None
        name = subtitle or fallback_name(event_id, lang)
        if source and source not in name:
            name = source + "：" + name
        paths = {}
        for v in variants:
            path = prefix + "/" + v["asset"].removeprefix("minecraft/")
            key = (path, v["pitch"], v["volume"])
            entry = paths.setdefault(key, {"path": path, "sha1": assets[v["asset"]]["hash"], "weight": 0,
                                          "pitch": v["pitch"], "volume": v["volume"]})
            entry["weight"] += v["weight"]
        if not paths:
            excluded[event_id] = "原版事件没有可播放的音频（保留记录）"
        events[event_id] = {"id": event_id, "name": name, "subtitleKey": definition.get("subtitle"),
                            "fingerprints": sorted({v["sha1"] for v in paths.values()}),
                            "variants": list(paths.values()), "playable": bool(paths)}
    catalog = {"schemaVersion": 1, "minecraftVersion": "", "events": list(events.values()), "groups": []}
    refresh_display_names(catalog, lang)
    return catalog, excluded


def fallback_name(event_id, lang):
    if event_id.startswith("music_disc."):
        key = "item.minecraft.music_disc_" + event_id.split(".", 1)[1]
        return "唱片：" + lang.get(key + ".desc", lang.get(key, event_id.split(".", 1)[1]))
    words = {"music": "背景音乐", "ambient": "环境音", "ui": "界面", "event": "事件", "entity": "生物",
             "block": "方块", "item": "物品", "weather": "天气", "overworld": "主世界", "nether": "下界",
             "end": "末地", "menu": "主菜单", "creative": "创造模式", "credits": "终末之诗", "game": "游戏",
             "under_water": "水下", "underwater": "水下", "empty": "静音事件", "intentionally_empty": "静音事件",
             "generic": "通用", "hurt": "受伤", "death": "死亡", "step": "脚步", "break": "破坏",
             "place": "放置", "open": "开启", "close": "关闭", "click": "点击", "rain": "雨声",
             "above": "上方", "loop": "循环", "additions": "附加音", "mood": "氛围", "button": "按钮"}
    result = []
    for token in event_id.split("."):
        translated = next((lang[p + ".minecraft." + token] for p in ("entity", "block", "item", "biome")
                           if p + ".minecraft." + token in lang), words.get(token, token.replace("_", " ")))
        result.append(translated)
    return "：".join(result)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", default="26.3-rc-3")
    parser.add_argument("--output", type=Path, default=Path("build/sound-upload"))
    parser.add_argument("--cache", type=Path, default=Path("build/sound-cache"))
    parser.add_argument("--metadata-only", action="store_true")
    parser.add_argument("--refresh-names", action="store_true",
                        help="Update an existing catalog offline, preserving audio, groups and difficulty")
    args = parser.parse_args()
    if not re.fullmatch(r"[a-zA-Z0-9._-]+", args.version):
        parser.error("Invalid version")
    if args.refresh_names:
        index_path = args.output / "minecraft" / args.version / "index.json"
        catalog = json.loads(index_path.read_text(encoding="utf-8"))
        lang = json.loads((index_path.parent / 'lang/zh_cn.json').read_text(encoding='utf-8'))
        changed = refresh_display_names(catalog, lang)
        write_json(index_path, catalog)
        print(f"Updated {changed} display names; audio and candidate membership retained")
        return
    manifest = json.loads(fetch(MANIFEST))
    version_entry = next(v for v in manifest["versions"] if v["id"] == args.version)
    version_data = fetch(version_entry["url"])
    if hashlib.sha1(version_data).hexdigest() != version_entry["sha1"]:
        raise ValueError("Version checksum mismatch")
    version = json.loads(version_data)
    index_data = fetch(version["assetIndex"]["url"])
    if hashlib.sha1(index_data).hexdigest() != version["assetIndex"]["sha1"]:
        raise ValueError("Asset index checksum mismatch")
    assets = json.loads(index_data)["objects"]
    prefix = "minecraft/" + args.version
    root = args.output / prefix
    metadata = {}
    for key in ["minecraft/sounds.json", "minecraft/lang/zh_cn.json"]:
        data = asset_bytes(assets[key], args.cache)
        target = root / key.removeprefix("minecraft/")
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        metadata[key] = json.loads(data)
    catalog, excluded = make_catalog(metadata["minecraft/sounds.json"], metadata["minecraft/lang/zh_cn.json"], assets, prefix)
    catalog["minecraftVersion"] = args.version
    write_json(root / "index.json", catalog)
    write_json(root / "version.json", version)
    write_json(root / "asset-index.json", json.loads(index_data))
    # Export the entire vanilla OGG pool, including music and files not referenced by a quiz event.
    selected = {prefix + "/" + key.removeprefix("minecraft/"): {"sha1": entry["hash"]}
                for key, entry in assets.items() if key.startswith("minecraft/sounds/") and key.endswith(".ogg")}
    print(f"{len(catalog['events'])} events, {len(catalog['groups'])} groups, {len(selected)} audio files", flush=True)
    if not args.metadata_only:
        def download(path):
            asset_key = "minecraft/" + path.removeprefix(prefix + "/")
            target = args.output / path
            data = asset_bytes(assets[asset_key], args.cache)
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(data)
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as executor:
            for count, _ in enumerate(executor.map(download, selected), 1):
                if count % 50 == 0:
                    print(f"Downloaded {count}/{len(selected)}", flush=True)
        for path, variant in selected.items():
            if hashlib.sha1((args.output / path).read_bytes()).hexdigest() != variant["sha1"]:
                raise ValueError("Output validation failed: " + path)
    write_json(args.output / "build-report.json", {
        "version": args.version, "events": len(catalog["events"]), "groups": len(catalog["groups"]),
        "audioFiles": len(selected), "audioDownloaded": not args.metadata_only,
        "stage": "originals-exported", "emptyEvents": excluded,
        "limitations": ["Run prepare_audio.py to generate playback variants and acoustic question groups.",
                         "All original files and events are retained; empty events have no playable sound."]})
    print("Pool ready: " + str(args.output.resolve()), flush=True)


if __name__ == "__main__":
    main()
