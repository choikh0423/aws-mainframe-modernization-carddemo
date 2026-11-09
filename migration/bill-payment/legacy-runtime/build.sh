#!/usr/bin/env bash
# Translate and compile the legacy programs the demo region runs.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
app="$here/../../../app"
out="$here/build"
mkdir -p "$out"
programs="${PROGRAMS:-COBIL00C COMEN01C}"
gcc -O1 -o "$out/cicstask" "$here/cicsrt.c" -Wl,--export-dynamic $(cob-config --cflags --libs)
for p in $programs; do
  python3 "$here/translate.py" "$app/cbl/$p.cbl" "$out/$p.cob"
  # -fsign=EBCDIC: signed DISPLAY fields use the {ABC..}JKL.. overpunch that the
  # app/data/ASCII extracts are written in.
  cobc -m -std=ibm -fsign=EBCDIC -I "$here/copy" -I "$app/cpy" -I "$app/cpy-bms" \
       -o "$out/$p.so" "$out/$p.cob"
  echo "built $p"
done
