#!/usr/bin/env python3
"""Select the largest PDFs into an ignored, reproducible local corpus."""

from __future__ import annotations

import argparse
import csv
import hashlib
import os
from pathlib import Path
import shutil
import tempfile


def arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    parser.add_argument("--limit", type=int, default=250)
    return parser.parse_args()


def digest(path: Path) -> str:
    value = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            value.update(block)
    return value.hexdigest()


def main() -> None:
    args = arguments()
    source = args.source.expanduser().resolve(strict=True)
    destination = args.destination.expanduser().resolve()
    if not source.is_dir():
        raise SystemExit(f"source is not a directory: {source}")
    if args.limit < 1:
        raise SystemExit("--limit must be positive")
    if destination == source or source in destination.parents:
        raise SystemExit("destination must not be inside source")

    pdfs = sorted(
        (path for path in source.rglob("*") if path.is_file() and path.suffix.lower() == ".pdf"),
        key=lambda path: (-path.stat().st_size, path.relative_to(source).as_posix()),
    )[: args.limit]
    if not pdfs:
        raise SystemExit(f"no PDFs found below {source}")

    destination.parent.mkdir(parents=True, exist_ok=True)
    staging = Path(tempfile.mkdtemp(prefix=f".{destination.name}-", dir=destination.parent))
    try:
        rows: list[tuple[int, str, int, str, str]] = []
        for rank, path in enumerate(pdfs, 1):
            relative = path.relative_to(source).as_posix()
            target = staging / f"{rank:03d}-{path.name}"
            try:
                os.link(path, target)
                method = "hardlink"
            except OSError:
                shutil.copy2(path, target)
                method = "copy"
            rows.append((rank, relative, path.stat().st_size, digest(path), method))

        with (staging / "manifest.csv").open("w", newline="", encoding="utf-8") as output:
            writer = csv.writer(output)
            writer.writerow(("rank", "source_relative_path", "bytes", "sha256", "materialization"))
            writer.writerows(rows)

        if destination.exists() or destination.is_symlink():
            if destination.is_dir() and not destination.is_symlink():
                shutil.rmtree(destination)
            else:
                destination.unlink()
        staging.rename(destination)
    except BaseException:
        shutil.rmtree(staging, ignore_errors=True)
        raise

    total = sum(path.stat().st_size for path in pdfs)
    print(f"selected {len(pdfs)} PDFs ({total:,} bytes) into {destination}")


if __name__ == "__main__":
    main()
