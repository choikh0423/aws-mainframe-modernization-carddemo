#!/usr/bin/env python3
"""CICS command translator for the demo region.

Plays the part of the IBM CICS translator (DFHECP1$): every
``EXEC CICS ... END-EXEC`` block in a fixed-format COBOL program is replaced
by plain COBOL that fills CICS-ARGS and does ``CALL 'CICSRT'``.  Nothing else
in the program changes, so the business logic that runs is the original
source line for line.

Usage: translate.py IN.cbl OUT.cob
"""
import re
import sys

# EIBRESP values, as documented for DFHRESP().
RESP_CODES = {
    'NORMAL': 0, 'ERROR': 1, 'RDATT': 2, 'WRBRK': 3, 'EOF': 4, 'EODS': 5,
    'EOC': 6, 'INBFMH': 7, 'ENDINPT': 8, 'NONVAL': 9, 'NOSTART': 10,
    'TERMIDERR': 11, 'FILENOTFOUND': 12, 'DSIDERR': 12, 'NOTFND': 13,
    'DUPREC': 14, 'DUPKEY': 15, 'INVREQ': 16, 'IOERR': 17, 'NOSPACE': 18,
    'NOTOPEN': 19, 'ENDFILE': 20, 'ILLOGIC': 21, 'LENGERR': 22,
    'QZERO': 23, 'SIGNAL': 24, 'QBUSY': 25, 'ITEMERR': 26, 'PGMIDERR': 27,
    'TRANSIDERR': 28, 'ENDDATA': 29, 'EXPIRED': 31, 'MAPFAIL': 36,
    'INVMPSZ': 38, 'OVERFLOW': 40, 'INVLDC': 41, 'NOSTG': 42,
    'NOTAUTH': 70, 'DISABLED': 84,
}

# Option -> slot.  P1/P2 carry names, A1..A3 carry data areas by reference.
NAME_OPTS = {'MAP': 'P1', 'DATASET': 'P1', 'FILE': 'P1', 'PROGRAM': 'P1',
             'TRANSID': 'P1', 'QUEUE': 'P1', 'MAPSET': 'P2'}
DATA_OPTS = {'INTO': 'A1', 'FROM': 'A1', 'COMMAREA': 'A1', 'ABSTIME': 'A1',
             'RIDFLD': 'A2', 'YYYYMMDD': 'A2', 'MMDDYY': 'A2',
             'DDMMYY': 'A2', 'YYMMDD': 'A2', 'TIME': 'A3'}
SEP_OPTS = {'DATESEP': 'CICS-DSEP', 'TIMESEP': 'CICS-TSEP'}
# Commands whose LENGTH option, when given, sets the length of A1.
EXPLICIT_LENGTH_CMDS = {'SEND TEXT', 'RETURN', 'XCTL', 'LINK'}

WS_BLOCK = [
    '       COPY CICSARGS.',
]
LINKAGE_COPY = '       COPY DFHEIBLK.'


def tokenize(body):
    """Split 'SEND MAP('X') FROM(Y) ERASE' into [(KEY, arg-or-None), ...]."""
    out, i, n = [], 0, len(body)
    while i < n:
        if body[i].isspace():
            i += 1
            continue
        m = re.match(r'[A-Za-z0-9-]+', body[i:])
        if not m:
            raise SyntaxError('cannot parse EXEC CICS near: ' + body[i:i + 30])
        key = m.group(0).upper()
        i += len(key)
        while i < n and body[i] == ' ':
            i += 1
        arg = None
        if i < n and body[i] == '(':
            depth, j, quote = 0, i, None
            while j < n:
                c = body[j]
                if quote:
                    if c == quote:
                        quote = None
                elif c in "'\"":
                    quote = c
                elif c == '(':
                    depth += 1
                elif c == ')':
                    depth -= 1
                    if depth == 0:
                        break
                j += 1
            arg = body[i + 1:j].strip()
            i = j + 1
        out.append((key, arg))
    return out


def emit(stmts):
    """Lay statements out in Area B, wrapping on word boundaries."""
    lines = []
    for s in stmts:
        words, cur = s.split(' '), ''
        for w in words:
            if cur and len('           ' + cur + ' ' + w) > 72:
                lines.append('           ' + cur)
                cur = '    ' + w
            else:
                cur = (cur + ' ' + w) if cur else w
        lines.append('           ' + cur)
    return lines


def translate_block(body):
    toks = tokenize(body)
    cmd = toks[0][0]
    opts = toks[1:]
    keys = [k for k, _ in opts]
    if cmd in ('SEND', 'RECEIVE') and 'TEXT' in keys:
        cmd += ' TEXT'
    elif cmd in ('SEND', 'RECEIVE') and 'MAP' in keys:
        cmd += ' MAP'
    stmts = ["MOVE SPACES TO CICS-ARGS",
             "MOVE '%s' TO CICS-CMD" % cmd,
             "MOVE ZERO TO CICS-KEYLEN CICS-L1 CICS-L2 CICS-L3"]
    flags, data, resp, resp2, length = [], {}, None, None, None
    for key, arg in opts:
        if key in NAME_OPTS and arg is not None:
            stmts.append('MOVE %s TO CICS-%s' % (arg, NAME_OPTS[key]))
        elif key in DATA_OPTS and arg is not None:
            data[DATA_OPTS[key]] = arg
        elif key in SEP_OPTS:
            stmts.append('MOVE %s TO %s' % (arg or ("'/'" if key == 'DATESEP' else "':'"), SEP_OPTS[key]))
        elif key == 'KEYLENGTH':
            stmts.append('MOVE %s TO CICS-KEYLEN' % arg)
        elif key == 'LENGTH':
            length = arg
        elif key == 'RESP':
            resp = arg
            flags.append('RESP')
        elif key == 'RESP2':
            resp2 = arg
        elif arg is None:
            flags.append(key)
        else:
            flags.append(key)  # options with values the region does not need
    if 'NOHANDLE' in flags and 'RESP' not in flags:
        flags.append('RESP')
    if flags:
        stmts.append("MOVE '%s' TO CICS-FLAGS" % ' '.join(flags)[:64])
    using = []
    for slot, lfield in (('A1', 'CICS-L1'), ('A2', 'CICS-L2'), ('A3', 'CICS-L3')):
        if slot in data:
            ref = data[slot]
            if slot == 'A1' and length and cmd in EXPLICIT_LENGTH_CMDS:
                stmts.append('MOVE %s TO %s' % (length, lfield))
            else:
                stmts.append('MOVE LENGTH OF %s TO %s' % (ref, lfield))
            using.append(ref)
        else:
            using.append('CICS-NULL')
    stmts.append("CALL 'CICSRT' USING DFHEIBLK CICS-ARGS " + ' '.join(using))
    if resp:
        stmts.append('MOVE EIBRESP TO %s' % resp)
    if resp2:
        stmts.append('MOVE EIBRESP2 TO %s' % resp2)
    return stmts


def translate(src_lines):
    out, i = [], 0
    have_linkage = any(re.match(r'^.{6} +LINKAGE +SECTION', l) for l in src_lines)
    have_commarea = any(re.search(r'\b01 +DFHCOMMAREA\b', l) for l in src_lines)
    while i < len(src_lines):
        line = src_lines[i].rstrip('\n')
        code = line[:72]
        indicator = code[6:7] if len(code) > 6 else ' '
        if indicator in ('*', '/'):
            out.append(line)
            i += 1
            continue
        area = code[7:] if len(code) > 7 else ''
        if re.match(r'\s*WORKING-STORAGE\s+SECTION', area):
            out.append(line)
            out.extend(WS_BLOCK)
            i += 1
            continue
        if re.match(r'\s*LINKAGE\s+SECTION', area):
            out.append(line)
            out.append(LINKAGE_COPY)
            i += 1
            continue
        m = re.match(r'(\s*)PROCEDURE\s+DIVISION\s*\.', area)
        if m:
            if not have_linkage:
                out.append('       LINKAGE SECTION.')
                out.append(LINKAGE_COPY)
            using = 'DFHEIBLK DFHCOMMAREA' if have_commarea else 'DFHEIBLK'
            out.append('       PROCEDURE DIVISION USING %s.' % using)
            i += 1
            continue
        if re.search(r'\bEXEC\s+CICS\b', area):
            prefix = area[:area.index('EXEC')]
            body, j = area[area.index('EXEC') + 4:], i
            while 'END-EXEC' not in body:
                j += 1
                nxt = src_lines[j].rstrip('\n')[:72]
                if nxt[6:7] in ('*', '/'):
                    continue
                body += ' ' + nxt[7:]
            before, after = body.split('END-EXEC', 1)
            before = re.sub(r'^\s*CICS\b', '', before)
            stmts = translate_block(' '.join(before.split()))
            if prefix.strip():
                out.append('       ' + prefix.rstrip())
            out.append('      *> EXEC CICS%s END-EXEC' % (' ' + ' '.join(before.split()))[:44])
            gen = emit(stmts)
            if after.strip().startswith('.'):
                gen[-1] += '.'
            out.extend(gen)
            i = j + 1
            continue
        line = re.sub(r'DFHRESP\(\s*([A-Z0-9]+)\s*\)',
                      lambda mm: str(RESP_CODES[mm.group(1)]), line)
        out.append(line)
        i += 1
    return out


def main():
    src, dst = sys.argv[1], sys.argv[2]
    with open(src, encoding='latin-1') as f:
        lines = f.readlines()
    with open(dst, 'w', encoding='latin-1') as f:
        f.write('\n'.join(translate(lines)) + '\n')


if __name__ == '__main__':
    main()
