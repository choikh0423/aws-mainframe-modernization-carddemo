# 03 — Glossary

## Shop vocabulary (as used in this engagement)
| Term | Meaning here |
|---|---|
| Module | The whole CardDemo estate (online + batch + add-ons) |
| Stream | One selectable, end-to-end unit of execution that can be migrated on its own: an online functional area (a set of screens sharing data ownership) or a scheduled batch chain |
| Wave | One child session's worth of work inside a stream — a set of programs migrated and merged together |
| Surface | ONLINE / BATCH / SUBTRANSACTION / DATA-BOUNDARY — selects which target-state profile applies |
| Boundary | A point where execution leaves the module (MQ, IMS, DB2 add-on, dataset hand-off, external call) |

## Business vocabulary
| Term | Meaning |
|---|---|
| Account (ACCTDAT, `CVACT01Y`) | A credit card account: credit limits, balances, cycle credits/debits, status, group id |
| Customer (CUSTDAT, `CVCUS01Y`) | The party owning accounts: name, address, SSN, FICO score |
| Card (CARDDAT, `CVACT02Y`) | A plastic card: 16-digit number, embossed name, expiry, active status |
| XREF (CCXREF, `CVACT03Y`) | Card ↔ Customer ↔ Account cross reference; `CXACAIX` is its alternate index by account |
| Transaction (TRANSACT, `CVTRA05Y`) | A 350-byte posted transaction: id, type, category, source, amount, merchant, card, timestamps |
| Daily transaction (DALYTRAN, `CVTRA06Y`) | The unposted daily input file consumed by POSTTRAN |
| Transaction category balance (TCATBALF, `CVTRA01Y`) | Running balance per account/type/category, updated by posting, read by interest calculation |
| Transaction type / category (`CVTRA03Y`, `CVTRA04Y`) | Reference data for transaction classification; also held in DB2 by the add-on module |
| Disclosure group (DISCGRP, `CVTRA02Y`) | Interest rate table keyed by account group / type / category |
| Statement (`COSTM01`) | Monthly statement produced by CBSTM03A in plain text and HTML |
| Pending authorization | An IMS-held authorization awaiting posting; viewed by CPVS/CPVD, purged by CBPAUP0J |
| User security (USRSEC, `CSUSR01Y`) | Sign-on credentials and user type (`U` user / `A` admin) |

## CICS / mainframe vocabulary
| Term | Meaning |
|---|---|
| COMMAREA | Communication area passed between pseudo-conversational tasks (`COCOM01Y`) |
| XCTL / LINK / START | Transfer control (no return) / call and return / start another transaction |
| BMS map | 3270 screen definition; `app/bms/*.bms` with generated symbolic maps in `app/cpy-bms/` |
| KSDS / ESDS / RRDS / AIX | VSAM dataset organisations; AIX = alternate index |
| GDG | Generation data group; `(+1)` creates the next generation |
| PF key | 3270 function key; PF3 back, PF7 page back, PF8 page forward |
| DL/I | IMS database call interface (`CBLTDLI`) |
| DCLGEN | Generated COBOL declaration of a DB2 table |
