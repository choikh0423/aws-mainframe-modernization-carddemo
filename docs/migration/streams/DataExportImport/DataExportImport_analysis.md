# S-16 DataExportImport — source analysis

Scope: `app/jcl/CBEXPORT.jcl`, `app/jcl/CBIMPORT.jcl`, `app/cbl/CBEXPORT.cbl`, `app/cbl/CBIMPORT.cbl`,
copybook `app/cpy/CVEXPORT.cpy`, boundary `app/jcl/FTPJCL.JCL` (B-12).

Both programs are pure batch: no BMS map, no CICS command, no `XCTL`/`LINK`, no `CDEMO-TO-PROGRAM` /
`CCARD-NEXT-PROG` navigation, no COMMAREA. Control flow is a single `PROCEDURE DIVISION` fall-through
(`CBEXPORT.cbl:149-158`, `CBIMPORT.cbl:165-171`) ending in `GOBACK`. The only external call in either
program is `CALL 'CEE3ABD'` (`CBEXPORT.cbl:579`, `CBIMPORT.cbl:483`), i.e. boundary B-01.

---

## 1. Entry conditions

| Program | Entry | Parameters | Preconditions |
|---|---|---|---|
| CBEXPORT | `EXEC PGM=CBEXPORT` in `CBEXPORT.jcl` STEP02 | none (no `PARM=`, no SYSIN) | The five input VSAM KSDS clusters exist and open cleanly; `EXPFILE` was deleted and re-`DEFINE`d by STEP01 (`CBEXPORT.jcl:26-40`) |
| CBIMPORT | `EXEC PGM=CBIMPORT` in `CBIMPORT.jcl` STEP01 | none | `EXPFILE` exists (the file CBEXPORT wrote); all output datasets are `DISP=(NEW,CATLG,DELETE)` |

`CBEXPORT.jcl` STEP01 is `IDCAMS`: `DELETE ... CLUSTER PURGE`, `SET MAXCC = 0` (so a first run where the
cluster does not exist still ends RC 0), then `DEFINE CLUSTER ... INDEXED KEYS(4 28) RECORDSIZE(500 500)`
(`CBEXPORT.jcl:29-40`). Every CBEXPORT run therefore starts from an empty export file — the job has no
incremental mode.

**Quirk (key offset).** `KEYS(4 28)` declares the 4-byte key at offset 28, but in `CVEXPORT.cpy` the key
field `EXPORT-SEQUENCE-NUM` begins at offset **27** (0-based: 1 byte `EXPORT-REC-TYPE` + 26 bytes
`EXPORT-TIMESTAMP`). The IDCAMS definition is off by one against the record layout the program writes.
The program itself never depends on the VSAM key position (it writes sequentially), so on the legacy side
this only affects the KSDS index; it is recorded here, not "fixed".

## 2. File access patterns

### CBEXPORT (`CBEXPORT.cbl:34-92`)

| DD | Logical file | Organization | Access | Key | Direction |
|---|---|---|---|---|---|
| CUSTFILE | CUSTOMER-INPUT (`CVCUS01Y`) | INDEXED | SEQUENTIAL | `CUST-ID` | read |
| ACCTFILE | ACCOUNT-INPUT (`CVACT01Y`) | INDEXED | SEQUENTIAL | `ACCT-ID` | read |
| XREFFILE | XREF-INPUT (`CVACT03Y`) | INDEXED | SEQUENTIAL | `XREF-CARD-NUM` | read |
| TRANSACT | TRANSACTION-INPUT (`CVTRA05Y`) | INDEXED | SEQUENTIAL | `TRAN-ID` | read |
| CARDFILE | CARD-INPUT (`CVACT02Y`) | INDEXED | SEQUENTIAL | `CARD-NUM` | read |
| EXPFILE | EXPORT-OUTPUT, `PIC X(500)` RECFM F | INDEXED | SEQUENTIAL | `EXPORT-SEQUENCE-NUM` | write |

`ACCESS MODE IS SEQUENTIAL` on a KSDS means every input is read in **ascending key order**, and the export
file is written in ascending sequence-number order.

### CBIMPORT (`CBIMPORT.cbl:36-109`)

| DD | Logical file | Organization | Record length | Direction |
|---|---|---|---|---|
| EXPFILE | EXPORT-INPUT `PIC X(500)` | INDEXED, SEQUENTIAL access, key `EXPORT-SEQUENCE-NUM` | 500 | read |
| CUSTOUT | CUSTOMER-OUTPUT (`CVCUS01Y`) | SEQUENTIAL | 500 | write |
| ACCTOUT | ACCOUNT-OUTPUT (`CVACT01Y`) | SEQUENTIAL | 300 | write |
| XREFOUT | XREF-OUTPUT (`CVACT03Y`) | SEQUENTIAL | 50 | write |
| TRNXOUT | TRANSACTION-OUTPUT (`CVTRA05Y`) | SEQUENTIAL | 350 | write |
| CARDOUT | CARD-OUTPUT (`CVACT02Y`) | SEQUENTIAL | 150 | write |
| ERROUT | ERROR-OUTPUT `PIC X(132)` | SEQUENTIAL | 132 | write |

**Quirk (missing DD).** `CBIMPORT.jcl` defines CUSTOUT, ACCTOUT, XREFOUT, TRNXOUT and ERROUT but **no
CARDOUT DD** (`CBIMPORT.jcl:29-62`), while the program `SELECT`s CARDOUT and `OPEN OUTPUT`s it
(`CBIMPORT.cbl:63-66, 233-238`). As shipped, the job cannot run to completion on z/OS: the OPEN of
CARD-OUTPUT fails and `1100-OPEN-FILES` abends. This is a genuine source-vs-JCL contradiction and is the
one place where the shipped job is inconsistent with the shipped program.

## 3. Record layouts

`CVEXPORT.cpy` — 500-byte export record, a fixed prefix plus a 460-byte `EXPORT-RECORD-DATA` area
`REDEFINE`d five ways. Unlike every other CardDemo file, this layout uses **binary (`COMP`) and packed
(`COMP-3`) fields**, so the export file is not printable text.

Prefix (`CVEXPORT.cpy:9-19`), offsets 0-based:

| Offset | Len | Field | PIC / storage |
|---:|---:|---|---|
| 0 | 1 | `EXPORT-REC-TYPE` | `X(1)` — `C`/`A`/`X`/`T`/`D` |
| 1 | 26 | `EXPORT-TIMESTAMP` | `X(26)` (redefined as date 10 / sep 1 / time 15) |
| 27 | 4 | `EXPORT-SEQUENCE-NUM` | `9(9) COMP` — 4-byte big-endian binary |
| 31 | 4 | `EXPORT-BRANCH-ID` | `X(4)` |
| 35 | 5 | `EXPORT-REGION-CODE` | `X(5)` |
| 40 | 460 | `EXPORT-RECORD-DATA` | `X(460)` |

Data area, offsets relative to byte 40. Storage sizes are the IBM Enterprise COBOL defaults, and each one
is confirmed arithmetically by the trailing FILLER adding up to exactly 460:

`C` customer (`CVEXPORT.cpy:24-42`): `EXP-CUST-ID` `9(9) COMP` 4B @0; first/middle/last name `X(25)` @4/29/54;
`EXP-CUST-ADDR-LINE` `X(50)` OCCURS 3 @79/129/179; state `X(2)` @229; country `X(3)` @231; zip `X(10)` @234;
`EXP-CUST-PHONE-NUM` `X(15)` OCCURS 2 @244/259; SSN `9(9)` display 9B @274; govt id `X(20)` @283;
DOB `X(10)` @303; EFT account `X(10)` @313; primary-holder ind `X(1)` @323; FICO `9(3) COMP-3` 2B @324;
FILLER `X(134)` @326.

`A` account (`CVEXPORT.cpy:47-60`): `EXP-ACCT-ID` `9(11)` display 11B @0; status `X(1)` @11;
`EXP-ACCT-CURR-BAL` `S9(10)V99 COMP-3` 7B @12; `EXP-ACCT-CREDIT-LIMIT` `S9(10)V99` display 12B @19;
`EXP-ACCT-CASH-CREDIT-LIMIT` `S9(10)V99 COMP-3` 7B @31; open/expiry/reissue date `X(10)` @38/48/58;
`EXP-ACCT-CURR-CYC-CREDIT` `S9(10)V99` display 12B @68; `EXP-ACCT-CURR-CYC-DEBIT` `S9(10)V99 COMP` 8B @80;
zip `X(10)` @88; group id `X(10)` @98; FILLER `X(352)` @108.

`T` transaction (`CVEXPORT.cpy:65-79`): id `X(16)` @0; type `X(2)` @16; category `9(4)` display @18;
source `X(10)` @22; description `X(100)` @32; `EXP-TRAN-AMT` `S9(9)V99 COMP-3` 6B @132;
`EXP-TRAN-MERCHANT-ID` `9(9) COMP` 4B @138; merchant name `X(50)` @142; city `X(50)` @192; zip `X(10)` @242;
card number `X(16)` @252; orig ts `X(26)` @268; proc ts `X(26)` @294; FILLER `X(140)` @320.

`X` card xref (`CVEXPORT.cpy:84-88`): card number `X(16)` @0; `EXP-XREF-CUST-ID` `9(9)` display 9B @16;
`EXP-XREF-ACCT-ID` `9(11) COMP` 8B @25; FILLER `X(427)` @33.

`D` card (`CVEXPORT.cpy:93-100`): card number `X(16)` @0; `EXP-CARD-ACCT-ID` `9(11) COMP` 8B @16;
`EXP-CARD-CVV-CD` `9(3) COMP` 2B @24; embossed name `X(50)` @26; expiry `X(10)` @76; status `X(1)` @86;
FILLER `X(373)` @87.

Target record layouts are the standard estate copybooks, all DISPLAY: `CVCUS01Y` (500), `CVACT01Y` (300),
`CVACT02Y` (150), `CVACT03Y` (50), `CVTRA05Y` (350). Their signed `S9(10)V99` / `S9(9)V99` fields carry the
sign as a trailing overpunch, exactly as in the `app/data/ASCII/**` unloads (e.g. `00000001940{` in
`app/data/ASCII/acctdata.txt`).

## 4. CBEXPORT control flow and business rules

```
0000-MAIN-PROCESSING (CBEXPORT.cbl:149)
  1000-INITIALIZE            -> 1050-GENERATE-TIMESTAMP, 1100-OPEN-FILES
  2000-EXPORT-CUSTOMERS      -> read CUSTFILE until EOF, 2200-CREATE-CUSTOMER-EXP-REC per record
  3000-EXPORT-ACCOUNTS       -> ACCTFILE  / 3200-CREATE-ACCOUNT-EXP-REC
  4000-EXPORT-XREFS          -> XREFFILE  / 4200-CREATE-XREF-EXPORT-RECORD
  5000-EXPORT-TRANSACTIONS   -> TRANSACT  / 5200-CREATE-TRAN-EXP-REC
  5500-EXPORT-CARDS          -> CARDFILE  / 5700-CREATE-CARD-EXPORT-RECORD
  6000-FINALIZE              -> CLOSE all, DISPLAY statistics
  GOBACK
```

Rules:

1. **Record order is by file, then by key.** All customers, then all accounts, then all xrefs, then all
   transactions, then all cards (`CBEXPORT.cbl:151-157`); within each group, ascending key order.
2. **One export record per input record.** No filtering, no selection, no joining — every record of every
   input file is exported (`CBEXPORT.cbl:249-252` and the equivalent loops).
3. **Sequence number is global and 1-based**, incremented once per written record across all five groups
   (`ADD 1 TO WS-SEQUENCE-COUNTER`, e.g. `CBEXPORT.cbl:276-277`).
4. **Timestamp is captured once**, in `1050-GENERATE-TIMESTAMP` (`CBEXPORT.cbl:172-195`), and stamped on
   every record: `WS-EXPORT-DATE` = `YYYY-MM-DD`, `WS-EXPORT-TIME` = `HH:MM:SS`, and
   `WS-FORMATTED-TIMESTAMP` = `WS-EXPORT-DATE + ' ' + WS-EXPORT-TIME + '.00'` — 22 characters moved into a
   `X(26)` field, so the last 4 bytes are spaces. The `.00` is a literal: hundredths are read
   (`WS-CURR-HUNDREDTH`) but never used.
5. **Branch and region are hard-coded literals** on every record: `EXPORT-BRANCH-ID` = `'0001'`,
   `EXPORT-REGION-CODE` = `'NORTH'` (e.g. `CBEXPORT.cbl:278-279`). There is no branch parameter anywhere in
   the program or the JCL, despite the "branch migration" use case in the header comment.
6. **`INITIALIZE EXPORT-RECORD` before each record** (e.g. `CBEXPORT.cbl:271`) sets alphanumeric fields to
   spaces and numeric fields to zero — including the whole 460-byte data area — so the unused tail of each
   record type is spaces, and unmapped numeric subfields are zero.
7. **Field mapping is 1:1** with no transformation; the export field list per type is exactly the copybook
   field list above (`CBEXPORT.cbl:282-299, 351-362, 415-417, 470-482, 535-540`).
8. **Counters**: one per record type plus `WS-TOTAL-RECORDS-EXPORTED`, all `PIC 9(9)`.

### Error paths (CBEXPORT)

| Condition | Message (verbatim) | Action |
|---|---|---|
| OPEN of any input/output fails (status ≠ `00`) | `ERROR: Cannot open <name>, Status: ` + status | `9999-ABEND-PROGRAM` |
| READ status not `00` and not `10` | `ERROR: Reading <name>, Status: ` + status | `9999-ABEND-PROGRAM` |
| WRITE status ≠ `00` | `ERROR: Writing export record, Status: ` + status | `9999-ABEND-PROGRAM` |
| abend | `CBEXPORT: ABENDING PROGRAM` then `CALL 'CEE3ABD'` | task terminates |

`<name>` is the COBOL file name: `CUSTOMER-INPUT`, `ACCOUNT-INPUT`, `XREF-INPUT`, `TRANSACTION-INPUT`,
`CARD-INPUT`, `EXPORT-OUTPUT` (`CBEXPORT.cbl:200-240, 262-266, ...`).

Progress/statistics messages, all verbatim (`CBEXPORT.cbl:163-169, 245-255, 314-324, 378-388, 433-443,
498-508, 563-573`):

```
CBEXPORT: Starting Customer Data Export
CBEXPORT: Export Date: <YYYY-MM-DD>
CBEXPORT: Export Time: <HH:MM:SS>
CBEXPORT: Processing customer records
CBEXPORT: Customers exported: <9 digits>
CBEXPORT: Processing account records
CBEXPORT: Accounts exported: <9 digits>
CBEXPORT: Processing cross-reference records
CBEXPORT: Cross-references exported: <9 digits>
CBEXPORT: Processing transaction records
CBEXPORT: Transactions exported: <9 digits>
CBEXPORT: Processing card records
CBEXPORT: Cards exported: <9 digits>
CBEXPORT: Export completed
CBEXPORT: Customers Exported: <9 digits>
CBEXPORT: Accounts Exported: <9 digits>
CBEXPORT: XRefs Exported: <9 digits>
CBEXPORT: Transactions Exported: <9 digits>
CBEXPORT: Cards Exported: <9 digits>
CBEXPORT: Total Records Exported: <9 digits>
```

`<9 digits>` is the COBOL `DISPLAY` of a `PIC 9(9)` counter, i.e. zero-padded to 9 characters.

## 5. CBIMPORT control flow and business rules

```
0000-MAIN-PROCESSING (CBIMPORT.cbl:165)
  1000-INITIALIZE          -> date/time from FUNCTION CURRENT-DATE, 1100-OPEN-FILES
  2000-PROCESS-EXPORT-FILE -> read EXPFILE until EOF
       2200-PROCESS-RECORD-BY-TYPE (EVALUATE EXPORT-REC-TYPE)
            'C' -> 2300 customer   'A' -> 2400 account   'X' -> 2500 xref
            'T' -> 2600 transaction 'D' -> 2650 card     other -> 2700 unknown
  3000-VALIDATE-IMPORT     -> two DISPLAY statements only
  4000-FINALIZE            -> CLOSE all, DISPLAY statistics
  GOBACK
```

Rules:

1. **Dispatch is on `EXPORT-REC-TYPE` only** (`CBIMPORT.cbl:272-285`). No sequence checking, no timestamp
   checking, no branch/region checking, no duplicate detection, no referential validation between types.
2. **Every recognised record is written to its target file**, in input order, with `INITIALIZE` of the
   target record first (`CBIMPORT.cbl:290, 325, 354, 374, 404`). Field mapping is the inverse of CBEXPORT's,
   1:1 and lossless except where the target PIC is narrower (none of the mapped pairs are).
3. **`WS-TOTAL-RECORDS-READ` counts every record read**, including unknown types
   (`CBIMPORT.cbl:253`), while per-type counters count only records written.
4. **Unknown record type** (`2700-PROCESS-UNKNOWN-RECORD`, `CBIMPORT.cbl:425-434`) increments
   `WS-UNKNOWN-RECORD-TYPE-COUNT` and writes one 132-byte error line; processing continues. Error line
   layout (`CBIMPORT.cbl:152-160`): `ERR-TIMESTAMP X(26)` `|` `ERR-RECORD-TYPE X(1)` `|`
   `ERR-SEQUENCE 9(7)` `|` `ERR-MESSAGE X(50)` `FILLER X(43) VALUE SPACES`, message text
   `Unknown record type encountered`.
5. **Quirk — error timestamp is 21 characters in a 26-byte field.** `MOVE FUNCTION CURRENT-DATE TO
   ERR-TIMESTAMP` (`CBIMPORT.cbl:429`) moves `YYYYMMDDhhmmssnn±hhmm` (21 chars) left-justified into
   `X(26)`, leaving 5 trailing spaces. It is not the `YYYY-MM-DD HH:MM:SS.00` format the export record uses,
   and `WS-IMPORT-DATE`/`WS-IMPORT-TIME`, built for exactly that purpose in `1000-INITIALIZE`, are never
   used in the error record.
6. **Quirk — error sequence truncates.** `MOVE EXPORT-SEQUENCE-NUM TO ERR-SEQUENCE` moves a `9(9)` value
   into `PIC 9(7)`, so sequence numbers above 9 999 999 keep their **low-order 7 digits**.
7. **Quirk — the advertised validation does not exist.** `3000-VALIDATE-IMPORT` (`CBIMPORT.cbl:449-452`)
   unconditionally displays `CBIMPORT: Import validation completed` and
   `CBIMPORT: No validation errors detected`, even when error records were written. The program header
   comment promises "Validate data integrity using checksums"; no checksum field exists in `CVEXPORT.cpy`
   and no validation is performed.
8. **Quirk — a write failure on ERROUT does not abend.** `2750-WRITE-ERROR` (`CBIMPORT.cbl:437-446`)
   displays `ERROR: Writing error record, Status: ` and carries on, unlike every other write in the estate;
   and `WS-ERROR-RECORDS-WRITTEN` is incremented even when the write failed.

### Error paths (CBIMPORT)

| Condition | Message (verbatim) | Action |
|---|---|---|
| OPEN fails | `ERROR: Cannot open <name>, Status: ` + status | abend |
| READ status not `00`/`10` | `ERROR: Reading EXPORT-INPUT, Status: ` + status | abend |
| WRITE to a target file fails | `ERROR: Writing <customer\|account\|xref\|transaction\|card> record, Status: ` + status | abend |
| WRITE to ERROUT fails | `ERROR: Writing error record, Status: ` + status | continue |
| abend | `CBIMPORT: ABENDING PROGRAM` then `CALL 'CEE3ABD'` | task terminates |

Statistics messages (`CBIMPORT.cbl:176, 192-193, 451-452, 465-478`):

```
CBIMPORT: Starting Customer Data Import
CBIMPORT: Import Date: <YYYY-MM-DD>
CBIMPORT: Import Time: <HH:MM:SS>
CBIMPORT: Import validation completed
CBIMPORT: No validation errors detected
CBIMPORT: Import completed
CBIMPORT: Total Records Read: <9 digits>
CBIMPORT: Customers Imported: <9 digits>
CBIMPORT: Accounts Imported: <9 digits>
CBIMPORT: XRefs Imported: <9 digits>
CBIMPORT: Transactions Imported: <9 digits>
CBIMPORT: Cards Imported: <9 digits>
CBIMPORT: Errors Written: <9 digits>
CBIMPORT: Unknown Record Types: <9 digits>
```

## 6. Boundary B-12 — the hand-off

`app/jcl/FTPJCL.JCL` is a standalone `PGM=FTP` job with in-stream SYSIN: server `172.31.21.124`, user
`carddemousr`, password `ftpdemo1` (in clear), `ASCII`, `cd /ftpfolder`,
`PUT 'AWS.M2.CARDEMO.FTP.TEST' welcome.txt`, `QUIT` (`FTPJCL.JCL:28-38`). It transfers a **different**
dataset (`AWS.M2.CARDEMO.FTP.TEST`, note the misspelling of CARDEMO) than the export file, is not
referenced by `CBEXPORT.jcl` or `CBIMPORT.jcl`, and appears in no scheduler definition. So in the source
estate the export file's journey to the external system is *not* automated anywhere in this repo: the
programs write and read a dataset, and something outside the estate moves it. That is precisely why B-12
is registered as a boundary, and why the target implementation moves files through a configurable
directory and leaves the transport unimplemented (see the migration plan).

## 7. Contradictions with the inventory

- `docs/migration/CardDemo_inventory.md:143` lists FTPJCL as a job of S-16. FTPJCL neither runs nor
  references either program and moves an unrelated dataset (§6); it is documented as the boundary, not
  migrated as a job.
- The inventory describes the stream as "Export/Import branch records"
  (`CardDemo_inventory.md:98-99`); the programs export **the whole estate's five master files**, with the
  branch identity hard-coded to `0001`/`NORTH` rather than selected on (§4, rule 5).
