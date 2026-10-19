"""Rebuild long quiz clips from their midpoint without changing answer groups.

Uses the published catalog as input and immutable vanilla audio from --sources.
Requires NumPy and FFmpeg with libvorbis. Short clips are left untouched.
"""
import argparse
import concurrent.futures
import csv
import hashlib
import io
import itertools
import json
import subprocess
import threading
from pathlib import Path

import numpy as np

RATE = 48000
CLIP_SAMPLES = 12 * RATE


def run_audio(ffmpeg, arguments, pcm=None):
    return subprocess.run(
        [ffmpeg, "-v", "error", "-nostdin", *arguments],
        input=pcm, capture_output=True, check=True, timeout=180,
        creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
    ).stdout


def center_clip(pcm):
    count = len(pcm) // 2
    start = max(0, (count - CLIP_SAMPLES) // 2)
    return pcm[start * 2:(start + CLIP_SAMPLES) * 2], start


def levels(pcm):
    wave = np.frombuffer(pcm, dtype="<i2").astype(np.float64) / 32768
    rms = float(np.sqrt(np.mean(wave ** 2)))
    frames = wave[:len(wave) // 4800 * 4800].reshape(-1, 4800)
    active = float(np.mean(np.sqrt(np.mean(frames ** 2, axis=1)) > 0.001))
    return {"rmsDb": round(float(20 * np.log10(max(rms, 1e-12))), 2),
            "activeFraction": round(active, 4)}


def compatible(ids, events):
    names, fingerprints = set(), set()
    for event_id in ids:
        event = events[event_id]
        hashes = {h for v in event["variants"]
                  for h in (v["playbackSha1"], "pcm:" + v["pcmSha256"])}
        if event["name"] in names or fingerprints & hashes:
            return False
        names.add(event["name"])
        fingerprints.update(hashes)
    return True


def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n",
                    encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pool", type=Path, required=True)
    parser.add_argument("--sources", type=Path, default=Path("doc/sounds"))
    parser.add_argument("--ffmpeg", default="ffmpeg")
    args = parser.parse_args()
    index_path = args.pool / "minecraft/26.3-rc-3/index.json"
    catalog = json.loads(index_path.read_bytes())
    original = json.loads(index_path.read_bytes())
    keys = {(v["path"], v.get("pitch", 1), v.get("volume", 1)): v
            for e in catalog["events"] for v in e["variants"] if v.get("excerpt")}
    locks = {key: threading.Lock() for key in range(32)}

    def work(key):
        source, pitch, volume = key
        old = keys[key]
        path = args.sources / source
        if hashlib.sha1(path.read_bytes()).hexdigest() != old["sha1"]:
            raise ValueError("Source checksum mismatch: " + source)
        pcm = run_audio(args.ffmpeg, [
            "-i", str(path),
            "-af", f"aresample=48000,asetrate=48000*{pitch},aresample=48000,volume={volume}",
            "-f", "s16le", "-ac", "1", "-ar", str(RATE), "pipe:1",
        ])
        clip, start = center_clip(pcm)
        if len(clip) != CLIP_SAMPLES * 2:
            raise ValueError("Marked excerpt is shorter than 12 seconds: " + source)
        previous_pcm = clip if old.get("excerptMode") == "center" else pcm[:CLIP_SAMPLES * 2]
        if hashlib.sha256(previous_pcm).hexdigest() != old["pcmSha256"]:
            raise ValueError("Existing PCM mapping could not be reproduced: " + source)
        digest = hashlib.sha256(clip).hexdigest()
        relative = f"minecraft/26.3-rc-3/playback/{digest}.ogg"
        target = args.pool / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        # Several events/sources can resolve to the same PCM. Encode each once.
        with locks[int(digest[:2], 16) % len(locks)]:
            if not target.exists():
                temporary = target.with_suffix(".tmp.ogg")
                run_audio(args.ffmpeg, [
                    "-y", "-f", "s16le", "-ar", str(RATE), "-ac", "1",
                    "-i", "pipe:0", "-c:a", "libvorbis", "-q:a", "5", str(temporary),
                ], clip)
                temporary.replace(target)
            encoded = target.read_bytes()
            if not encoded.startswith(b"OggS"):
                raise ValueError("Invalid playback OGG: " + relative)
            decoded = run_audio(args.ffmpeg, [
                "-i", str(target), "-f", "s16le", "-ar", str(RATE),
                "-ac", "1", "pipe:1",
            ])
            if abs(len(decoded) - len(clip)) > RATE * 2 * 0.05:
                raise ValueError("Encoded clip duration differs: " + relative)
        profile = {
            "pcmSha256": digest, "durationSeconds": 12.0,
            "playbackPath": relative,
            "playbackSha1": hashlib.sha1(encoded).hexdigest(),
            "excerpt": True, "excerptMode": "center",
            "excerptStartSeconds": round(start / RATE, 6),
            "sourcePlaybackSeconds": round(len(pcm) / (RATE * 2), 6),
        }
        report = {
            "source": source, "pitch": pitch, "volume": volume,
            "oldPlaybackPath": old["playbackPath"], **profile,
            "oldLevels": levels(pcm[:CLIP_SAMPLES * 2]),
            "centerLevels": levels(clip), "encodedLevels": levels(decoded),
        }
        return key, profile, report

    profiles, reports = {}, []
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as executor:
        for i, (key, profile, report) in enumerate(executor.map(work, sorted(keys)), 1):
            profiles[key] = profile
            reports.append(report)
            if i % 10 == 0 or i == len(keys):
                print(f"Rebuilt {i}/{len(keys)} long variants", flush=True)
    changed = 0
    for event in catalog["events"]:
        for variant in event["variants"]:
            if variant.get("excerpt"):
                variant.update(profiles[(variant["path"], variant.get("pitch", 1),
                                         variant.get("volume", 1))])
                changed += 1
        if any(v.get("excerpt") for v in event["variants"]):
            event["fingerprints"] = sorted({
                h for v in event["variants"]
                for h in (v["playbackSha1"], "pcm:" + v["pcmSha256"])
            })
    events = {e["id"]: e for e in catalog["events"]}
    for group in catalog["groups"]:
        if not any(group["anchor"] in ids and compatible(ids, events)
                   for ids in itertools.combinations(group["events"], 4)):
            raise ValueError("Group no longer has four distinct choices: " + group["id"])
    assert catalog["groups"] == original["groups"]
    assert len(catalog["events"]) == len(original["events"])
    for before, after in zip(original["events"], catalog["events"], strict=True):
        for old, new in zip(before["variants"], after["variants"], strict=True):
            if not old.get("excerpt"):
                assert old == new
            for field in ("path", "sha1", "pitch", "volume", "weight"):
                assert old.get(field) == new.get(field)
    quiet = [r["source"] for r in reports if r["encodedLevels"]["rmsDb"] < -60]
    write_json(args.pool / "center-excerpts-report.json", {
        "mode": "center", "seconds": 12, "transformedSources": len(keys),
        "updatedReferences": changed, "quietSources": quiet, "clips": reports,
    })
    if any(r["encodedLevels"]["rmsDb"] <= -120 for r in reports):
        raise ValueError("Silent central excerpt; index not written")
    if quiet:
        print(f"Note: {len(quiet)} central excerpts retain low original volume.", flush=True)
    csv_path = args.pool / "catalog-events.csv"
    rows = list(csv.reader(io.StringIO(csv_path.read_text(encoding="utf-8"))))
    header = rows[0]
    field = {name: i for i, name in enumerate(header)}
    for row in rows[1:]:
        key = (row[field["source_path"]], float(row[field["pitch"]]),
               float(row[field["volume"]]))
        if key in profiles:
            row[field["playback_path"]] = profiles[key]["playbackPath"]
    with csv_path.open("w", encoding="utf-8", newline="") as output:
        csv.writer(output, lineterminator="\n").writerows(rows)
    write_json(index_path, catalog)
    print(f"Validated {len(catalog['groups'])} groups; updated {changed} references.", flush=True)


if __name__ == "__main__":
    main()
