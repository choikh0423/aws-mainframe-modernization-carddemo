#!/usr/bin/env python3
"""Generate the CardDemo seed migration from the legacy ASCII unloads.

Reads the fixed-width files under ``app/data/ASCII`` (never the EBCDIC copies)
plus the in-stream USRSEC records in ``app/jcl/DUSRSECJ.jcl``, and writes

    migration/carddemo/backend/src/main/resources/db/seed/R__seed_carddemo_data.sql

That repeatable Flyway migration is what replaces the S-18 DataLoadAndSetup
IDCAMS jobs: it deletes and reloads the demo dataset, exactly like the legacy
DELETE/DEFINE + REPRO sequence did.

Field offsets come from the copybooks; signed PIC S9(a)V9(b) DISPLAY fields
carry their sign as a trailing overpunch character, which is decoded here.

Run from the repository root:

    python3 migration/carddemo/tools/generate_seed_sql.py
"""

from __future__ import annotations

import os
import sys
from decimal import Decimal

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
ASCII_DIR = os.path.join(REPO_ROOT, "app", "data", "ASCII")
USRSEC_JCL = os.path.join(REPO_ROOT, "app", "jcl", "DUSRSECJ.jcl")
OUTPUT = os.path.join(REPO_ROOT, "migration", "carddemo", "backend", "src", "main",
                      "resources", "db", "seed", "R__seed_carddemo_data.sql")

# COBOL DISPLAY sign overpunch on the final digit.
OVERPUNCH = {
    "{": ("+", "0"), "A": ("+", "1"), "B": ("+", "2"), "C": ("+", "3"), "D": ("+", "4"),
    "E": ("+", "5"), "F": ("+", "6"), "G": ("+", "7"), "H": ("+", "8"), "I": ("+", "9"),
    "}": ("-", "0"), "J": ("-", "1"), "K": ("-", "2"), "L": ("-", "3"), "M": ("-", "4"),
    "N": ("-", "5"), "O": ("-", "6"), "P": ("-", "7"), "Q": ("-", "8"), "R": ("-", "9"),
}


class Field:
    def __init__(self, column, size, kind, scale=0):
        self.column = column
        self.size = size
        self.kind = kind  # 'X', '9' or 'S9'
        self.scale = scale


def X(column, size):
    return Field(column, size, "X")


def NUM(column, size):
    return Field(column, size, "9")


def SNUM(column, size, scale):
    return Field(column, size, "S9", scale)


def decode_signed(raw: str, scale: int) -> Decimal:
    digits, last = raw[:-1], raw[-1]
    if last in OVERPUNCH:
        sign, digit = OVERPUNCH[last]
    else:
        sign, digit = "+", last
    value = Decimal(sign + (digits + digit))
    return value.scaleb(-scale) if scale else value


def sql_literal(value) -> str:
    if value is None:
        return "NULL"
    if isinstance(value, (int, Decimal)):
        return str(value)
    return "'" + value.replace("'", "''") + "'"


def parse(path, layout, record_len):
    rows = []
    with open(path, "r", encoding="utf-8", newline="") as handle:
        for line in handle:
            line = line.rstrip("\r\n")
            if not line.strip():
                continue
            line = line.ljust(record_len)
            row, offset = [], 0
            for field in layout:
                raw = line[offset:offset + field.size]
                offset += field.size
                if field.kind == "X":
                    row.append(raw.rstrip())
                elif field.kind == "9":
                    row.append(int(raw) if raw.strip() else 0)
                else:
                    row.append(decode_signed(raw, field.scale))
            rows.append(row)
    return rows


def parse_usrsec(path):
    layout = [X("sec_usr_id", 8), X("sec_usr_fname", 20), X("sec_usr_lname", 20),
              X("sec_usr_pwd", 8), X("sec_usr_type", 1)]
    rows, in_data = [], False
    with open(path, "r", encoding="utf-8") as handle:
        for line in handle:
            line = line.rstrip("\r\n")
            if line.startswith("//SYSUT1"):
                in_data = True
                continue
            if not in_data:
                continue
            if line.startswith("/*") or line.startswith("//"):
                break
            padded = line.ljust(57)
            row, offset = [], 0
            for field in layout:
                row.append(padded[offset:offset + field.size].rstrip())
                offset += field.size
            rows.append(row)
    return rows


TRAN_LAYOUT = [
    X("id", 16), X("type_cd", 2), NUM("cat_cd", 4), X("source", 10), X("description", 100),
    SNUM("amount", 11, 2), NUM("merchant_id", 9), X("merchant_name", 50),
    X("merchant_city", 50), X("merchant_zip", 10), X("card_num", 16),
    X("orig_ts", 26), X("proc_ts", 26),
]

TABLES = [
    ("accounts", "acctdata.txt", 300, "ACCTDAT / CVACT01Y", [
        NUM("acct_id", 11), X("active_status", 1),
        SNUM("curr_bal", 12, 2), SNUM("credit_limit", 12, 2), SNUM("cash_credit_limit", 12, 2),
        X("open_date", 10), X("expiraion_date", 10), X("reissue_date", 10),
        SNUM("curr_cyc_credit", 12, 2), SNUM("curr_cyc_debit", 12, 2),
        X("addr_zip", 10), X("group_id", 10),
    ]),
    ("customers", "custdata.txt", 500, "CUSTDAT / CVCUS01Y", [
        NUM("cust_id", 9), X("first_name", 25), X("middle_name", 25), X("last_name", 25),
        X("addr_line_1", 50), X("addr_line_2", 50), X("addr_line_3", 50),
        X("addr_state_cd", 2), X("addr_country_cd", 3), X("addr_zip", 10),
        X("phone_num_1", 15), X("phone_num_2", 15), NUM("ssn", 9), X("govt_issued_id", 20),
        X("dob_yyyy_mm_dd", 10), X("eft_account_id", 10), X("pri_card_holder_ind", 1),
        NUM("fico_credit_score", 3),
    ]),
    ("cards", "carddata.txt", 150, "CARDDAT / CVACT02Y", [
        X("card_num", 16), NUM("acct_id", 11), NUM("cvv_cd", 3),
        X("embossed_name", 50), X("expiraion_date", 10), X("active_status", 1),
    ]),
    ("card_xref", "cardxref.txt", 50, "CCXREF / CVACT03Y", [
        X("card_num", 16), NUM("cust_id", 9), NUM("acct_id", 11),
    ]),
    ("daily_transactions", "dailytran.txt", 350, "DALYTRAN / CVTRA06Y", TRAN_LAYOUT),
    ("transaction_category_balances", "tcatbal.txt", 50, "TCATBALF / CVTRA01Y", [
        NUM("acct_id", 11), X("type_cd", 2), NUM("cat_cd", 4), SNUM("bal", 11, 2),
    ]),
    ("disclosure_groups", "discgrp.txt", 50, "DISCGRP / CVTRA02Y", [
        X("acct_group_id", 10), X("tran_type_cd", 2), NUM("tran_cat_cd", 4),
        SNUM("int_rate", 6, 2),
    ]),
    ("transaction_types", "trantype.txt", 60, "TRANTYPE / CVTRA03Y", [
        X("tran_type", 2), X("tran_type_desc", 50),
    ]),
    ("transaction_categories", "trancatg.txt", 60, "TRANCATG / CVTRA04Y", [
        X("tran_type_cd", 2), NUM("tran_cat_cd", 4), X("tran_cat_type_desc", 50),
    ]),
]

# The DB2 add-on keeps its own copy of the transaction type reference data
# (app/app-transaction-type-db2). CBTRN03C/COTRTLIC read it from DB2, so it is
# loaded from the same VSAM unloads the DB2 LOAD utility was fed.
DB2_MIRRORS = [
    ("db2_transaction_type", "transaction_types", ["tr_type", "tr_description"]),
    ("db2_transaction_type_category", "transaction_categories",
     ["trc_type_code", "trc_type_category", "trc_cat_data"]),
]

# Delete order: children before parents.
DELETE_ORDER = [
    "db2_transaction_type_category", "db2_transaction_type",
    "pending_auth_detail", "pending_auth_summary", "auth_fraud",
    "transaction_categories", "transaction_types", "disclosure_groups",
    "transaction_category_balances", "daily_transactions", "card_xref",
    "cards", "customers", "accounts", "sec_users",
]


def render_inserts(table, columns, rows):
    lines = []
    collist = ", ".join(columns)
    for row in rows:
        values = ", ".join(sql_literal(v) for v in row)
        lines.append("INSERT INTO %s (%s) VALUES (%s);" % (table, collist, values))
    return lines


def main():
    out = [
        "-- Generated by migration/carddemo/tools/generate_seed_sql.py - do not edit by hand.",
        "--",
        "-- The CardDemo demo dataset, loaded from the ASCII unloads in app/data/ASCII and the",
        "-- in-stream USRSEC records in app/jcl/DUSRSECJ.jcl. This repeatable migration is the",
        "-- replacement for the S-18 DataLoadAndSetup IDCAMS jobs, so like them it clears each",
        "-- file before reloading it.",
        "",
    ]
    for table in DELETE_ORDER:
        out.append("DELETE FROM %s;" % table)
    out.append("")

    parsed = {}
    for table, filename, record_len, origin, layout in TABLES:
        rows = parse(os.path.join(ASCII_DIR, filename), layout, record_len)
        parsed[table] = rows
        columns = [f.column for f in layout]
        out.append("-- %s: %d records from app/data/ASCII/%s (%s)"
                   % (table, len(rows), filename, origin))
        out.extend(render_inserts(table, columns, rows))
        out.append("")

    for db2_table, source_table, columns in DB2_MIRRORS:
        # TRC_TYPE_CATEGORY is CHAR(4) in the DB2 DDL where the VSAM copybook has
        # PIC 9(04), so the category code is zero-padded rather than numeric.
        rows = [[("%04d" % v) if isinstance(v, int) else v for v in row]
                for row in parsed[source_table]]
        out.append("-- %s: %d records mirrored from %s (DB2 add-on module)"
                   % (db2_table, len(rows), source_table))
        out.extend(render_inserts(db2_table, columns, rows))
        out.append("")

    users = parse_usrsec(USRSEC_JCL)
    out.append("-- sec_users: %d records from the in-stream SYSUT1 data of app/jcl/DUSRSECJ.jcl"
               % len(users))
    out.extend(render_inserts(
        "sec_users",
        ["sec_usr_id", "sec_usr_fname", "sec_usr_lname", "sec_usr_pwd", "sec_usr_type"],
        users))
    out.append("")

    os.makedirs(os.path.dirname(OUTPUT), exist_ok=True)
    with open(OUTPUT, "w", encoding="utf-8") as handle:
        handle.write("\n".join(out))
    print("wrote %s" % os.path.relpath(OUTPUT, REPO_ROOT))
    return 0


if __name__ == "__main__":
    sys.exit(main())
