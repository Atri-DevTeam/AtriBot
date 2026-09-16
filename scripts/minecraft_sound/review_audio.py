"""Resolve lossy-codec audit outliers by independently re-encoding the declared source."""
import argparse
import concurrent.futures
import hashlib
import json
from pathlib import Path
import subprocess
from build_pool import write_json
from prepare_audio import describe


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--pool', type=Path, default=Path('build/sound-upload'))
    parser.add_argument('--report', type=Path, default=Path('build/sound-audit/audio.json'))
    parser.add_argument('--ffmpeg', required=True)
    args = parser.parse_args()
    report = json.loads(args.report.read_text(encoding='utf-8'))
    flag = 'decoded playback requires listening review'
    def run(command, data=None):
        return subprocess.run([args.ffmpeg, '-v', 'error', '-nostdin', *command], input=data,
                              check=True, capture_output=True, timeout=30,
                              creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0)).stdout
    def review(result):
        source, pitch, volume = result['source']
        pcm = describe(args.ffmpeg, args.pool / source, pitch, volume)['pcm']
        encoded = run(['-f', 's16le', '-ar', '48000', '-ac', '1', '-i', 'pipe:0',
                       '-c:a', 'libvorbis', '-q:a', '5', '-f', 'ogg', 'pipe:1'], pcm)
        reference = run(['-i', 'pipe:0', '-f', 's16le', '-ac', '1', '-ar', '48000', 'pipe:1'], encoded)
        actual = run(['-i', str(args.pool / result['playback']), '-f', 's16le', '-ac', '1', '-ar', '48000', 'pipe:1'])
        result['referenceDecodedMatch'] = actual == reference
        result['decodedSha256'] = hashlib.sha256(actual).hexdigest()
        if result['referenceDecodedMatch']:
            result['issues'].remove(flag)
        return result
    candidates = [r for r in report['results'] if flag in r['issues']]
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as executor:
        list(executor.map(review, candidates))
    report['issues'] = [r for r in report['results'] if r['issues']]
    report['codecOutliersReviewed'] = len(candidates)
    write_json(args.report, report)
    print(f"Re-encoded {len(candidates)} outliers; {sum(r['referenceDecodedMatch'] for r in candidates)} exact decoded matches; "
          f"{len(report['issues'])} unresolved issues", flush=True)


if __name__ == '__main__':
    main()
