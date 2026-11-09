"""A tiny single-region CICS: terminals, pseudo-conversational tasks, BMS,
file control and the EXEC interface used by the translated programs.

Each task is a separate `cicstask` process running the real compiled COBOL
program.  Every EXEC CICS command arrives here as one request; the reply
carries the updated EIB and data areas.  A task is one unit of work: file
updates commit when the task ends normally and are backed out on abend.
"""
import datetime as dt
import os
import struct
import subprocess
import threading

from .bms import parse_bms
from .vsam import Vsam, VsamError

EIB_LEN = 85
ARGS_LEN = 114

RESP_NAMES = {0: 'NORMAL', 12: 'FILENOTFOUND', 13: 'NOTFND', 14: 'DUPREC', 15: 'DUPKEY',
              16: 'INVREQ', 20: 'ENDFILE', 22: 'LENGERR', 27: 'PGMIDERR', 36: 'MAPFAIL'}
ABEND_CODES = {13: 'AEIM', 14: 'AEIN', 15: 'AEIO', 16: 'AEIP', 12: 'AEIL',
               20: 'AEIK', 22: 'AEIV', 27: 'AEI0', 36: 'AEI9'}

# AID keys -> EIBAID byte (EBCDIC code point displayed as its cp037 character).
AID_EBCDIC = {'ENTER': 0x7D, 'CLEAR': 0x6D, 'PA1': 0x6C, 'PA2': 0x6E, 'PA3': 0x6B}
for _i, _b in enumerate([0xF1, 0xF2, 0xF3, 0xF4, 0xF5, 0xF6, 0xF7, 0xF8, 0xF9, 0x7A, 0x7B, 0x7C,
                         0xC1, 0xC2, 0xC3, 0xC4, 0xC5, 0xC6, 0xC7, 0xC8, 0xC9, 0x4A, 0x4B, 0x4C]):
    AID_EBCDIC['PF%d' % (_i + 1)] = _b

COLOR_CODES = {0xF1: 'BLUE', 0xF2: 'RED', 0xF3: 'PINK', 0xF4: 'GREEN', 0xF5: 'TURQUOISE',
               0xF6: 'YELLOW', 0xF7: 'NEUTRAL'}
HILIGHT_CODES = {0xF1: 'BLINK', 0xF2: 'REVERSE', 0xF4: 'UNDERLINE'}

PROGRAM_TRANSIDS = {'COBIL00C': 'CB00', 'COMEN01C': 'CM00', 'COSGN00C': 'CC00'}
TRANSID_PROGRAMS = {v: k for k, v in PROGRAM_TRANSIDS.items()}
MAPSETS = {'COBIL00', 'COMEN01', 'COSGN00'}


def to_ebcdic(b):
    """Byte as the COBOL program holds it (ASCII of the cp037 glyph) -> EBCDIC code point."""
    return bytes([b]).decode('latin-1').encode('cp037', errors='replace')[0]


def from_ebcdic(e):
    return bytes([e]).decode('cp037').encode('latin-1', errors='replace')


def pack(value, nbytes):
    digits = str(abs(value)).rjust(nbytes * 2 - 1, '0')
    nib = digits + ('D' if value < 0 else 'C')
    return bytes.fromhex(nib)


def unpack(b):
    h = b.hex()
    v = int(h[:-1] or '0')
    return -v if h[-1] in 'bd' else v


class ScreenField:
    def __init__(self, mf, initial=True):
        self.name, self.row, self.col, self.length = mf.name, mf.row, mf.col, mf.length
        a = set(mf.attrb)
        self.prot = 'ASKIP' in a or 'PROT' in a
        self.askip = 'ASKIP' in a
        self.num = 'NUM' in a or 'ASKIP' in a
        self.intensity = 'BRT' if 'BRT' in a else ('DRK' if 'DRK' in a else 'NORM')
        self.mdt = 'FSET' in a
        self.ic = 'IC' in a
        self.color = mf.color
        self.hilight = mf.hilight
        self.data = list(mf.initial.ljust(self.length, '\0')) if initial and mf.initial \
            else ['\0'] * self.length

    def set_attribute(self, ebc):
        self.prot = bool(ebc & 0x20)
        self.num = bool(ebc & 0x10)
        self.askip = self.prot and self.num
        self.intensity = {0x00: 'NORM', 0x04: 'NORM', 0x08: 'BRT', 0x0C: 'DRK'}[ebc & 0x0C]
        self.mdt = bool(ebc & 0x01)

    @property
    def value(self):
        return ''.join(self.data)

    def to_json(self):
        color = self.color or {
            (False, False): 'GREEN', (False, True): 'RED',
            (True, False): 'BLUE', (True, True): 'NEUTRAL'}[(self.prot, self.intensity == 'BRT')]
        return dict(name=self.name, row=self.row, col=self.col, length=self.length,
                    protected=self.prot, numeric=self.num and not self.prot,
                    intensity=self.intensity, color=color, hilight=self.hilight,
                    mdt=self.mdt, value=self.value.replace('\0', ' '))


class Screen:
    def __init__(self, bms_map=None):
        self.map = bms_map
        self.fields = []
        self.text = None
        self.cursor = (1, 1)

    def rows(self):
        grid = [[' '] * 80 for _ in range(24)]
        if self.text is not None:
            for i in range(0, min(len(self.text), 1920)):
                ch = self.text[i]
                grid[i // 80][i % 80] = ch if ch.isprintable() else ' '
        for f in self.fields:
            start = (f.row - 1) * 80 + f.col  # data follows the attribute byte
            for i, ch in enumerate(f.data):
                p = start + i
                if p < 1920:
                    grid[p // 80][p % 80] = ' ' if (ch == '\0' or f.intensity == 'DRK') else ch
        return [''.join(r) for r in grid]

    def field(self, name):
        for f in self.fields:
            if f.name == name:
                return f
        return None

    def to_json(self):
        return dict(map=self.map.name if self.map else None,
                    mapset=self.map.mapset if self.map else None,
                    rows=self.rows(), fields=[f.to_json() for f in self.fields],
                    cursor=dict(row=self.cursor[0], col=self.cursor[1]))


class Terminal:
    def __init__(self, termid):
        self.termid = termid
        self.screen = Screen()
        self.next_transid = None
        self.commarea = b''
        self.aid = 'ENTER'
        self.trace = []
        self.last = {}


class Abend(Exception):
    def __init__(self, code, detail=''):
        super().__init__(code)
        self.code, self.detail = code, detail


class Region:
    def __init__(self, build_dir, bms_dir, seed_dir, db_path, applid='CICSDEMO'):
        self.build_dir, self.seed_dir, self.applid = build_dir, seed_dir, applid
        self.maps = {}
        for ms in MAPSETS:
            p = os.path.join(bms_dir, ms + '.bms')
            if os.path.exists(p):
                self.maps.update(parse_bms(p))
        self.vsam = Vsam(db_path)
        self.terminals = {}
        self.lock = threading.RLock()
        self.taskn = 0

    # ---- public API ------------------------------------------------------------
    def terminal(self, termid):
        with self.lock:
            if termid not in self.terminals:
                self.terminals[termid] = Terminal(termid)
            return self.terminals[termid]

    def reseed(self):
        with self.lock:
            self.vsam.reseed(self.seed_dir)

    def installed(self, program):
        return os.path.exists(os.path.join(self.build_dir, program.strip() + '.so'))

    def start_from_menu(self, termid, program='COBIL00C'):
        """Enter a program the way COMEN01C does: XCTL with the CardDemo COMMAREA."""
        ca = (b'CM00' + b'COMEN01C' + b' ' * 4 + b' ' * 8 + b'USER0001' + b'U' + b'0'
              + b'0' * 9 + b' ' * 75 + b'0' * 11 + b' ' + b'0' * 16 + b' ' * 14)
        with self.lock:
            term = self.terminal(termid)
            term.trace = []
            term.screen = Screen()
            self._run(term, program, ca, 'ENTER')
            return self.state(term)

    def enter(self, termid, aid, fields=None, cursor=None):
        """A 3270 AID key from the terminal, with the values typed into the screen."""
        with self.lock:
            term = self.terminal(termid)
            term.trace = []
            aid = aid.upper()
            if aid not in AID_EBCDIC:
                raise ValueError('unknown AID ' + aid)
            for name, value in (fields or {}).items():
                f = term.screen.field(name)
                if f is None or f.prot:
                    continue
                value = (value or '')[:f.length]
                current = f.value.replace('\0', ' ').rstrip()
                if value.rstrip() != current or (value == '' and f.value.strip('\0') != ''):
                    f.data = list(value.ljust(f.length, '\0'))
                    f.mdt = True
            if cursor and term.screen.field(cursor):
                f = term.screen.field(cursor)
                term.screen.cursor = (f.row, f.col + 1)
            if aid == 'CLEAR':
                term.screen = Screen()
            transid = term.next_transid
            if not transid:
                self._log(term, None, 'TERMINAL', '', 'no transaction',
                          'pseudo-conversation ended; use Start CB00')
                return self.state(term)
            program = TRANSID_PROGRAMS.get(transid)
            if not program:
                self._log(term, None, 'ATTACH', transid, 'TRANSIDERR', '')
                return self.state(term)
            self._run(term, program, term.commarea, aid)
            return self.state(term)

    def state(self, term):
        return dict(termid=term.termid, screen=term.screen.to_json(),
                    nextTransid=term.next_transid, trace=term.trace, last=term.last)

    # ---- task management ---------------------------------------------------------
    def _log(self, term, program, cmd, target, resp, detail=''):
        term.trace.append(dict(seq=len(term.trace) + 1, program=program, cmd=cmd,
                               target=target, resp=resp, detail=detail))

    def _eib(self, term, transid, calen, aid):
        now = dt.datetime.now()
        self.taskn += 1
        eib = bytearray(b'\0' * EIB_LEN)
        eib[0:4] = pack(int(now.strftime('%H%M%S')), 4)
        eib[4:8] = pack((now.year - 1900) * 1000 + now.timetuple().tm_yday, 4)
        eib[8:12] = transid.ljust(4).encode()
        eib[12:16] = pack(self.taskn, 4)
        eib[16:20] = term.termid[:4].ljust(4).encode()
        eib[24:26] = struct.pack('>h', calen)
        eib[26:27] = from_ebcdic(AID_EBCDIC[aid])
        return eib

    def _run(self, term, program, commarea, aid):
        term.aid = aid
        term.next_transid = None
        uow_state = {'update': {}, 'browse': {}}
        self.vsam.begin()
        try:
            while program:
                transid = PROGRAM_TRANSIDS.get(program, 'XXXX')
                self._log(term, program, 'ATTACH' if not term.trace else 'START',
                          program, 'NORMAL', 'TRANSID %s, EIBCALEN %d, EIBAID %s'
                          % (transid, len(commarea), aid))
                program, commarea = self._run_program(term, program, transid, commarea,
                                                      aid, uow_state)
            self.vsam.commit()
            self._log(term, None, 'SYNCPOINT', '', 'NORMAL', 'unit of work committed')
        except Abend as a:
            self.vsam.rollback()
            term.next_transid = None
            now = dt.datetime.now().strftime('%H:%M:%S')
            msg = ('DFHAC2206 %s %s Transaction %s failed with abend %s. Updates to local '
                   'recoverable resources backed out.' % (now, self.applid,
                                                          term.last.get('transid', '????'), a.code))
            term.screen = Screen()
            term.screen.text = msg
            term.last['abend'] = a.code
            self._log(term, None, 'ABEND', a.code, a.code, a.detail)

    def _run_program(self, term, program, transid, commarea, aid, uow):
        """Run one program in a cicstask process. Returns (xctl_program, commarea) or (None, _)."""
        term.last = dict(program=program, transid=transid)
        if not self.installed(program):
            raise Abend('AEI0', 'PGMIDERR: program %s is not installed in this region' % program)
        req_r, req_w = os.pipe()
        rep_r, rep_w = os.pipe()

        def child_fds():
            os.dup2(req_w, 200)
            os.dup2(rep_r, 201)
            os.dup2(200, 3)
            os.dup2(201, 4)

        env = dict(os.environ, COB_LIBRARY_PATH=self.build_dir)
        proc = subprocess.Popen([os.path.join(self.build_dir, 'cicstask'), program],
                                pass_fds=(3, 4), preexec_fn=child_fds, env=env,
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        os.close(req_w)
        os.close(rep_r)
        rin = os.fdopen(req_r, 'rb', buffering=0)
        rout = os.fdopen(rep_w, 'wb', buffering=0)
        eib = self._eib(term, transid, len(commarea), aid)
        result = (None, b'')
        try:
            rout.write(bytes(eib) + struct.pack('<I', len(commarea)) + commarea)
            while True:
                tag = self._readn(rin, 4)
                if tag is None:
                    out = proc.stdout.read().decode('latin-1', 'replace')
                    raise Abend('ASRA', 'program check in %s: %s' % (program, out.strip()[-300:]))
                if tag == b'GOBK':
                    self._log(term, program, 'GOBACK', '', 'NORMAL', 'treated as EXEC CICS RETURN')
                    break
                if tag == b'NPGM':
                    raise Abend('AEI0', 'PGMIDERR ' + program)
                args = self._readn(rin, ARGS_LEN)
                eib = bytearray(self._readn(rin, EIB_LEN))
                areas = []
                for _ in range(3):
                    n = struct.unpack('<I', self._readn(rin, 4))[0]
                    areas.append(bytearray(self._readn(rin, n)) if n else None)
                req = self._decode_args(args)
                resp, resp2, end, xfer = self._dispatch(term, program, req, areas, uow)
                if resp and 'RESP' not in req['flags']:
                    raise Abend(ABEND_CODES.get(resp, 'AEIx'),
                                '%s %s: unhandled %s' % (req['cmd'], req['p1'],
                                                        RESP_NAMES.get(resp, resp)))
                eib[76:80] = struct.pack('>i', resp)
                eib[80:84] = struct.pack('>i', resp2)
                eib[35:43] = req['p1'].ljust(8).encode()[:8]
                reply = bytearray(eib)
                for a in areas:
                    reply += struct.pack('<I', len(a) if a else 0) + (bytes(a) if a else b'')
                reply += b'\1' if end else b'\0'
                rout.write(bytes(reply))
                if end:
                    result = xfer
                    break
        finally:
            rout.close()
            try:
                proc.wait(timeout=10)
            except subprocess.TimeoutExpired:
                proc.kill()
            rin.close()
        return result

    @staticmethod
    def _readn(f, n):
        buf = b''
        while len(buf) < n:
            chunk = f.read(n - len(buf))
            if not chunk:
                return None if not buf else buf
            buf += chunk
        return buf

    @staticmethod
    def _decode_args(a):
        s = lambda b: b.decode('latin-1')
        return dict(cmd=s(a[0:16]).strip(), p1=s(a[16:24]).strip(), p2=s(a[24:32]).strip(),
                    flags=s(a[32:96]).split(), dsep=s(a[96:97]), tsep=s(a[97:98]),
                    keylen=struct.unpack('<i', a[98:102])[0])

    # ---- EXEC interface ----------------------------------------------------------
    def _dispatch(self, term, program, req, areas, uow):
        cmd, p1 = req['cmd'], req['p1']
        handler = getattr(self, '_cmd_' + cmd.replace(' ', '_'), None)
        if handler is None:
            raise Abend('AEY9', 'EXEC CICS %s is not supported by this region' % cmd)
        try:
            resp, resp2, end, xfer, detail = handler(term, program, req, areas, uow)
        except VsamError as e:
            resp, resp2, end, xfer, detail = e.resp, e.resp2, False, None, ''
        self._log(term, program, cmd, p1 + ('/' + req['p2'] if req['p2'] else ''),
                  RESP_NAMES.get(resp, str(resp)), detail)
        return resp, resp2, end, xfer

    def _cmd_ASKTIME(self, term, program, req, areas, uow):
        now = dt.datetime.now()
        ms = int((now - dt.datetime(1900, 1, 1)).total_seconds() * 1000)
        areas[0][:] = pack(ms, len(areas[0]))
        return 0, 0, False, None, ''

    def _cmd_FORMATTIME(self, term, program, req, areas, uow):
        ms = unpack(bytes(areas[0]))
        t = dt.datetime(1900, 1, 1) + dt.timedelta(milliseconds=ms)
        ds = req['dsep'].strip()
        ts = req['tsep'].strip()
        flags = req['flags']
        if areas[1] is not None:
            if 'MMDDYY' in flags:
                parts = (t.strftime('%m'), t.strftime('%d'), t.strftime('%y'))
            elif 'DDMMYY' in flags:
                parts = (t.strftime('%d'), t.strftime('%m'), t.strftime('%y'))
            else:
                parts = (t.strftime('%Y'), t.strftime('%m'), t.strftime('%d'))
            v = ds.join(parts).encode()
            areas[1][:] = v.ljust(len(areas[1]))[:len(areas[1])]
        if areas[2] is not None:
            v = ts.join((t.strftime('%H'), t.strftime('%M'), t.strftime('%S'))).encode()
            areas[2][:] = v.ljust(len(areas[2]))[:len(areas[2])]
        return 0, 0, False, None, t.strftime('%Y-%m-%d %H:%M:%S')

    def _map(self, req):
        m = self.maps.get(req['p1'])
        if not m or (req['p2'] and m.mapset != req['p2']):
            raise Abend('AEI1', 'map %s/%s not found' % (req['p1'], req['p2']))
        return m

    def _cmd_SEND_MAP(self, term, program, req, areas, uow):
        m = self._map(req)
        flags = req['flags']
        data = bytes(areas[0]) if areas[0] is not None and 'MAPONLY' not in flags else None
        dataonly = 'DATAONLY' in flags
        scr = term.screen
        if 'ERASE' in flags or scr.map is not m:
            scr = Screen(m)
            scr.fields = [ScreenField(mf, initial=not dataonly) for mf in m.fields]
            term.screen = scr
        cursor = None
        for mf, sf in zip(m.fields, scr.fields):
            if not mf.name or data is None:
                continue
            o = mf.offset
            if struct.unpack('>h', data[o:o + 2])[0] == -1 and cursor is None:
                cursor = sf
            attr = data[o + 2]
            # X'00' keeps the map attribute; X'80' is the RECEIVE MAP "field erased"
            # flag left in the shared I/O area, not an attribute the program set.
            if attr != 0x00 and to_ebcdic(attr) != 0x80:
                sf.set_attribute(to_ebcdic(attr))
            if m.ext:
                c, h = data[o + 3], data[o + 5]
                if c:
                    sf.color = COLOR_CODES.get(to_ebcdic(c), sf.color)
                if h:
                    sf.hilight = HILIGHT_CODES.get(to_ebcdic(h), sf.hilight)
            d = data[o + 3 + m.ext:o + 3 + m.ext + mf.length]
            if d and d[0] != 0:
                sf.data = list(d.decode('latin-1'))
        if cursor is None:
            cursor = next((f for f in scr.fields if f.ic), None)
        if cursor is not None:
            scr.cursor = (cursor.row, cursor.col + 1)
        msg = scr.field('ERRMSG')
        return 0, 0, False, None, ('ERRMSG=' + msg.value.replace('\0', '').strip()) if msg and msg.value.strip('\0 ') else ''

    def _cmd_RECEIVE_MAP(self, term, program, req, areas, uow):
        m = self._map(req)
        scr = term.screen
        if term.aid in ('CLEAR', 'PA1', 'PA2', 'PA3') or scr.map is not m:
            return 36, 0, False, None, 'no data from terminal (AID %s)' % term.aid
        buf = bytearray(b'\0' * m.io_len)
        sent = []
        for mf, sf in zip(m.fields, scr.fields):
            if not mf.name or not sf.mdt:
                continue
            v = sf.value.replace('\0', '')
            o = mf.offset
            buf[o:o + 2] = struct.pack('>h', len(v))
            buf[o + 2] = from_ebcdic(0x80)[0] if not v else 0x00  # DFHBMEOF
            if v:
                buf[o + 3 + m.ext:o + 3 + m.ext + mf.length] = v.ljust(mf.length).encode('latin-1')
            sent.append('%s=%r' % (mf.name, v))
        if not sent:
            return 36, 0, False, None, 'no modified fields'
        n = len(areas[0])
        areas[0][:] = bytes(buf).ljust(n, b'\0')[:n]
        user = [s for s in sent if s.split('=')[0] in ('ACTIDIN', 'CONFIRM') or
                not scr.field(s.split('=')[0]).prot]
        return 0, 0, False, None, ', '.join(user)

    def _cmd_SEND_TEXT(self, term, program, req, areas, uow):
        term.screen = Screen()
        term.screen.text = bytes(areas[0]).decode('latin-1')
        return 0, 0, False, None, ''

    def _key(self, req, area):
        ln = req['keylen'] or self.vsam.spec(req['p1'])['keylen']
        return bytes(area[:ln])

    def _cmd_READ(self, term, program, req, areas, uow):
        key = self._key(req, areas[1])
        rec, resp = self.vsam.read(req['p1'], key, gteq='GTEQ' in req['flags'])
        n = len(areas[0])
        areas[0][:] = rec.ljust(n, b' ')[:n]
        if 'UPDATE' in req['flags']:
            uow['update'][req['p1']] = key
        return resp, 0, False, None, 'key=%s%s' % (key.decode('latin-1'),
                                                  ' UPDATE' if 'UPDATE' in req['flags'] else '')

    def _cmd_REWRITE(self, term, program, req, areas, uow):
        if req['p1'] not in uow['update']:
            return 16, 0, False, None, 'no prior READ UPDATE'
        self.vsam.rewrite(req['p1'], areas[0])
        del uow['update'][req['p1']]
        return 0, 0, False, None, 'record %d bytes' % len(areas[0])

    def _cmd_WRITE(self, term, program, req, areas, uow):
        key = self._key(req, areas[1])
        self.vsam.write(req['p1'], key, areas[0])
        return 0, 0, False, None, 'key=%s' % key.decode('latin-1')

    def _cmd_STARTBR(self, term, program, req, areas, uow):
        key = self._key(req, areas[1])
        keys = self.vsam.browse_keys(req['p1'])
        if key == b'\xff' * len(key):
            pos = len(keys)  # HIGH-VALUES: positioned after the last record
        else:
            exact = 'EQUAL' in req['flags']
            pos = next((i for i, k in enumerate(keys) if (k == key if exact else k >= key)), None)
            if pos is None:
                return 13, 0, False, None, 'key=%s' % key.decode('latin-1', 'replace')
        uow['browse'][req['p1']] = {'keys': keys, 'pos': pos, 'started': True}
        shown = 'HIGH-VALUES' if key == b'\xff' * len(key) else key.decode('latin-1')
        return 0, 0, False, None, 'key=' + shown

    def _browse_read(self, req, areas, uow, step):
        br = uow['browse'].get(req['p1'])
        if not br:
            return 16, 0, False, None, 'no browse active'
        keys = br['keys']
        if step < 0:
            pos = br['pos'] - 1 if br['started'] or br['pos'] >= len(keys) else br['pos'] - 1
        else:
            pos = br['pos'] if br['started'] else br['pos'] + 1
        br['started'] = False
        if pos < 0 or pos >= len(keys):
            return 20, 0, False, None, ''
        br['pos'] = pos
        rec, _ = self.vsam.read(req['p1'], keys[pos])
        n = len(areas[0])
        areas[0][:] = rec.ljust(n, b' ')[:n]
        kl = len(areas[1])
        areas[1][:] = keys[pos].ljust(kl, b' ')[:kl]
        return 0, 0, False, None, 'key=%s' % keys[pos].decode('latin-1')

    def _cmd_READPREV(self, term, program, req, areas, uow):
        return self._browse_read(req, areas, uow, -1)

    def _cmd_READNEXT(self, term, program, req, areas, uow):
        return self._browse_read(req, areas, uow, +1)

    def _cmd_ENDBR(self, term, program, req, areas, uow):
        if uow['browse'].pop(req['p1'], None) is None:
            return 16, 0, False, None, ''
        return 0, 0, False, None, ''

    def _cmd_RETURN(self, term, program, req, areas, uow):
        ca = bytes(areas[0]) if areas[0] is not None else b''
        term.next_transid = req['p1'] or None
        term.commarea = ca
        return 0, 0, True, (None, b''), ('TRANSID %s COMMAREA %d bytes' % (req['p1'], len(ca))
                                         if req['p1'] else 'end of conversation')

    def _cmd_XCTL(self, term, program, req, areas, uow):
        target = req['p1']
        if not self.installed(target):
            return 27, 0, False, None, '%s is not installed in this region' % target
        ca = bytes(areas[0]) if areas[0] is not None else b''
        return 0, 0, True, (target, ca), 'to %s COMMAREA %d bytes' % (target, len(ca))

    def _cmd_INQUIRE(self, term, program, req, areas, uow):
        ok = self.installed(req['p1'])
        return (0 if ok else 27), 0, False, None, ''

    def _cmd_SYNCPOINT(self, term, program, req, areas, uow):
        self.vsam.commit()
        self.vsam.begin()
        return 0, 0, False, None, ''
