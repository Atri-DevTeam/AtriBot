"""Verify a public resource prefix using the full index and representative original/playback OGGs."""
import argparse
import hashlib
import json
from pathlib import Path
import urllib.request
from urllib.parse import urljoin, urlsplit


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--pool", type=Path, default=Path("build/sound-upload"))
    args = parser.parse_args()
    base = args.base_url.rstrip("/") + "/"
    index_path = "minecraft/26.3-rc-3/index.json"
    with urllib.request.urlopen(urljoin(base, index_path), timeout=45) as response:
        remote = json.load(response)
        final_origin = urlsplit(response.url).netloc
        redirected = response.url != urljoin(base, index_path)
    local = json.loads((args.pool / index_path).read_text(encoding="utf-8"))
    if remote != local:
        raise ValueError("Remote index differs from local catalog")
    print(f"Index verified: {len(remote['events'])} events, {len(remote['groups'])} groups; redirected={redirected}, host={final_origin}", flush=True)
    selected = [e for e in remote["events"] if e["variants"]]
    for event in [selected[0], next(e for e in selected if e["id"] == "entity.zombie.hurt"),
                  next(e for e in selected if e["id"].startswith("music."))]:
        variant = event["variants"][0]
        for path_key, hash_key in [("path", "sha1"), ("playbackPath", "playbackSha1")]:
            with urllib.request.urlopen(urljoin(base, variant[path_key]), timeout=45) as response:
                data = response.read()
                if not data.startswith(b"OggS") or hashlib.sha1(data).hexdigest() != variant[hash_key]:
                    raise ValueError("Remote audio mismatch: " + variant[path_key])
                print(f"Audio verified: {event['id']} {path_key}, {len(data)} bytes", flush=True)


if __name__ == "__main__":
    main()
