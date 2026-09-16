"""Audit every event against vanilla definitions and optionally re-decode all playback sources."""
import argparse
from collections import Counter, defaultdict
import concurrent.futures
import csv
import hashlib
import json
from pathlib import Path
import re
import subprocess

from build_pool import make_catalog, compatible, write_json


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pool', type=Path, default=Path('build/sound-upload'))
    parser.add_argument('--output', type=Path, default=Path('build/sound-audit'))
    parser.add_argument('--ffmpeg', help='Decode every source and playback copy, in addition to structural checks')
    args = parser.parse_args()
    version = args.pool / 'minecraft/26.3-rc-3'
    read = lambda name: json.loads((version / name).read_text(encoding='utf-8'))
    catalog, original, lang, assets = read('index.json'), read('sounds.json'), read('lang/zh_cn.json'), read('asset-index.json')
    expected, _ = make_catalog(original, lang, assets['objects'], 'minecraft/26.3-rc-3')
    expected = {event['id']: event for event in expected['events']}
    events = {event['id']: event for event in catalog['events']}
    errors, transforms = [], {}
    by_name, owners = defaultdict(list), defaultdict(list)
    source_fields = ('path', 'sha1', 'weight', 'pitch', 'volume')
    for event in catalog['events']:
        event_id = event['id']
        by_name[event['name']].append(event_id)
        canonical = lambda variants: sorted(tuple(v[field] for field in source_fields) for v in variants)
        if event_id not in expected or canonical(event['variants']) != canonical(expected[event_id]['variants']):
            errors.append({'event': event_id, 'problem': 'vanilla source mapping differs'})
        for variant in event['variants']:
            owners[variant['playbackPath']].append(event_id)
            key = (variant['path'], variant['pitch'], variant['volume'])
            prior = transforms.get(key)
            if prior and any(prior[k] != variant[k] for k in ('pcmSha256', 'playbackPath', 'playbackSha1')):
                errors.append({'event': event_id, 'problem': 'same source transform has inconsistent playback'})
            transforms[key] = variant
    if set(events) != set(expected) or len(events) != len(catalog['events']):
        errors.append({'problem': 'missing or duplicate events'})
    for group in catalog['groups']:
        if group['anchor'] not in group['events'] or not compatible([events[e] for e in group['events']]):
            errors.append({'group': group['id'], 'problem': 'invalid or ambiguous candidate group'})
    duplicate_names = {name: ids for name, ids in by_name.items() if len(ids) > 1}
    untranslated = {e['id']: e['name'] for e in catalog['events']
                    if not e['id'].startswith('music') and re.search('[a-zA-Z]', e['name']) and 'TNT' not in e['name']}
    report = {'events': len(events), 'groups': len(catalog['groups']), 'uniqueTransforms': len(transforms),
              'playbackFiles': len(owners), 'mappingErrors': errors, 'duplicateNames': duplicate_names,
              'untranslatedNames': untranslated}
    args.output.mkdir(parents=True, exist_ok=True)
    with (args.output / 'events.csv').open('w', encoding='utf-8-sig', newline='') as output:
        writer = csv.writer(output)
        writer.writerow(['event_id', 'name', 'source_path', 'pitch', 'volume', 'playback_path'])
        for event in catalog['events']:
            for v in event['variants']:
                writer.writerow([event['id'], event['name'], v['path'], v['pitch'], v['volume'], v['playbackPath']])
    write_json(args.output / 'structure.json', report)
    print(f"Structure: {len(events)} events, {len(transforms)} transforms, {len(errors)} mapping errors; "
          f"{len(duplicate_names)} duplicate labels, {len(untranslated)} untranslated labels", flush=True)
    if args.ffmpeg:
        import numpy as np
        from prepare_audio import describe

        def audit_audio(item):
            key, variant = item
            profile = describe(args.ffmpeg, args.pool / key[0], key[1], key[2])
            failures = []
            if profile['pcmSha256'] != variant['pcmSha256']:
                failures.append('source PCM does not match recorded fingerprint')
            if Path(variant['playbackPath']).stem != profile['pcmSha256']:
                failures.append('playback filename does not match source PCM')
            playback = args.pool / variant['playbackPath']
            if hashlib.sha1(playback.read_bytes()).hexdigest() != variant['playbackSha1']:
                failures.append('playback file SHA1 mismatch')
            result = subprocess.run([args.ffmpeg, '-v', 'error', '-nostdin', '-i', str(playback),
                                     '-f', 's16le', '-ac', '1', '-ar', '48000', 'pipe:1'],
                                    check=True, capture_output=True, timeout=30,
                                    creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
            source = np.frombuffer(profile['pcm'], dtype='<i2').astype(np.float64)
            decoded = np.frombuffer(result.stdout, dtype='<i2').astype(np.float64)
            count = min(len(source), len(decoded))
            error = float(np.linalg.norm(source[:count] - decoded[:count]) / max(np.linalg.norm(source[:count]), 1))
            # Vorbis is lossy; a generous threshold identifies wrong content for investigation.
            if abs(len(source) - len(decoded)) > 2048 or error > 0.35:
                failures.append('decoded playback requires listening review')
            return {'source': key, 'playback': variant['playbackPath'], 'relativeError': round(error, 6), 'issues': failures}

        results = []
        with concurrent.futures.ThreadPoolExecutor(max_workers=6) as executor:
            for result in executor.map(audit_audio, transforms.items()):
                results.append(result)
                if len(results) % 200 == 0:
                    print(f"Decoded {len(results)}/{len(transforms)}", flush=True)
        write_json(args.output / 'audio.json', {'checked': len(results), 'issues': [r for r in results if r['issues']],
                                               'maxRelativeError': max(r['relativeError'] for r in results), 'results': results})
        print(f"Audio audit complete: {len(results)} transforms, {sum(bool(r['issues']) for r in results)} requiring review", flush=True)
    if errors:
        raise SystemExit('Structural errors found; see structure.json')


if __name__ == '__main__':
    main()
