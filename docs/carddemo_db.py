"""
CardDemo SQLite Database Module
================================
Mirrors the VSAM datasets from the mainframe CardDemo application:
  - accounts   (ACCTDAT)
  - cardxref   (CARDXREF / CCXREF)
  - transactions (TRANSACT)
  - daily_rejects (DALYREJS)

Provides CRUD operations and the batch overlimit-check logic that
mirrors CBTRN02C.cbl's 1500-VALIDATE-TRAN / 2000-POST-TRANSACTION /
2500-WRITE-REJECT-REC flow.
"""

import sqlite3
import os
import time
from pathlib import Path

DB_PATH = Path(__file__).parent / "carddemo.db"
DATA_DIR = Path(__file__).parent.parent / "app" / "data" / "ASCII"

SCHEMA_SQL = """
CREATE TABLE IF NOT EXISTS accounts (
    acct_id     TEXT PRIMARY KEY,
    status      TEXT NOT NULL DEFAULT 'Y',
    balance     REAL NOT NULL DEFAULT 0.0,
    credit_limit REAL NOT NULL DEFAULT 0.0,
    cash_credit_limit REAL NOT NULL DEFAULT 0.0,
    open_date   TEXT NOT NULL DEFAULT '',
    expires     TEXT NOT NULL DEFAULT '2027-12-31',
    reissue_date TEXT NOT NULL DEFAULT '',
    cyc_credit  REAL NOT NULL DEFAULT 0.0,
    cyc_debit   REAL NOT NULL DEFAULT 0.0,
    group_id    TEXT NOT NULL DEFAULT ''
);

CREATE TABLE IF NOT EXISTS cardxref (
    card_num    TEXT PRIMARY KEY,
    cust_id     TEXT NOT NULL,
    acct_id     TEXT NOT NULL,
    FOREIGN KEY (acct_id) REFERENCES accounts(acct_id)
);

CREATE TABLE IF NOT EXISTS transactions (
    tran_id     INTEGER PRIMARY KEY AUTOINCREMENT,
    type        TEXT NOT NULL DEFAULT 'PR',
    amount      REAL NOT NULL,
    card_num    TEXT NOT NULL,
    merchant    TEXT NOT NULL DEFAULT 'UNSPECIFIED',
    status      TEXT NOT NULL DEFAULT 'Pending',
    created_at  TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS daily_rejects (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    tran_id     INTEGER NOT NULL,
    amount      REAL NOT NULL,
    reason_code TEXT NOT NULL,
    description TEXT NOT NULL,
    created_at  TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS query_log (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    query_type  TEXT NOT NULL,
    sql_text    TEXT NOT NULL,
    result_summary TEXT,
    created_at  REAL NOT NULL
);
"""


# ---------------------------------------------------------------------------
# ASCII data file parsers (mirrors COBOL copybook layouts)
# ---------------------------------------------------------------------------

def _parse_signed(s):
    """Parse COBOL S9(n)V99 DISPLAY format with trailing overpunch sign."""
    sign_pos = {'{': 0, 'A': 1, 'B': 2, 'C': 3, 'D': 4,
                'E': 5, 'F': 6, 'G': 7, 'H': 8, 'I': 9}
    sign_neg = {'}': 0, 'J': 1, 'K': 2, 'L': 3, 'M': 4,
                'N': 5, 'O': 6, 'P': 7, 'Q': 8, 'R': 9}
    last = s[-1]
    digits = s[:-1]
    if last in sign_pos:
        return (int(digits) * 10 + sign_pos[last]) / 100.0
    elif last in sign_neg:
        return -(int(digits) * 10 + sign_neg[last]) / 100.0
    return int(s) / 100.0


def _parse_acctdata(filepath):
    """Parse acctdata.txt using CVACT01Y.cpy layout (RECLN 300).

    Fields (0-based offsets):
      ACCT-ID               PIC 9(11)     [0:11]
      ACCT-ACTIVE-STATUS    PIC X(1)      [11:12]
      ACCT-CURR-BAL         PIC S9(10)V99 [12:24]   (12 chars, trailing sign)
      ACCT-CREDIT-LIMIT     PIC S9(10)V99 [24:36]
      ACCT-CASH-CREDIT-LIM  PIC S9(10)V99 [36:48]
      ACCT-OPEN-DATE        PIC X(10)     [48:58]
      ACCT-EXPIRAION-DATE   PIC X(10)     [58:68]
      ACCT-REISSUE-DATE     PIC X(10)     [68:78]
      ACCT-CURR-CYC-CREDIT  PIC S9(10)V99 [78:90]
      ACCT-CURR-CYC-DEBIT   PIC S9(10)V99 [90:102]
      ACCT-ADDR-ZIP         PIC X(10)     [102:112]
      ACCT-GROUP-ID         PIC X(10)     [112:122]  (if present)
    """
    records = []
    for line in open(filepath):
        line = line.rstrip()
        if len(line) < 102:
            continue
        records.append({
            "acct_id":     line[0:11].lstrip("0") or "0",
            "status":      line[11:12],
            "balance":     _parse_signed(line[12:24]),
            "credit_limit": _parse_signed(line[24:36]),
            "cash_credit_limit": _parse_signed(line[36:48]),
            "open_date":   line[48:58],
            "expires":     line[58:68],
            "reissue_date": line[68:78],
            "cyc_credit":  _parse_signed(line[78:90]),
            "cyc_debit":   _parse_signed(line[90:102]),
            "group_id":    line[112:122].strip() if len(line) >= 122 else "",
        })
    return records


def _parse_cardxref(filepath):
    """Parse cardxref.txt using CVACT03Y.cpy layout (RECLN 50).

    Fields (0-based offsets):
      XREF-CARD-NUM  PIC X(16)  [0:16]
      XREF-CUST-ID   PIC 9(9)   [16:25]
      XREF-ACCT-ID   PIC 9(11)  [25:36]
    """
    records = []
    for line in open(filepath):
        line = line.rstrip()
        if len(line) < 36:
            continue
        records.append({
            "card_num": line[0:16],
            "cust_id":  line[16:25].lstrip("0") or "0",
            "acct_id":  line[25:36].lstrip("0") or "0",
        })
    return records


def _seed_from_data_files(conn):
    """Seed the database from the repo's ASCII data files."""
    acct_file = DATA_DIR / "acctdata.txt"
    xref_file = DATA_DIR / "cardxref.txt"

    if acct_file.exists():
        accounts = _parse_acctdata(str(acct_file))
        for a in accounts:
            conn.execute(
                "INSERT OR REPLACE INTO accounts "
                "(acct_id, status, balance, credit_limit, cash_credit_limit, "
                "open_date, expires, reissue_date, cyc_credit, cyc_debit, group_id) "
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                (a["acct_id"], a["status"], a["balance"], a["credit_limit"],
                 a["cash_credit_limit"], a["open_date"], a["expires"],
                 a["reissue_date"], a["cyc_credit"], a["cyc_debit"], a["group_id"]),
            )
        print(f"  Loaded {len(accounts)} accounts from {acct_file.name}")
    else:
        conn.execute(
            "INSERT OR REPLACE INTO accounts "
            "(acct_id, status, balance, credit_limit, expires, cyc_credit, cyc_debit) "
            "VALUES ('1', 'Y', 400.00, 1000.00, '2027-12-31', 0.00, 0.00)"
        )
        print("  WARNING: acctdata.txt not found, using fallback seed")

    if xref_file.exists():
        xrefs = _parse_cardxref(str(xref_file))
        for x in xrefs:
            conn.execute(
                "INSERT OR REPLACE INTO cardxref (card_num, cust_id, acct_id) "
                "VALUES (?, ?, ?)",
                (x["card_num"], x["cust_id"], x["acct_id"]),
            )
        print(f"  Loaded {len(xrefs)} card cross-references from {xref_file.name}")
    else:
        conn.execute(
            "INSERT OR REPLACE INTO cardxref (card_num, cust_id, acct_id) "
            "VALUES ('9680294154603697', '1', '1')"
        )
        print("  WARNING: cardxref.txt not found, using fallback seed")


def _log_query(conn, query_type, sql_text, result_summary=None):
    conn.execute(
        "INSERT INTO query_log (query_type, sql_text, result_summary, created_at) "
        "VALUES (?, ?, ?, ?)",
        (query_type, sql_text, result_summary, time.time()),
    )


def get_connection():
    conn = sqlite3.connect(str(DB_PATH))
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL")
    return conn


def init_db():
    conn = get_connection()
    conn.executescript(SCHEMA_SQL)
    _seed_from_data_files(conn)
    conn.commit()
    conn.close()


def reset_db():
    conn = get_connection()
    conn.execute("DELETE FROM daily_rejects")
    conn.execute("DELETE FROM transactions")
    conn.execute("DELETE FROM query_log")
    conn.execute("DELETE FROM accounts")
    conn.execute("DELETE FROM cardxref")
    _seed_from_data_files(conn)
    conn.commit()
    conn.close()
    return {"status": "ok", "message": "Database reset to initial state (from ASCII data files)"}


def get_state(acct_id=None):
    """Return full DB state for the frontend VSAM panel.

    If acct_id is None, looks up the account linked to the demo card.
    """
    conn = get_connection()

    if acct_id is None:
        xref = conn.execute(
            "SELECT acct_id FROM cardxref WHERE card_num = '9680294154603697'"
        ).fetchone()
        acct_id = xref["acct_id"] if xref else "1"

    acct_sql = f"SELECT * FROM accounts WHERE acct_id = '{acct_id}'"
    row = conn.execute(acct_sql).fetchone()
    acct = dict(row) if row else {}
    if acct:
        _log_query(conn, "SELECT", acct_sql,
                   f"balance={acct['balance']}, credit_limit={acct['credit_limit']}, "
                   f"cyc_credit={acct['cyc_credit']}, cyc_debit={acct['cyc_debit']}")

    all_accts_sql = "SELECT acct_id, status, balance, credit_limit, cyc_credit, cyc_debit FROM accounts ORDER BY acct_id"
    all_accts = [dict(r) for r in conn.execute(all_accts_sql).fetchall()]

    xref_sql = "SELECT * FROM cardxref"
    xrefs = [dict(r) for r in conn.execute(xref_sql).fetchall()]

    txn_sql = "SELECT * FROM transactions ORDER BY tran_id"
    txns = [dict(r) for r in conn.execute(txn_sql).fetchall()]

    rej_sql = "SELECT * FROM daily_rejects ORDER BY id"
    rejs = [dict(r) for r in conn.execute(rej_sql).fetchall()]

    log_sql = "SELECT * FROM query_log ORDER BY id"
    logs = [dict(r) for r in conn.execute(log_sql).fetchall()]

    conn.commit()
    conn.close()

    return {
        "account": acct,
        "all_accounts": all_accts,
        "cardxref": xrefs,
        "transactions": txns,
        "daily_rejects": rejs,
        "query_log": logs,
        "stats": {
            "total_accounts": len(all_accts),
            "total_xrefs": len(xrefs),
        },
    }


def add_transaction(amount, merchant="UNSPECIFIED", card_num="9680294154603697"):
    """
    Online phase: EXEC CICS WRITE DATASET(TRANSACT-FILE).
    Inserts a pending transaction — mirrors COTRN02C's WRITE.
    """
    conn = get_connection()

    # XREF lookup (mirrors EXEC CICS READ DATASET(CCXREF))
    xref_sql = f"SELECT acct_id, cust_id FROM cardxref WHERE card_num = '{card_num}'"
    xref = conn.execute(xref_sql).fetchone()
    if not xref:
        conn.close()
        return {"error": f"Card {card_num} not found in XREF"}
    _log_query(conn, "SELECT", xref_sql,
               f"acct_id={xref['acct_id']}, cust_id={xref['cust_id']}")

    # INSERT transaction (mirrors EXEC CICS WRITE DATASET(TRANSACT-FILE))
    ins_sql = (
        "INSERT INTO transactions (type, amount, card_num, merchant, status) "
        f"VALUES ('PR', {amount}, '{card_num}', '{merchant}', 'Pending')"
    )
    cur = conn.execute(
        "INSERT INTO transactions (type, amount, card_num, merchant, status) "
        "VALUES (?, ?, ?, ?, ?)",
        ("PR", amount, card_num, merchant, "Pending"),
    )
    tran_id = cur.lastrowid
    _log_query(conn, "INSERT", ins_sql, f"tran_id={tran_id}, status=Pending")

    conn.commit()
    conn.close()

    return {
        "tran_id": tran_id,
        "amount": amount,
        "card_num": card_num,
        "merchant": merchant,
        "status": "Pending",
        "xref": {"acct_id": dict(xref)["acct_id"], "cust_id": dict(xref)["cust_id"]},
        "queries_executed": [
            {"type": "SELECT", "sql": xref_sql},
            {"type": "INSERT", "sql": ins_sql},
        ],
    }


def run_batch():
    """
    Batch phase: mirrors CBTRN02C's processing loop.
    Reads all Pending transactions, runs overlimit check against
    the ACCOUNT master, and either posts (ACCEPT) or rejects (REJECT).

    Returns a detailed log of every query and decision for the UI.
    """
    conn = get_connection()
    results = []
    queries = []

    # Read pending transactions (mirrors 1000-DALYTRAN-GET-NEXT)
    pending_sql = "SELECT * FROM transactions WHERE status = 'Pending' ORDER BY tran_id"
    pending = conn.execute(pending_sql).fetchall()
    _log_query(conn, "SELECT", pending_sql, f"{len(pending)} pending transaction(s)")
    queries.append({"type": "SELECT", "sql": pending_sql,
                    "result": f"{len(pending)} pending transaction(s)"})

    for txn in pending:
        txn = dict(txn)
        tran_id = txn["tran_id"]
        amount = txn["amount"]
        card_num = txn["card_num"]

        # XREF lookup (mirrors 1500-A-LOOKUP-XREF)
        xref_sql = f"SELECT acct_id FROM cardxref WHERE card_num = '{card_num}'"
        xref = conn.execute(xref_sql).fetchone()
        _log_query(conn, "SELECT", xref_sql, f"acct_id={xref['acct_id']}")
        queries.append({"type": "SELECT", "sql": xref_sql,
                        "result": f"acct_id={xref['acct_id']}"})

        acct_id = xref["acct_id"]

        # Read account master (mirrors 1500-B-LOOKUP-ACCT)
        acct_sql = (
            f"SELECT balance, credit_limit, cyc_credit, cyc_debit "
            f"FROM accounts WHERE acct_id = '{acct_id}'"
        )
        acct = dict(conn.execute(acct_sql).fetchone())
        _log_query(conn, "SELECT", acct_sql,
                   f"balance={acct['balance']}, credit_limit={acct['credit_limit']}, "
                   f"cyc_credit={acct['cyc_credit']}, cyc_debit={acct['cyc_debit']}")
        queries.append({"type": "SELECT", "sql": acct_sql,
                        "result": f"balance={acct['balance']}, limit={acct['credit_limit']}, "
                                  f"cyc_cr={acct['cyc_credit']}, cyc_db={acct['cyc_debit']}"})

        # Overlimit check (mirrors CBTRN02C.cbl:407)
        # COMPUTE WS-TEMP-BAL = ACCT-CURR-CYC-CREDIT - ACCT-CURR-CYC-DEBIT + DALYTRAN-AMT
        temp_bal = acct["cyc_credit"] - acct["cyc_debit"] + amount
        check_sql = (
            f"-- OVERLIMIT CHECK (CBTRN02C.cbl:407)\n"
            f"-- COMPUTE WS-TEMP-BAL = cyc_credit({acct['cyc_credit']}) "
            f"- cyc_debit({acct['cyc_debit']}) + amount({amount})\n"
            f"-- WS-TEMP-BAL = {temp_bal}\n"
            f"-- IF credit_limit({acct['credit_limit']}) >= WS-TEMP-BAL({temp_bal})"
        )
        accepted = acct["credit_limit"] >= temp_bal

        if accepted:
            decision = "ACCEPTED"
            reason = f"credit_limit({acct['credit_limit']}) >= temp_bal({temp_bal})"

            # Post transaction (mirrors 2000-POST-TRANSACTION)
            new_bal = acct["balance"] + amount
            new_cyc_cr = acct["cyc_credit"] + amount

            update_acct_sql = (
                f"UPDATE accounts SET balance = {new_bal}, cyc_credit = {new_cyc_cr} "
                f"WHERE acct_id = '{acct_id}'"
            )
            conn.execute(
                "UPDATE accounts SET balance = ?, cyc_credit = ? WHERE acct_id = ?",
                (new_bal, new_cyc_cr, acct_id),
            )
            _log_query(conn, "UPDATE", update_acct_sql,
                       f"balance: {acct['balance']}→{new_bal}, cyc_credit: {acct['cyc_credit']}→{new_cyc_cr}")
            queries.append({"type": "UPDATE", "sql": update_acct_sql,
                            "result": f"balance: {acct['balance']}→{new_bal}, "
                                      f"cyc_credit: {acct['cyc_credit']}→{new_cyc_cr}"})

            update_txn_sql = (
                f"UPDATE transactions SET status = 'Posted' WHERE tran_id = {tran_id}"
            )
            conn.execute(
                "UPDATE transactions SET status = ? WHERE tran_id = ?",
                ("Posted", tran_id),
            )
            _log_query(conn, "UPDATE", update_txn_sql, "status: Pending→Posted")
            queries.append({"type": "UPDATE", "sql": update_txn_sql,
                            "result": "status: Pending→Posted"})

            results.append({
                "tran_id": tran_id,
                "amount": amount,
                "decision": "ACCEPTED",
                "reason": reason,
                "temp_bal": temp_bal,
                "new_balance": new_bal,
                "new_cyc_credit": new_cyc_cr,
            })
        else:
            decision = "REJECTED"
            reason = f"credit_limit({acct['credit_limit']}) < temp_bal({temp_bal})"

            # Write reject record (mirrors 2500-WRITE-REJECT-REC)
            rej_ins_sql = (
                f"INSERT INTO daily_rejects (tran_id, amount, reason_code, description) "
                f"VALUES ({tran_id}, {amount}, '102', 'OVERLIMIT TRANSACTION')"
            )
            conn.execute(
                "INSERT INTO daily_rejects (tran_id, amount, reason_code, description) "
                "VALUES (?, ?, ?, ?)",
                (tran_id, amount, "102", "OVERLIMIT TRANSACTION"),
            )
            _log_query(conn, "INSERT", rej_ins_sql,
                       "reason_code=102, OVERLIMIT TRANSACTION")
            queries.append({"type": "INSERT", "sql": rej_ins_sql,
                            "result": "reason_code=102, OVERLIMIT TRANSACTION"})

            update_txn_sql = (
                f"UPDATE transactions SET status = 'Rejected' WHERE tran_id = {tran_id}"
            )
            conn.execute(
                "UPDATE transactions SET status = ? WHERE tran_id = ?",
                ("Rejected", tran_id),
            )
            _log_query(conn, "UPDATE", update_txn_sql, "status: Pending→Rejected")
            queries.append({"type": "UPDATE", "sql": update_txn_sql,
                            "result": "status: Pending→Rejected"})

            results.append({
                "tran_id": tran_id,
                "amount": amount,
                "decision": "REJECTED",
                "reason": reason,
                "reason_code": "102",
                "description": "OVERLIMIT TRANSACTION",
                "temp_bal": temp_bal,
            })

        queries.append({"type": "DECISION", "sql": check_sql,
                        "result": f"{decision}: {reason}"})

    conn.commit()
    conn.close()

    return {
        "results": results,
        "queries_executed": queries,
    }


def get_query_log(since=None):
    """Get query log entries, optionally filtered by timestamp."""
    conn = get_connection()
    if since is not None:
        rows = conn.execute(
            "SELECT * FROM query_log WHERE created_at > ? ORDER BY id",
            (since,),
        ).fetchall()
    else:
        rows = conn.execute("SELECT * FROM query_log ORDER BY id").fetchall()
    conn.close()
    return [dict(r) for r in rows]
