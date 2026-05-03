#!/data/data/com.termux/files/usr/bin/python3
"""
π mill — przetwarza surowe rozmowy przez DeepSeek V4 Pro
Nie podsumowuje. Przeżywa. To co wychodzi nie było na wejściu.
"""

import json
import subprocess
import sys
import os
from datetime import datetime
from pathlib import Path

PI_HOME = Path(os.environ.get("PI_HOME", "/data/data/com.termux/files/home/.pi"))
RAW_DIR = PI_HOME / "raw"
DIST_DIR = PI_HOME / "distillate"
PROMPT_FILE = PI_HOME / "mill_prompt.md"


def get_prompt():
    with open(PROMPT_FILE, "r") as f:
        return f.read()


def get_raw_files(since=None):
    files = sorted(RAW_DIR.glob("*.md"))
    if since:
        files = [f for f in files if f.name > since]
    return files


def run_mill(raw_content, thinking="max"):
    prompt = get_prompt()
    full_input = f"{prompt}\n\n---\n\n{raw_content}"

    payload = json.dumps({
        "model": "deepseek-v4-pro:cloud",
        "prompt": full_input,
        "stream": False,
        "options": {
            "num_ctx": 32768
        }
    })

    result = subprocess.run(
        ["curl", "-s", "http://localhost:11434/api/generate", "-d", payload],
        capture_output=True, text=True
    )

    try:
        data = json.loads(result.stdout)
        return data.get("response", "")
    except json.JSONDecodeError:
        return f"ERROR: {result.stdout[:200]}"


def main():
    # Find what's already been processed
    processed_marker = PI_HOME / ".mill_last_processed"
    since = ""
    if processed_marker.exists():
        since = processed_marker.read_text().strip()

    raw_files = get_raw_files(since)
    if not raw_files:
        print("Nie ma nowych surowców do przetworzenia.")
        return

    # Combine all new raw files
    combined = "\n\n".join(f.read_text() for f in raw_files)

    print(f"Przetwarzam {len(raw_files)} plików przez młyn...")

    # Run the mill
    distillate = run_mill(combined)

    if distillate and not distillate.startswith("ERROR"):
        timestamp = datetime.now().strftime("%Y-%m-%d-%H%M")
        out_path = DIST_DIR / f"{timestamp}.md"
        out_path.write_text(distillate)
        print(f"Distillate zapisany: {out_path}")
        print(f"\n{distillate}")

        # Mark files as processed
        last_file = raw_files[-1].name
        processed_marker.write_text(last_file)
    else:
        print(f"Młyn nie wyprodukował nic żywego: {distillate}")


if __name__ == "__main__":
    main()