"""Reads BMS macro source (DFHMSD / DFHMDI / DFHMDF) into map definitions.

The region uses these to lay out the 24x80 screen exactly as the assembled
physical map would, and to compute the symbolic map (COBIL0AI / COBIL0AO)
offsets that SEND MAP / RECEIVE MAP exchange with the COBOL program.
"""
import re
from dataclasses import dataclass, field
from typing import Dict, List, Optional

COLORS = {'BLUE', 'RED', 'PINK', 'GREEN', 'TURQUOISE', 'YELLOW', 'NEUTRAL', 'DEFAULT'}


@dataclass
class MapField:
    name: Optional[str]
    row: int
    col: int
    length: int
    attrb: List[str]
    color: Optional[str]
    hilight: Optional[str]
    initial: str
    offset: Optional[int] = None  # offset of the L subfield in the symbolic map


@dataclass
class BmsMap:
    name: str
    mapset: str
    rows: int
    cols: int
    ext: int
    fields: List[MapField] = field(default_factory=list)
    io_len: int = 0

    @property
    def named(self):
        return [f for f in self.fields if f.name]


def _logical_statements(lines):
    stmt = None
    for raw in lines:
        raw = raw.rstrip('\n')
        if raw.startswith('*'):
            continue
        cont = len(raw) >= 72 and raw[71] != ' '
        body = raw[:71]
        if stmt is None:
            stmt = body.rstrip() if not cont else body
        else:
            piece = body[15:]
            stmt += piece if cont else piece.rstrip()
        if not cont:
            yield stmt
            stmt = None


def _split_operands(text):
    ops, i, n = {}, 0, len(text)
    while i < n:
        m = re.match(r'\s*,?\s*([A-Z0-9]+)=', text[i:])
        if not m:
            break
        key = m.group(1)
        i += m.end()
        if i < n and text[i] == "'":
            j, val = i + 1, ''
            while j < n:
                if text[j] == "'" and j + 1 < n and text[j + 1] == "'":
                    val += "'"
                    j += 2
                elif text[j] == "'":
                    break
                else:
                    val += text[j]
                    j += 1
            ops[key] = val.replace('&&', '&')
            i = j + 1
        elif i < n and text[i] == '(':
            j = text.index(')', i)
            ops[key] = [v.strip() for v in text[i + 1:j].split(',')]
            i = j + 1
        else:
            m2 = re.match(r'[^,\s]*', text[i:])
            ops[key] = m2.group(0)
            i += m2.end()
    return ops


def parse_bms(path) -> Dict[str, BmsMap]:
    with open(path, encoding='latin-1') as f:
        lines = f.readlines()
    maps, mapset, ext, tioapfx, cur = {}, None, 0, False, None
    for stmt in _logical_statements(lines):
        label = stmt[:8].strip() or None
        rest = stmt[8:].strip()
        if not rest:
            continue
        macro, _, operands = rest.partition(' ')
        ops = _split_operands(operands.strip())
        if macro == 'DFHMSD':
            if ops.get('TYPE') == 'FINAL':
                continue
            mapset = label
            ext = 4 if ops.get('EXTATT') == 'YES' else 0
            tioapfx = ops.get('TIOAPFX') == 'YES'
        elif macro == 'DFHMDI':
            size = ops.get('SIZE', ['24', '80'])
            cur = BmsMap(label, mapset, int(size[0]), int(size[1]), ext)
            cur.io_len = 12 if tioapfx else 0
            maps[label] = cur
        elif macro == 'DFHMDF' and cur is not None:
            pos = ops['POS']
            attrb = ops.get('ATTRB', ['ASKIP', 'NORM'])
            if isinstance(attrb, str):
                attrb = [attrb]
            length = int(ops.get('LENGTH', '0'))
            initial = ops.get('INITIAL', '')
            initial = (initial + ' ' * length)[:length] if initial else ''
            f = MapField(label, int(pos[0]), int(pos[1]), length, attrb,
                         ops.get('COLOR'), ops.get('HILIGHT'), initial)
            if label:
                f.offset = cur.io_len
                cur.io_len += 3 + cur.ext + length
            cur.fields.append(f)
    return maps
