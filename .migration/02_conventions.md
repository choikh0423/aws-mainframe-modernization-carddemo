# 02 — Conventions probed from the source

## Encoding
COBOL, copybook, JCL, CSD and BMS sources are **plain ASCII** (verified: `file app/cbl/CBTRN02C.cbl` →
`ASCII text`). No LATIN-1 transcoding is required. The only EBCDIC content is sample **data**:
`app/data/EBCDIC/**` and `app/app-authorization-ims-db2-mq/data/EBCDIC/**`; ASCII equivalents exist in
`app/data/ASCII/**` and are the ones to seed from.

Some members carry sequence numbers in columns 1-6 and 73-80 (e.g. `app/cpy/COADM02Y.cpy`); most do not.
Any parser must treat columns 7 (`*` / `/` = comment) and 8-72 as the only meaningful area.

## Program-name prefixes

| Prefix | Meaning | Example |
|---|---|---|
| `CO` | CICS online program (screen handler) | `COACTVWC` (Account View) |
| `CB` | COBOL batch program | `CBTRN02C` (post daily transactions) |
| `CS` | Shared/utility subroutine | `CSUTLDTC` (date validation) |
| `CV`/`CI`/`CC` (copybooks) | Record layouts / interface areas | `CVTRA05Y` (TRAN-RECORD) |
| `PAU`/`DB`-prefixed | IMS DL/I load/unload utilities | `PAUDBLOD`, `DBUNLDGS` |

Trailing `C` on an online program name = "CICS program"; the 4-character CICS transaction id is defined
separately in the CSD.

## Where things live

| Artifact | Location |
|---|---|
| Online + batch COBOL | `app/cbl/*.cbl`, add-on modules under `app/app-*/cbl/` |
| Copybooks | `app/cpy/*.cpy` (data + interfaces), `app/cpy-bms/*.cpy` (generated map symbolic maps) |
| BMS map source | `app/bms/*.bms`, add-on modules under `app/app-*/bms/` |
| JCL / PROC | `app/jcl/*.jcl`, `app/proc/*.prc`, add-on modules under `app/app-*/jcl/` |
| CICS resource definitions | `app/csd/CARDDEMO.CSD`, `app/app-transaction-type-db2/csd/CRDDEMOD.csd`, `app/app-authorization-ims-db2-mq/csd/CRDDEMO2.csd`, `app/app-vsam-mq/csd/CRDDEMOM.csd` |
| DB2 DDL / DCLGEN | `app/app-transaction-type-db2/{ddl,dcl}`, `app/app-authorization-ims-db2-mq/{ddl,dcl}` |
| IMS DBD / PSB | `app/app-authorization-ims-db2-mq/ims/` |
| Scheduler | `app/scheduler/CardDemo.controlm` (Control-M), `app/scheduler/CardDemo.ca7` (CA-7) |
| Sample data | `app/data/ASCII/**` (use these), `app/data/EBCDIC/**` |
| Assembler | `app/asm/` (`MVSWAIT`, `COBDATFT`) |

## Online navigation protocol
CICS **pseudo-conversational**. Screen-to-screen routing is `EXEC CICS XCTL PROGRAM(CDEMO-TO-PROGRAM)`
where `CDEMO-TO-PROGRAM` is a field of the shared COMMAREA copybook `COCOM01Y`; the caller records itself
in `CDEMO-FROM-PROGRAM`. The card streams add their own routing field `CCARD-NEXT-PROG` (`CVCRD01Y`).
Menu dispatch is table-driven: `app/cpy/COMEN02Y.cpy` (11 user options) and `app/cpy/COADM02Y.cpy`
(6 admin options), each row being `option-number / label / 8-char program name [/ user type]`.

## Batch protocol
One JCL job per business function; steps are `EXEC PGM=`. Utility steps (`IDCAMS`, `IEBGENER`, `SORT`,
`IEFBR14`, `IKJEFT01`, `DFSRRC00`, `SDSF`) surround the COBOL steps. Job-to-job dependencies live in the
scheduler definitions, not in the JCL. GDG generations are written as `DSN=...(+1)`.

## Data-access protocol
- Online: `EXEC CICS READ/STARTBR/READNEXT/READPREV/ENDBR/WRITE/REWRITE` against VSAM KSDS, with
  `RESP`/`RESP2` checked after every call.
- Batch: plain COBOL `OPEN/READ/WRITE/REWRITE/CLOSE` with `FILE STATUS` checks; an abend is raised via
  `CALL 'CEE3ABD'`.
- IMS: `CALL 'CBLTDLI'` with the function codes in `app/app-authorization-ims-db2-mq/cpy/IMSFUNCS.cpy`.
- MQ: `CALL 'MQOPEN'/'MQPUT'/'MQPUT1'/'MQGET'/'MQCLOSE'`.
- DB2: embedded `EXEC SQL` with DCLGEN copybooks under `app/app-transaction-type-db2/dcl/`.

## Message / label conventions
Screen messages are literals inside the programs (e.g. `'Tran ID must be Numeric ...'`), plus the shared
message copybooks `CSMSG01Y`/`CSMSG02Y`. Labels come from the BMS maps. All English; no i18n.
Migrated code must reproduce the message text **verbatim**.
