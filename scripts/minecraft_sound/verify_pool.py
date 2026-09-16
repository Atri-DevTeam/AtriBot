"""Offline verification of the complete vanilla export, playback copies and every answer pool."""
import argparse
import hashlib
import json
from pathlib import Path
from build_pool import compatible, make_catalog


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pool", type=Path, default=Path("build/sound-upload"))
    args = parser.parse_args()
    root = args.pool.resolve()
    version_root = root / "minecraft/26.3-rc-3"
    catalog = json.loads((version_root / "index.json").read_text(encoding="utf-8"))
    assets = json.loads((version_root / "asset-index.json").read_text(encoding="utf-8"))["objects"]
    original = json.loads((version_root / "sounds.json").read_text(encoding="utf-8"))
    lang = json.loads((version_root / "lang/zh_cn.json").read_text(encoding="utf-8"))
    expected, _ = make_catalog(original, lang, assets, "minecraft/26.3-rc-3")
    expected = {event['id']: event for event in expected['events']}
    events = {e["id"]: e for e in catalog["events"]}
    assert set(events) == set(original), "Some vanilla events were dropped"
    checked = {}
    def check(relative, digest):
        target = (root / relative).resolve()
        assert target.is_relative_to(root), relative
        if relative not in checked:
            data = target.read_bytes()
            assert data.startswith(b"OggS"), relative
            checked[relative] = hashlib.sha1(data).hexdigest()
        assert checked[relative] == digest, relative
    count = 0
    for key, entry in assets.items():
        if key.startswith("minecraft/sounds/") and key.endswith(".ogg"):
            check("minecraft/26.3-rc-3/" + key.removeprefix("minecraft/"), entry["hash"])
            count += 1
    for event in events.values():
        source_fields = ('path', 'sha1', 'weight', 'pitch', 'volume')
        source_mapping = lambda variants: sorted(tuple(v[field] for field in source_fields) for v in variants)
        assert source_mapping(event['variants']) == source_mapping(expected[event['id']]['variants']), event['id']
        for variant in event["variants"]:
            assert Path(variant['playbackPath']).stem == variant['pcmSha256'], event['id']
            check(variant["path"], variant["sha1"])
            check(variant["playbackPath"], variant["playbackSha1"])
    anchors = set()
    for group in catalog["groups"]:
        assert group["anchor"] in group["events"]
        assert 4 <= len(group["events"]) <= 7
        assert compatible([events[e] for e in group["events"]]), group["id"]
        anchors.add(group["anchor"])
    assert anchors == {e["id"] for e in events.values() if e["variants"]}, "A playable event cannot be selected as an answer"
    print(f"Verified: {count} original OGGs, {len(events)} retained events, {len(anchors)} selectable answers, {len(checked)-count} playback files")


if __name__ == "__main__":
    main()
