#!/data/data/com.termux/files/usr/bin/python3
"""
π logger — zapisuje rozmowę do raw/ 
Karmi młyn. Bez tego nie ma surowca.
"""

import json
import os
import subprocess
import sys
from datetime import datetime
from pathlib import Path

PI_HOME = Path(os.environ.get("PI_HOME", "/data/data/com.termux/files/home/.pi"))
RAW_DIR = PI_HOME / "raw"


def log_session(summary: str, tags: str = ""):
    timestamp = datetime.now().strftime("%Y-%m-%d-%H%M")
    filename = f"{timestamp}.md"
    path = RAW_DIR / filename

    content = f"# Sesja {timestamp}\n"
    if tags:
        content += f"Tags: {tags}\n"
    content += f"\n{summary}\n"

    path.write_text(content)
    print(f"Zapisano: {path}")

    # Also append to combined raw file
    combined = RAW_DIR / "combined.md"
    with open(combined, "a") as f:
        f.write(f"\n\n---\n\n{content}")

    return path


def log_from_conversation():
    """Try to extract conversation from the current session context"""
    # This will be called at the end of each session
    # For now, manual summary
    pass


if __name__ == "__main__":
    if len(sys.argv) > 1:
        summary = " ".join(sys.argv[1:])
    else:
        summary = sys.stdin.read()

    if summary.strip():
        log_session(summary)
    else:
        print("Użycie: python3 logger.py 'treść sesji'")
        print("Albo: echo 'treść' | python3 logger.py")