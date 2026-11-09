"""Runs the real, translated COBIL00C/COMEN01C in the region (needs ./build.sh first)."""
import os
import tempfile
import unittest

from region.bms import parse_bms
from region.cics import Region
from region import parity

RUNTIME = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REPO = os.path.abspath(os.path.join(RUNTIME, '..', '..', '..'))
BMS = os.path.join(REPO, 'app', 'bms')
SEED = os.path.join(REPO, 'app', 'data', 'ASCII')


def fields(state):
    return {f['name']: f for f in state['screen']['fields'] if f['name']}


class BmsTest(unittest.TestCase):
    def test_cobil00_layout_matches_symbolic_map(self):
        m = parse_bms(os.path.join(BMS, 'COBIL00.bms'))['COBIL0A']
        self.assertEqual((m.rows, m.cols, m.io_len), (24, 80, 294))
        offsets = {f.name: f.offset for f in m.fields if f.name}
        self.assertEqual(offsets['ACTIDIN'], 162)
        self.assertEqual(offsets['CONFIRM'], 201)
        self.assertEqual(offsets['ERRMSG'], 209)


class RegionTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.region = Region(os.path.join(RUNTIME, 'build'), BMS, SEED, os.path.join(self.tmp.name, 'v.db'))
        self.region.reseed()
        self.first = self.region.start_from_menu('TST1')

    def tearDown(self):
        self.tmp.cleanup()

    def enter(self, aid='ENTER', **f):
        return self.region.enter('TST1', aid, f)

    def test_first_screen_is_the_real_map(self):
        rows = self.first['screen']['rows']
        self.assertEqual(self.first['screen']['map'], 'COBIL0A')
        self.assertIn('Bill Payment', rows[3])
        self.assertTrue(rows[0].strip().startswith('Tran: CB00'))
        self.assertEqual(rows[23].strip(), 'ENTER=Continue  F3=Back  F4=Clear')
        self.assertEqual(self.first['nextTransid'], 'CB00')

    def test_empty_and_unknown_account(self):
        self.assertEqual(fields(self.enter(ACTIDIN=''))['ERRMSG']['value'].rstrip(), 'Acct ID can NOT be empty...')
        self.assertEqual(fields(self.enter(ACTIDIN='99999999999'))['ERRMSG']['value'].rstrip(),
                         'Account ID NOT found...')

    def test_pay_in_full_writes_transaction_and_zeroes_balance(self):
        f = fields(self.enter(ACTIDIN='00000000001'))
        self.assertEqual(f['CURBAL']['value'].rstrip(), '+0000000194.00')
        st = self.enter(CONFIRM='Y')
        self.assertEqual(fields(st)['ERRMSG']['value'].rstrip(),
                         'Payment successful.  Your Transaction ID is 0000000996722788.')
        self.assertEqual(parity.legacy_account(self.region, '00000000001')['currentBalance'], '+0000000000.00')
        rec = parity.legacy_latest(self.region, 1)[0]['record']
        self.assertTrue(rec.startswith('0000000996722788020002POS TERM  BILL PAYMENT - ONLINE'))
        cmds = [(t['cmd'], t['target']) for t in st['trace']]
        for expected in [('READ', 'ACCTDAT'), ('READ', 'CXACAIX'), ('STARTBR', 'TRANSACT'),
                         ('READPREV', 'TRANSACT'), ('WRITE', 'TRANSACT'), ('REWRITE', 'ACCTDAT')]:
            self.assertIn(expected, cmds)

    def test_pf3_transfers_to_real_main_menu(self):
        st = self.enter('PF3')
        self.assertEqual(st['screen']['map'], 'COMEN1A')
        self.assertTrue(any('10. Bill Payment' in r for r in st['screen']['rows']))

    def test_invalid_key(self):
        self.assertEqual(fields(self.enter('PF5'))['ERRMSG']['value'].rstrip(),
                         'Invalid key pressed. Please see below...')


@unittest.skipUnless(os.environ.get('JAVA_URL'), 'set JAVA_URL to run legacy-vs-Java parity')
class ParityTest(unittest.TestCase):
    def test_all_scenarios_match(self):
        with tempfile.TemporaryDirectory() as tmp:
            region = Region(os.path.join(RUNTIME, 'build'), BMS, SEED, os.path.join(tmp, 'v.db'))
            report = parity.run(region, os.environ['JAVA_URL'])
        failed = [(r['id'], [f for s in r['steps'] for f in s['fields'] if not f['match']],
                   [d['what'] for d in r['data'] if not d['match']]) for r in report if r['status'] == 'FAIL']
        self.assertEqual(failed, [])
        self.assertGreaterEqual(sum(r['status'] == 'PASS' for r in report), 11)


if __name__ == '__main__':
    unittest.main()
