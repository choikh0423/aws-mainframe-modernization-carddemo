#!/usr/bin/env python3
"""Independent oracle for S-11 POSTTRAN parity.

Re-implements CBTRN02C's validation and posting straight from app/cbl/CBTRN02C.cbl
against the ASCII unloads in app/data/ASCII, so the expected numbers in
PostTranJobIntegrationTest come from the COBOL rules rather than from a run of the
migrated job. Prints the counts, the reason distribution and the accounts and
category balances the run leaves behind.

    python3 migration/carddemo/tools/posttran_parity_oracle.py
"""
from __future__ import annotations

import json
import pathlib
from decimal import Decimal

DATA = pathlib.Path(__file__).resolve().parents[3] / "app" / "data" / "ASCII"

POSITIVE = "{ABCDEFGHI"
NEGATIVE = "}JKLMNOPQR"


def signed(field: str, scale: int = 2) -> Decimal:
    """Zoned decimal with the sign overpunched on the last digit."""
    digits, last = field[:-1], field[-1]
    if last in POSITIVE:
        value = Decimal(digits + str(POSITIVE.index(last)))
    elif last in NEGATIVE:
        value = -Decimal(digits + str(NEGATIVE.index(last)))
    else:
        value = Decimal(field)
    return value.scaleb(-scale)


def read(name: str, length: int) -> list[str]:
    lines = (DATA / name).read_text().splitlines()
    return [line.ljust(length)[:length] for line in lines]


def main() -> None:
    # CVACT03Y: card X(16), cust 9(09), acct 9(11) - the unload has no FILLER
    xref = {line[0:16]: int(line[25:36]) for line in read("cardxref.txt", 36)}

    # CVACT01Y: id 9(11), status X, curr-bal, credit-limit, cash-limit S9(10)V99,
    # open-date X(10) at 48, expiraion-date X(10) at 58, curr-cyc-credit/debit at 78/90
    accounts = {}
    for line in read("acctdata.txt", 300):
        accounts[int(line[0:11])] = {
            "curr_bal": signed(line[12:24]),
            "credit_limit": signed(line[24:36]),
            "expiration": line[58:68],
            "cyc_credit": signed(line[78:90]),
            "cyc_debit": signed(line[90:102]),
        }

    # CVTRA01Y: acct 9(11), type X(02), cat 9(04), balance S9(09)V99
    balances = {}
    for line in read("tcatbal.txt", 50):
        balances[(int(line[0:11]), line[11:13], int(line[13:17]))] = signed(line[17:28])

    processed = rejected = 0
    reasons: dict[int, int] = {}
    posted = 0
    created_balances = 0

    for line in read("dailytran.txt", 350):
        processed += 1
        card = line[262:278]
        amount = signed(line[132:143])
        type_cd = line[16:18]
        cat_cd = int(line[18:22])
        orig_ts = line[278:304]

        reason = 0
        acct_id = xref.get(card)
        if acct_id is None:
            reason = 100
        else:
            account = accounts.get(acct_id)
            if account is None:
                reason = 101
            else:
                if account["credit_limit"] < account["cyc_credit"] - account["cyc_debit"] + amount:
                    reason = 102
                if account["expiration"] < orig_ts[:10]:
                    reason = 103

        if reason:
            rejected += 1
            reasons[reason] = reasons.get(reason, 0) + 1
            continue

        key = (acct_id, type_cd, cat_cd)
        if key not in balances:
            balances[key] = Decimal("0.00")
            created_balances += 1
        balances[key] += amount

        account["curr_bal"] += amount
        if amount >= 0:
            account["cyc_credit"] += amount
        else:
            account["cyc_debit"] += amount
        posted += 1

    print(json.dumps({
        "processed": processed,
        "rejected": rejected,
        "posted": posted,
        "reasons": {str(k): v for k, v in sorted(reasons.items())},
        "category_balances_total": len(balances),
        "category_balances_created": created_balances,
        "condition_code": 4 if rejected else 0,
        "sample_accounts": {
            str(acct): {
                "curr_bal": str(accounts[acct]["curr_bal"]),
                "cyc_credit": str(accounts[acct]["cyc_credit"]),
                "cyc_debit": str(accounts[acct]["cyc_debit"]),
            }
            for acct in sorted(accounts)[:3]
        },
    }, indent=2))


if __name__ == "__main__":
    main()
