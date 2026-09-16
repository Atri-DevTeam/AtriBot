"""Verify the full upload directory and create a ZIP whose root matches the resource URL prefix."""
import argparse
from pathlib import Path
import subprocess
import sys
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pool", type=Path, default=Path("build/sound-upload"))
    parser.add_argument("--output", type=Path, default=Path("build/minecraft-sound-26.3-rc-3.zip"))
    args = parser.parse_args()
    pool, target = args.pool.resolve(), args.output.resolve()
    if target.is_relative_to(pool):
        parser.error("Archive must be outside the uploaded directory")
    subprocess.run([sys.executable, str(Path(__file__).with_name("verify_pool.py")), "--pool", str(pool)], check=True)
    target.parent.mkdir(parents=True, exist_ok=True)
    # OGG is already compressed. Storing it avoids wasting CPU without appreciable size savings.
    with zipfile.ZipFile(target, "w", compression=zipfile.ZIP_STORED, allowZip64=True) as archive:
        for path in sorted(pool.rglob("*")):
            if path.is_file():
                archive.write(path, path.relative_to(pool).as_posix())
    with zipfile.ZipFile(target) as archive:
        bad = archive.testzip()
        if bad is not None:
            raise ValueError("ZIP CRC check failed: " + bad)
        print(f"Packaged {len(archive.namelist())} files; CRC verified", flush=True)
    print(f"{target} ({target.stat().st_size / 1024 / 1024:.1f} MiB)", flush=True)


if __name__ == "__main__":
    main()
