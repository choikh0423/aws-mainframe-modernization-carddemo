"""KSDS / alternate-index file control on SQLite.

Records are stored byte-for-byte as the COBOL program sees them (the
app/data/ASCII extracts), keyed by the primary key bytes.  Alternate index
paths (CXACAIX) are resolved on the fly from the base cluster.
"""
import os
import sqlite3

DATASETS = {
    'ACCTDAT': dict(base='ACCTDAT', keyoff=0, keylen=11, reclen=300),
    'CCXREF': dict(base='CCXREF', keyoff=0, keylen=16, reclen=50),
    'CXACAIX': dict(base='CCXREF', keyoff=25, keylen=11, reclen=50, path=True),
    'TRANSACT': dict(base='TRANSACT', keyoff=0, keylen=16, reclen=350),
    'USRSEC': dict(base='USRSEC', keyoff=0, keylen=8, reclen=80),
}

SEED_FILES = {
    'ACCTDAT': 'acctdata.txt',
    'CCXREF': 'cardxref.txt',
    'TRANSACT': 'dailytran.txt',
}

NORMAL, NOTFND, DUPREC, DUPKEY, INVREQ, ENDFILE, DSIDERR = 0, 13, 14, 15, 16, 20, 12


class VsamError(Exception):
    def __init__(self, resp, resp2=0):
        super().__init__(resp)
        self.resp, self.resp2 = resp, resp2


class Vsam:
    def __init__(self, db_path):
        self.db_path = db_path
        self.conn = sqlite3.connect(db_path, isolation_level=None, check_same_thread=False)
        self.conn.execute('CREATE TABLE IF NOT EXISTS records '
                          '(ds TEXT, key BLOB, rec BLOB, PRIMARY KEY (ds, key))')

    # ---- seeding -------------------------------------------------------------
    def reseed(self, seed_dir, users=None):
        c = self.conn
        c.execute('BEGIN')
        c.execute('DELETE FROM records')
        for ds, fname in SEED_FILES.items():
            d = DATASETS[ds]
            with open(os.path.join(seed_dir, fname), 'rb') as f:
                for line in f.read().splitlines():
                    if not line.strip():
                        continue
                    rec = line.ljust(d['reclen'], b' ')[:d['reclen']]
                    key = rec[d['keyoff']:d['keyoff'] + d['keylen']]
                    c.execute('INSERT OR REPLACE INTO records VALUES (?,?,?)', (ds, key, rec))
        for rec in users or []:
            c.execute('INSERT OR REPLACE INTO records VALUES (?,?,?)', ('USRSEC', rec[:8], rec))
        c.execute('COMMIT')

    # ---- unit of work ---------------------------------------------------------
    def begin(self):
        self.conn.execute('BEGIN')

    def commit(self):
        if self.conn.in_transaction:
            self.conn.execute('COMMIT')

    def rollback(self):
        if self.conn.in_transaction:
            self.conn.execute('ROLLBACK')

    # ---- helpers --------------------------------------------------------------
    @staticmethod
    def spec(ds):
        d = DATASETS.get(ds.strip())
        if not d:
            raise VsamError(DSIDERR)
        return d

    def _all(self, base):
        return self.conn.execute('SELECT key, rec FROM records WHERE ds=? ORDER BY key',
                                 (base,)).fetchall()

    def _ordered(self, d):
        rows = self._all(d['base'])
        if d.get('path'):
            off, ln = d['keyoff'], d['keylen']
            return sorted(((bytes(r[off:off + ln]), bytes(r)) for _, r in rows),
                          key=lambda kr: kr[0])
        return [(bytes(k), bytes(r)) for k, r in rows]

    # ---- file control ---------------------------------------------------------
    def read(self, ds, key, gteq=False):
        d = self.spec(ds)
        key = bytes(key[:d['keylen']])
        rows = self._ordered(d)
        hits = [r for k, r in rows if (k >= key if gteq else k == key)]
        if not hits:
            raise VsamError(NOTFND, 80)
        if gteq:
            first_key = [k for k, r in rows if k >= key][0]
            hits = [r for k, r in rows if k == first_key]
        return hits[0], (DUPKEY if d.get('path') and len(hits) > 1 else NORMAL)

    def rewrite(self, ds, rec):
        d = self.spec(ds)
        if d.get('path'):
            raise VsamError(INVREQ)
        rec = bytes(rec).ljust(d['reclen'], b' ')[:d['reclen']]
        key = rec[d['keyoff']:d['keyoff'] + d['keylen']]
        cur = self.conn.execute('UPDATE records SET rec=? WHERE ds=? AND key=?',
                                (rec, d['base'], key))
        if cur.rowcount == 0:
            raise VsamError(NOTFND)

    def write(self, ds, key, rec):
        d = self.spec(ds)
        rec = bytes(rec).ljust(d['reclen'], b' ')[:d['reclen']]
        key = bytes(key[:d['keylen']])
        try:
            self.conn.execute('INSERT INTO records VALUES (?,?,?)', (d['base'], key, rec))
        except sqlite3.IntegrityError:
            raise VsamError(DUPREC)

    def browse_keys(self, ds):
        return [k for k, _ in self._ordered(self.spec(ds))]

    def get(self, ds, key):
        row = self.conn.execute('SELECT rec FROM records WHERE ds=? AND key=?',
                                (DATASETS[ds]['base'], key)).fetchone()
        return bytes(row[0]) if row else None

    def last(self, ds, n):
        rows = self.conn.execute('SELECT rec FROM records WHERE ds=? ORDER BY key DESC LIMIT ?',
                                 (DATASETS[ds]['base'], n)).fetchall()
        return [bytes(r[0]) for r in rows]
