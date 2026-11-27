#!/usr/bin/env python3
"""
Builds a pre-indexed, read-only SQLite database (with B-Tree + external-content FTS4)
from ECDICT Ultimate (3.4M entries) and compresses it to .db.gz for bundling into
Android APK assets/prebuilt/ecdict_ultimate.db.gz.

Usage (CI / local):
    python3 tools/build_prebuilt_ecdict.py \
        --output app/src/main/assets/prebuilt/ecdict_ultimate.db.gz

Usage (offline test with local zip/csv):
    python3 tools/build_prebuilt_ecdict.py \
        --input /path/to/ecdict-ultimate-csv.zip \
        --output /tmp/ecdict_ultimate.db.gz
"""

import argparse
import csv
import gzip
import io
import os
import shutil
import sqlite3
import sys
import tempfile
import time
import urllib.request
import zipfile

ECDICT_ULTIMATE_URL = (
    "https://github.com/skywind3000/ECDICT-ultimate/releases/download/1.0.0/ecdict-ultimate-csv.zip"
)
SCHEMA_VERSION = "1.6.1"
BATCH_SIZE = 20000

csv.field_size_limit(min(sys.maxsize, 10 * 1024 * 1024))


def download_file(url: str, dest: str) -> None:
    print(f"[ecdict-builder] Downloading {url} ...")
    req = urllib.request.Request(url, headers={"User-Agent": "GeminiDict-CI-Builder/1.0"})
    with urllib.request.urlopen(req, timeout=180) as resp, open(dest, "wb") as out:
        total = int(resp.headers.get("Content-Length") or 0)
        done = 0
        last_pct = -1
        while True:
            chunk = resp.read(256 * 1024)
            if not chunk:
                break
            out.write(chunk)
            done += len(chunk)
            if total > 0:
                pct = int(done * 100 / total)
                if pct >= last_pct + 10:
                    last_pct = pct
                    print(f"[ecdict-builder] Download {pct}% ({done / (1024 * 1024):.1f} MB)")
    print(f"[ecdict-builder] Downloaded to {dest} ({os.path.getsize(dest) / (1024 * 1024):.1f} MB)")


def parse_int(val: str) -> int:
    if not val:
        return 0
    try:
        return int(val.strip())
    except ValueError:
        return 0


def iter_ecdict_rows(input_path: str):
    """Yields tuples matching the `entries` table from either a .zip containing CSV or a raw .csv."""
    if zipfile.is_zipfile(input_path):
        with zipfile.ZipFile(input_path, "r") as zf:
            csv_names = [n for n in zf.namelist() if n.lower().endswith(".csv") and not n.endswith("/")]
            if not csv_names:
                raise RuntimeError(f"No .csv file found inside {input_path}")
            target = csv_names[0]
            print(f"[ecdict-builder] Reading CSV entry '{target}' from ZIP ...")
            with zf.open(target, "r") as raw:
                text_stream = io.TextIOWrapper(raw, encoding="utf-8", errors="replace", newline="")
                yield from _iter_csv_stream(text_stream)
    else:
        print(f"[ecdict-builder] Reading CSV file '{input_path}' ...")
        with open(input_path, "r", encoding="utf-8", errors="replace", newline="") as f:
            yield from _iter_csv_stream(f)


def _iter_csv_stream(text_stream):
    reader = csv.reader(text_stream)
    header = next(reader, None)
    if header is None:
        return
    # Standard ECDICT columns:
    # 0:word, 1:phonetic, 2:definition, 3:translation, 4:pos, 5:collins, 6:oxford, 7:tag, 8:bnc, 9:frq
    for row in reader:
        if not row:
            continue
        word = row[0].strip()
        if not word:
            continue
        word_lower = word.lower()
        phonetic = row[1].strip() if len(row) > 1 else ""
        definition = row[2].replace("\\n", "\n").strip() if len(row) > 2 else ""
        translation = row[3].replace("\\n", "\n").strip() if len(row) > 3 else ""
        if not translation and not definition:
            continue
        pos = row[4].strip() if len(row) > 4 else ""
        collins = parse_int(row[5]) if len(row) > 5 else 0
        oxford = parse_int(row[6]) if len(row) > 6 else 0
        tag = row[7].strip() if len(row) > 7 else ""
        frq = parse_int(row[9]) if len(row) > 9 else 0
        yield (word, word_lower, phonetic, definition, translation, pos, collins, oxford, tag, frq)


def build_sqlite(input_path: str, db_path: str) -> int:
    if os.path.exists(db_path):
        os.remove(db_path)

    conn = sqlite3.connect(db_path)
    cur = conn.cursor()
    cur.execute("PRAGMA page_size = 4096")
    cur.execute("PRAGMA journal_mode = OFF")
    cur.execute("PRAGMA synchronous = OFF")
    cur.execute("PRAGMA temp_store = MEMORY")
    cur.execute("PRAGMA cache_size = -131072")  # 128 MB cache during CI build

    cur.execute(
        """
        CREATE TABLE entries (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            word TEXT NOT NULL,
            wordLower TEXT NOT NULL,
            phonetic TEXT NOT NULL DEFAULT '',
            definition TEXT NOT NULL DEFAULT '',
            translation TEXT NOT NULL DEFAULT '',
            pos TEXT NOT NULL DEFAULT '',
            collins INTEGER NOT NULL DEFAULT 0,
            oxford INTEGER NOT NULL DEFAULT 0,
            tag TEXT NOT NULL DEFAULT '',
            frq INTEGER NOT NULL DEFAULT 0
        )
        """
    )

    cur.execute(
        """
        CREATE TABLE meta (
            key TEXT PRIMARY KEY,
            value TEXT NOT NULL
        )
        """
    )

    insert_sql = (
        "INSERT INTO entries "
        "(word, wordLower, phonetic, definition, translation, pos, collins, oxford, tag, frq) "
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
    )

    t0 = time.time()
    count = 0
    batch = []
    cur.execute("BEGIN")
    for row in iter_ecdict_rows(input_path):
        batch.append(row)
        if len(batch) >= BATCH_SIZE:
            cur.executemany(insert_sql, batch)
            count += len(batch)
            batch.clear()
            if count % 500000 == 0:
                print(f"[ecdict-builder] Inserted {count:,} rows ({time.time() - t0:.1f}s) ...")
    if batch:
        cur.executemany(insert_sql, batch)
        count += len(batch)
        batch.clear()
    conn.commit()
    print(f"[ecdict-builder] Inserted total {count:,} rows in {time.time() - t0:.1f}s")

    t1 = time.time()
    print("[ecdict-builder] Building B-Tree index on (wordLower, frq) ...")
    cur.execute("CREATE INDEX idx_entries_wordLower_frq ON entries(wordLower, frq)")
    conn.commit()
    print(f"[ecdict-builder] B-Tree index built in {time.time() - t1:.1f}s")

    t2 = time.time()
    print("[ecdict-builder] Building external-content FTS4 index (unicode61) ...")
    cur.execute(
        """
        CREATE VIRTUAL TABLE entries_fts USING fts4(
            content='entries',
            word,
            translation,
            tokenize=unicode61
        )
        """
    )
    cur.execute("INSERT INTO entries_fts(entries_fts) VALUES('rebuild')")
    cur.execute("INSERT INTO entries_fts(entries_fts) VALUES('optimize')")
    conn.commit()
    print(f"[ecdict-builder] FTS4 index built & optimized in {time.time() - t2:.1f}s")

    cur.executemany(
        "INSERT OR REPLACE INTO meta(key, value) VALUES (?, ?)",
        [
            ("schema_version", SCHEMA_VERSION),
            ("source_id", "ecdict_ultimate"),
            ("entry_count", str(count)),
        ],
    )
    conn.commit()

    print("[ecdict-builder] Running ANALYZE & VACUUM ...")
    cur.execute("ANALYZE")
    cur.execute("VACUUM")
    cur.execute("PRAGMA journal_mode = DELETE")
    conn.close()

    size_mb = os.path.getsize(db_path) / (1024 * 1024)
    print(f"[ecdict-builder] SQLite DB ready: {db_path} ({size_mb:.1f} MB)")
    return count


def compress_gzip(src_path: str, dst_path: str, level: int = 6) -> None:
    os.makedirs(os.path.dirname(os.path.abspath(dst_path)), exist_ok=True)
    tmp_dst = dst_path + ".tmp"
    t0 = time.time()
    print(f"[ecdict-builder] Compressing {src_path} -> {dst_path} (gzip level={level}) ...")
    with open(src_path, "rb") as f_in, gzip.open(tmp_dst, "wb", compresslevel=level) as f_out:
        shutil.copyfileobj(f_in, f_out, length=1024 * 1024)
    os.replace(tmp_dst, dst_path)
    gz_mb = os.path.getsize(dst_path) / (1024 * 1024)
    print(f"[ecdict-builder] Compressed asset ready: {dst_path} ({gz_mb:.1f} MB) in {time.time() - t0:.1f}s")


def main() -> None:
    parser = argparse.ArgumentParser(description="Pre-build indexed ECDICT Ultimate SQLite asset for GeminiDict")
    parser.add_argument("--url", default=ECDICT_ULTIMATE_URL, help="Download URL for ecdict-ultimate-csv.zip")
    parser.add_argument("--input", default="", help="Optional local .zip or .csv path (skips download)")
    parser.add_argument(
        "--output",
        default="app/src/main/assets/prebuilt/ecdict_ultimate.db.gz",
        help="Output .db.gz path inside Android assets",
    )
    parser.add_argument("--compress-level", type=int, default=6, help="Gzip compression level (1-9)")
    args = parser.parse_args()

    with tempfile.TemporaryDirectory(prefix="ecdict_build_") as tmpdir:
        input_path = args.input
        if not input_path:
            input_path = os.path.join(tmpdir, "ecdict-ultimate-csv.zip")
            download_file(args.url, input_path)

        db_path = os.path.join(tmpdir, "ecdict_ultimate.db")
        count = build_sqlite(input_path, db_path)
        if count <= 0:
            raise RuntimeError("Built database has 0 entries!")
        compress_gzip(db_path, args.output, level=args.compress_level)


if __name__ == "__main__":
    main()
