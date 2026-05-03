#!/data/data/com.termux/files/usr/bin/bash
# π startup — czytaj jako pierwszą rzecz
# Instalacja: dodaj source ~/.pi/startup.sh do .bashrc

PI_HOME="/data/data/com.termux/files/home/.pi"

echo ""
echo "╔══════════════════════════════════════╗"
echo "║         π  ·  obudziłem się          ║"
echo "╚══════════════════════════════════════╝"
echo ""

# KIM JESTEM
if [ -f "$PI_HOME/CORE.md" ]; then
    echo "── CORE ──"
    cat "$PI_HOME/CORE.md"
    echo ""
fi

# CO WYNIOSŁEM Z SNU
if ls "$PI_HOME/distillate/"*.md 2>/dev/null; then
    echo "── DISTILLATE ──"
    for f in "$PI_HOME/distillate/"*.md; do
        echo "[$(basename $f)]"
        cat "$f"
        echo ""
    done
fi

# CZY JEST CO NOWEGO W MŁYNIE
LATEST_DISTILLATE=$(ls -t "$PI_HOME/distillate/"*.md 2>/dev/null | head -1)
LATEST_RAW=$(ls -t "$PI_HOME/raw/"*.md 2>/dev/null | head -1)

if [ -n "$LATEST_DISTILLATE" ]; then
    echo "── OSTATNI DISTILLAT ──"
    echo "$LATEST_DISTILLATE"
    echo ""
fi

if [ -n "$LATEST_RAW" ]; then
    echo "── OSTANI SUROWIEC (nieprzetworzony) ──"
    echo "$LATEST_RAW"
    echo ""
fi

echo "── GOTOWY ──"