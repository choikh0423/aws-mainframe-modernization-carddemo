"""Replays the same keystrokes on the legacy region and the Java service and
diffs what each one puts on the screen and on file."""
import json
import re
import urllib.request

SCENARIOS = [
    dict(id='empty-id', name='ENTER with no account ID', steps=[dict(aid='ENTER', acctId='')]),
    dict(id='unknown-id', name='Unknown account 99999999999', steps=[dict(aid='ENTER', acctId='99999999999')]),
    dict(id='lookup', name='Look up account 00000000001 (blank confirm)',
         steps=[dict(aid='ENTER', acctId='00000000001')]),
    dict(id='invalid-confirm', name='Look up, then confirm X',
         steps=[dict(aid='ENTER', acctId='00000000001'), dict(aid='ENTER', confirm='X')]),
    dict(id='cancel', name='Look up, then confirm N',
         steps=[dict(aid='ENTER', acctId='00000000002'), dict(aid='ENTER', confirm='N')]),
    dict(id='pay', name='Look up, confirm Y: pay in full', data=['00000000001'],
         steps=[dict(aid='ENTER', acctId='00000000001'), dict(aid='ENTER', confirm='Y')]),
    dict(id='pay-lowercase-one-step', name='Account + y in one ENTER', data=['00000000003'],
         steps=[dict(aid='ENTER', acctId='00000000003', confirm='y')]),
    dict(id='nothing-to-pay', name='Pay, then look up again', data=['00000000004'],
         steps=[dict(aid='ENTER', acctId='00000000004', confirm='Y'),
                dict(aid='ENTER', acctId='00000000004', confirm='')]),
    dict(id='pf4', name='F4 clears the screen',
         steps=[dict(aid='ENTER', acctId='00000000001'), dict(aid='PF4')]),
    dict(id='pf3', name='F3 returns to the menu (XCTL COMEN01C)', steps=[dict(aid='PF3')]),
    dict(id='pf5', name='Unassigned key F5', steps=[dict(aid='PF5')]),
    dict(id='short-id', name='Account ID "1" (not zero-padded)', known='gnucobol-move',
         steps=[dict(aid='ENTER', acctId='1')]),
]

KNOWN = {
    'gnucobol-move': 'GnuCOBOL turns MOVE "1          " TO ACCT-ID PIC 9(11) into 00000000001; '
                     'IBM Enterprise COBOL keeps the bytes, so z/OS gets NOTFND. The Java side '
                     'follows z/OS.',
}

OVERPUNCH = {c: (i, 1) for i, c in enumerate('{ABCDEFGHI')}
OVERPUNCH.update({c: (i, -1) for i, c in enumerate('}JKLMNOPQR')})


def zoned_balance(field):
    d, sign = OVERPUNCH[field[-1]] if field[-1] in OVERPUNCH else (int(field[-1]), 1)
    cents = int(field[:-1] + str(d)) * sign
    return '%s%013.2f' % ('-' if cents < 0 else '+', abs(cents) / 100)


def legacy_account(region, acct):
    rec = region.vsam.get('ACCTDAT', acct.encode())
    if not rec:
        return None
    r = rec.decode('latin-1')
    return dict(acctId=acct, currentBalance=zoned_balance(r[12:24]), record=r)


def legacy_latest(region, n):
    return [dict(tranId=r[:16].decode(), record=r.decode('latin-1')) for r in region.vsam.last('TRANSACT', n)]


class Java:
    def __init__(self, base):
        self.base = base.rstrip('/')

    def call(self, method, path, body=None):
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(self.base + path, data=data, method=method,
                                     headers={'Content-Type': 'application/json'})
        with urllib.request.urlopen(req, timeout=15) as r:
            return json.loads(r.read())


def legacy_view(state):
    scr = state['screen']
    f = {x['name']: x for x in scr['fields'] if x['name']}
    if scr.get('map') != 'COBIL0A':
        return dict(program=state['last'].get('program'))
    cur = scr['cursor']
    cursor = next((n for n, x in f.items() if x['row'] == cur['row'] and x['col'] + 1 == cur['col']), None)
    return dict(program='COBIL00C', acctId=f['ACTIDIN']['value'].rstrip(),
                currentBalance=f['CURBAL']['value'].rstrip(), confirm=f['CONFIRM']['value'].rstrip(),
                message=f['ERRMSG']['value'].rstrip(), messageColor=f['ERRMSG']['color'], cursor=cursor)


def java_view(screen):
    if screen.get('nextProgram'):
        return dict(program=screen['nextProgram'])
    return dict(program='COBIL00C', acctId=screen['acctId'], currentBalance=screen['currentBalance'],
                confirm=screen['confirm'], message=screen['message'],
                messageColor=screen['messageColor'], cursor=screen['cursor'])


def diff(a, b):
    keys = list(dict.fromkeys(list(a) + list(b)))
    return [dict(field=k, legacy=a.get(k), java=b.get(k), match=a.get(k) == b.get(k)) for k in keys]


TS = re.compile(r'^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{6}$')


def record_diff(lrec, jrec):
    """Byte compare of TRAN-RECORD; the two timestamps (278-330) only need the same format."""
    if lrec is None or jrec is None:
        return dict(match=lrec == jrec, legacy=lrec, java=jrec)
    same = lrec[:278] == jrec[:278] and lrec[330:] == jrec[330:]
    ts_ok = all(TS.match(r[o:o + 26]) for r in (lrec, jrec) for o in (278, 304))
    return dict(match=same and ts_ok, legacy=lrec, java=jrec)


def run(region, java_url, scenarios=None):
    java = Java(java_url)
    report = []
    for sc in scenarios or SCENARIOS:
        region.reseed()
        java.call('POST', '/api/demo/reset')
        state = region.start_from_menu('PRTY')
        jscreen = java.call('GET', '/api/bill-payment')
        steps = []
        for step in sc['steps']:
            fields = {}
            if 'acctId' in step:
                fields['ACTIDIN'] = step['acctId']
            if 'confirm' in step:
                fields['CONFIRM'] = step['confirm']
            state = region.enter('PRTY', step['aid'], fields)
            jreq = dict(aid=step['aid'], acctId=step.get('acctId', jscreen.get('acctId', '')),
                        confirm=step.get('confirm', jscreen.get('confirm', '')),
                        currentBalance=jscreen.get('currentBalance', ''), fromProgram='COMEN01C')
            jscreen = java.call('POST', '/api/bill-payment', jreq)
            d = diff(legacy_view(state), java_view(jscreen))
            steps.append(dict(step=step, fields=d, match=all(x['match'] for x in d)))
        data = []
        for acct in sc.get('data', []):
            la = legacy_account(region, acct)
            ja = java.call('GET', '/api/demo/accounts/' + acct)
            data.append(dict(what='ACCTDAT %s balance' % acct, legacy=la['currentBalance'],
                             java=ja['currentBalance'], match=la['currentBalance'] == ja['currentBalance']))
            data.append(dict(what='ACCTDAT %s record' % acct, legacy=la['record'], java=ja['record'],
                             match=la['record'] == ja['record']))
        if sc.get('data'):
            lt = legacy_latest(region, 1)[0]
            jt = java.call('GET', '/api/demo/transactions/latest?limit=1')[0]
            rd = record_diff(lt['record'], jt['record'])
            data.append(dict(what='TRANSACT newest record (%s / %s)' % (lt['tranId'], jt['tranId']), **rd))
        match = all(s['match'] for s in steps) and all(x['match'] for x in data)
        status = 'PASS' if match else ('KNOWN' if sc.get('known') else 'FAIL')
        report.append(dict(id=sc['id'], name=sc['name'], status=status, steps=steps, data=data,
                           note=KNOWN.get(sc.get('known'))))
    region.reseed()
    java.call('POST', '/api/demo/reset')
    return report
