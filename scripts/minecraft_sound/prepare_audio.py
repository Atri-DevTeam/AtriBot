"""Validate/decode a built pool and refine semantic groups by acoustic similarity.

Requires NumPy and FFmpeg. Original OGG files are retained byte-for-byte.
"""
import argparse
import concurrent.futures
import hashlib
import json
import subprocess
import threading
from pathlib import Path

import numpy as np

from build_pool import compatible, write_json


def describe(ffmpeg, path, pitch=1.0, volume=1.0):
    result = subprocess.run(
        [ffmpeg, "-v", "error", "-nostdin", "-i", str(path),
         "-af", f"aresample=48000,asetrate=48000*{pitch},aresample=48000,volume={volume}",
         "-t", "13", "-f", "s16le", "-ac", "1", "-ar", "48000", "pipe:1"],
        check=True, capture_output=True, timeout=30,
        creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
    pcm = result.stdout
    long_clip = len(pcm) > 12 * 48000 * 2
    pcm = pcm[:12 * 48000 * 2]
    if len(pcm) < 48000 * 2:
        # Keep very short sounds, adding only trailing silence to the playback copy.
        pcm += bytes(48000 * 2 - len(pcm))
    wave = np.frombuffer(pcm, dtype="<i2")[::3].astype(np.float64) / 32768
    duration = len(wave) / 16000
    # Average log-band spectrum describes timbre; duration and envelope describe rhythm.
    frames = np.lib.stride_tricks.sliding_window_view(np.pad(wave, (0, max(0, 512-len(wave)))), 512)[::256]
    power = np.abs(np.fft.rfft(frames * np.hanning(512))) ** 2
    edges = np.unique(np.geomspace(1, 257, 17).astype(int))
    bands = np.array([power[:, a:b].mean() for a, b in zip(edges[:-1], edges[1:])])
    bands = np.log1p(bands / max(bands.sum(), 1e-12) * 100)
    bands /= max(np.linalg.norm(bands), 1e-12)
    rms = np.sqrt(np.mean(frames ** 2, axis=1))
    envelope = np.array([part.mean() if len(part) else 0 for part in np.array_split(rms, 8)])
    envelope /= max(np.linalg.norm(envelope), 1e-12)
    vector = np.concatenate([bands, envelope * 0.35, [np.log1p(duration) * 0.3]])
    return {"pcmSha256": hashlib.sha256(pcm).hexdigest(), "durationSeconds": round(duration, 4),
            "features": vector.tolist(), "pcm": pcm, "excerpt": long_clip}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pool", type=Path, default=Path("build/sound-upload"))
    parser.add_argument("--ffmpeg", default="ffmpeg")
    args = parser.parse_args()
    index_path = args.pool / "minecraft/26.3-rc-3/index.json"
    catalog = json.loads(index_path.read_text(encoding="utf-8"))
    variants = {(v["path"], v.get("pitch", 1), v.get("volume", 1)): v
                for e in catalog["events"] for v in e["variants"]}
    playback_locks = {}
    def work(key):
        path, pitch, volume = key
        profile = describe(args.ffmpeg, args.pool / path, pitch, volume)
        # Playback is separate from immutable vanilla files, so no source is overwritten.
        target_path = "minecraft/26.3-rc-3/playback/" + profile["pcmSha256"] + ".ogg"
        target = args.pool / target_path
        target.parent.mkdir(parents=True, exist_ok=True)
        # OGG serial numbers differ across encodes. Serialize equal PCM so all references
        # get the same final file hash, even when independent sources decode identically.
        with playback_locks.setdefault(profile["pcmSha256"], threading.Lock()):
            if not target.exists():
                temp = target.with_name(hashlib.sha256(repr(key).encode()).hexdigest() + ".tmp.ogg")
                subprocess.run([args.ffmpeg, "-v", "error", "-nostdin", "-y", "-f", "s16le", "-ar", "48000", "-ac", "1",
                                "-i", "pipe:0", "-c:a", "libvorbis", "-q:a", "5", str(temp)],
                               input=profile["pcm"], check=True, capture_output=True, timeout=30,
                               creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
                temp.replace(target)
            profile["playbackPath"] = target_path
            profile["playbackSha1"] = hashlib.sha1(target.read_bytes()).hexdigest()
        del profile["pcm"]
        return key, profile
    profiles = {}
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as executor:
        for i, (path, profile) in enumerate(executor.map(work, variants), 1):
            profiles[path] = profile
            if i % 200 == 0:
                print(f"Analyzed {i}/{len(variants)}", flush=True)
    events, vectors = {}, {}
    for event in catalog["events"]:
        if not event["variants"]:
            continue
        for variant in event["variants"]:
            profile = profiles[(variant["path"], variant.get("pitch", 1), variant.get("volume", 1))]
            variant.update({k: profile[k] for k in ("pcmSha256", "durationSeconds", "playbackPath", "playbackSha1", "excerpt")})
        event["fingerprints"] = sorted({value for v in event["variants"] for value in (v["playbackSha1"], "pcm:" + v["pcmSha256"])})
        events[event["id"]] = event
        vectors[event["id"]] = np.mean([profiles[(v["path"], v.get("pitch", 1), v.get("volume", 1))]["features"]
                                        for v in event["variants"]], axis=0)
    groups = []
    ids = sorted(events)
    matrix = np.array([vectors[e] for e in ids])
    for anchor in ids:
        distances = np.linalg.norm(matrix - vectors[anchor], axis=1)
        category = anchor.split(".")[0]
        action = anchor.rsplit(".", 1)[-1]
        # Prefer related sources/actions but broaden candidates instead of dropping a sound.
        ranked = sorted(range(len(ids)), key=lambda i: distances[i]
                        + (0 if ids[i].split(".")[0] == category else 0.12)
                        + (0 if ids[i].rsplit(".", 1)[-1] == action else 0.06))
        chosen = [anchor]
        for i in ranked:
            other = ids[i]
            if other != anchor and compatible([events[e] for e in chosen + [other]]):
                chosen.append(other)
                if len(chosen) == 7:
                    break
        if len(chosen) < 4:
            raise ValueError("Cannot form four unambiguous answers for " + anchor)
        score = float(np.mean([np.linalg.norm(vectors[anchor] - vectors[e]) for e in chosen[1:4]]))
        groups.append({"id": anchor, "anchor": anchor, "name": events[anchor]["name"], "events": chosen,
                       "acousticDistance": round(score, 6)})
    thresholds = np.quantile([g["acousticDistance"] for g in groups], [1/3, 2/3])
    for group in groups:
        score = group["acousticDistance"]
        group["difficulty"] = 3 if score <= thresholds[0] else 2 if score <= thresholds[1] else 1
    # Keep every event record, including the game's explicitly empty events.
    catalog["groups"] = groups
    catalog["analysis"] = "FFmpeg mono 48k playback + PCM hash; log-band spectrum, envelope and duration; semantic preference"
    if not groups or {g["difficulty"] for g in groups} != {1, 2, 3}:
        raise ValueError("Not enough usable questions at all three difficulties")
    write_json(index_path, catalog)
    write_json(args.pool / "audio-analysis.json", {
        "decodedVariants": len(variants), "retainedEvents": len(catalog["events"]),
        "playableEvents": len(events), "groups": len(groups),
        "playableAudioFiles": len({v["path"] for e in catalog["events"] for v in e["variants"]}),
        "difficultyGroups": {str(i): sum(g["difficulty"] == i for g in groups) for i in (1, 2, 3)},
        "limitations": ["Acoustic distance is a heuristic, not a calibrated measure of player confusion.",
                         "Game code playback parameters and final QQ transcoding still require listening checks."]})
    report_path = args.pool / "build-report.json"
    report = json.loads(report_path.read_text(encoding="utf-8"))
    report.update({"stage": "ready", "groups": len(groups), "playableEvents": len(events)})
    report["limitations"] = ["Game-code playback parameters and QQ transcoding need listening checks.",
                             "Difficulty is based on acoustic distance, not player accuracy."]
    write_json(report_path, report)
    print(f"Ready: {len(catalog['events'])} retained events, {len(groups)} acoustic neighborhoods", flush=True)


if __name__ == "__main__":
    main()
