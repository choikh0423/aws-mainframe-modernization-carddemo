#!/usr/bin/env python3
"""Mechanical call-graph / entry-point extractor for the CardDemo estate.

Emits, with <file>:<line> cites:
  * caller -> callee edges from COBOL CALL 'LIT' / EXEC CICS XCTL|LINK|START PROGRAM|TRANSID
  * dynamic CALL <identifier> sites (unresolved edges)
  * JCL EXEC PGM= steps (batch entry points)
  * CSD DEFINE TRANSACTION/PROGRAM pairs (online entry points)
Comment lines (column 7 = '*') are skipped.
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC_DIRS = [
    "app/cbl",
    "app/app-transaction-type-db2",
    "app/app-authorization-ims-db2-mq",
    "app/app-vsam-mq",
]
COBOL_EXT = {".cbl", ".CBL", ".cob", ".COB"}

CALL_LIT = re.compile(r"\bCALL\s+'([A-Z0-9$#@]+)'", re.I)
CALL_VAR = re.compile(r"\bCALL\s+([A-Z0-9\-]+)\s*(?:USING|\.|$)", re.I)
XCTL_LINK = re.compile(r"\b(XCTL|LINK)\b", re.I)
PROGRAM_LIT = re.compile(r"PROGRAM\s*\(\s*'?([A-Z0-9$#@\-]+)'?\s*\)", re.I)
TRANSID_LIT = re.compile(r"TRANSID\s*\(\s*'?([A-Z0-9$#@\-]+)'?\s*\)", re.I)
START_TX = re.compile(r"\bSTART\b.*TRANSID", re.I | re.S)
EXEC_PGM = re.compile(r"^//(\S+)\s+EXEC\s+PGM=([A-Z0-9$#@]+)", re.I)
CSD_TRAN = re.compile(r"DEFINE\s+TRANSACTION\((\S+)\)", re.I)
CSD_PROG_OF_TRAN = re.compile(r"PROGRAM\((\S+)\)", re.I)


def cobol_lines(path: Path):
    with path.open(encoding="latin-1") as fh:
        for n, raw in enumerate(fh, 1):
            if len(raw) > 6 and raw[6] in "*/":
                continue
            yield n, raw.rstrip("\n")


def cobol_files():
    for d in SRC_DIRS:
        base = ROOT / d
        if not base.exists():
            continue
        for p in sorted(base.rglob("*")):
            if p.suffix in COBOL_EXT and p.is_file():
                yield p


def main():
    edges, dynamic, statics = [], [], set()
    for path in cobol_files():
        prog = path.stem.upper()
        statics.add(prog)
        rel = path.relative_to(ROOT)
        # join continuation-prone EXEC CICS blocks by scanning a sliding window
        lines = list(cobol_lines(path))
        text_by_line = {n: t for n, t in lines}
        for n, line in lines:
            for m in CALL_LIT.finditer(line):
                edges.append({"from": prog, "to": m.group(1).upper(), "kind": "CALL",
                              "cite": f"{rel}:{n}"})
            if not CALL_LIT.search(line):
                mv = CALL_VAR.search(line)
                if mv and not mv.group(1).upper().startswith("TO"):
                    dynamic.append({"from": prog, "var": mv.group(1).upper(),
                                    "cite": f"{rel}:{n}"})
            if XCTL_LINK.search(line):
                window = " ".join(text_by_line.get(k, "") for k in range(n, n + 4))
                mp = PROGRAM_LIT.search(window)
                kind = "XCTL" if re.search(r"\bXCTL\b", line, re.I) else "LINK"
                edges.append({"from": prog,
                              "to": (mp.group(1).upper() if mp else "<DYNAMIC>"),
                              "kind": kind, "cite": f"{rel}:{n}"})
            if re.search(r"\bSTART\b", line, re.I) and "CICS" in " ".join(
                    text_by_line.get(k, "") for k in range(max(1, n - 3), n + 1)).upper():
                window = " ".join(text_by_line.get(k, "") for k in range(n, n + 4))
                mt = TRANSID_LIT.search(window)
                if mt:
                    edges.append({"from": prog, "to": f"TRAN:{mt.group(1).upper()}",
                                  "kind": "START", "cite": f"{rel}:{n}"})

    jcl_steps = []
    for d in ["app/jcl", "app/proc"]:
        for p in sorted((ROOT / d).glob("*")):
            if not p.is_file():
                continue
            rel = p.relative_to(ROOT)
            with p.open(encoding="latin-1") as fh:
                for n, line in enumerate(fh, 1):
                    m = EXEC_PGM.match(line)
                    if m:
                        jcl_steps.append({"job": p.stem.upper(), "step": m.group(1).upper(),
                                          "pgm": m.group(2).upper(), "cite": f"{rel}:{n}"})

    csd = []
    csd_path = ROOT / "app/csd/CARDDEMO.CSD"
    if csd_path.exists():
        with csd_path.open(encoding="latin-1") as fh:
            lines = fh.readlines()
        for n, line in enumerate(lines, 1):
            mt = CSD_TRAN.search(line)
            if mt:
                window = " ".join(lines[n - 1:n + 4])
                mp = CSD_PROG_OF_TRAN.search(window)
                csd.append({"tran": mt.group(1).upper(),
                            "pgm": mp.group(1).upper() if mp else None,
                            "cite": f"app/csd/CARDDEMO.CSD:{n}"})

    out = {"programs": sorted(statics), "edges": edges, "dynamic_calls": dynamic,
           "jcl_steps": jcl_steps, "csd_transactions": csd}
    json.dump(out, sys.stdout, indent=2)


if __name__ == "__main__":
    main()
