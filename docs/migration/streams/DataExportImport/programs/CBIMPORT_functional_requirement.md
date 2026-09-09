# CBIMPORT — functional requirements

Source: `app/cbl/CBIMPORT.cbl`, `app/jcl/CBIMPORT.jcl`, `app/cpy/CVEXPORT.cpy` and the target copybooks
`CVCUS01Y` (500), `CVACT01Y` (300), `CVACT03Y` (50), `CVTRA05Y` (350), `CVACT02Y` (150).

Encoding terms ("binary", "packed", "zoned", overpunch) are as defined in
`CBEXPORT_functional_requirement.md`.

## Job structure and input handling

| # | Requirement | Source |
|---|---|---|
| FR-I-01 | The import runs as job `CBIMPORT` with a single step (legacy STEP01, `PGM=CBIMPORT`) reading the export file produced by CBEXPORT. | `CBIMPORT.jcl:22-27` |
| FR-I-02 | The export file is read as fixed 500-byte records, in file order, until end of file. | `CBIMPORT.cbl:76-79, 250-256` |
| FR-I-03 | Every record read increments the total-read counter, whether or not its type is recognised. | `CBIMPORT.cbl:253` |
| FR-I-04 | Each record is dispatched on its type byte only: `C` customer, `A` account, `X` xref, `T` transaction, `D` card, anything else unknown. No sequence, timestamp, branch, region, duplicate or referential check is performed. | `CBIMPORT.cbl:272-285` |
| FR-I-05 | Recognised records are written to their target file in input order, one output record per input record. | `CBIMPORT.cbl:312, 341, 361, 391, 414` |
| FR-I-06 | A successful run ends with condition code 0; any abend path ends with condition code 12. | `CBIMPORT.cbl:480-483`, D-3 |

## Target record production

| # | Requirement | Source |
|---|---|---|
| FR-I-07 | **Customer** records are written to the customer output as 500-byte `CVCUS01Y` records: id zoned 9B @0; first/middle/last name @9/34/59; address lines @84/134/184; state @234; country @236; zip @239; phones @249/264; SSN zoned 9B @279; government id @288; date of birth @308; EFT account @318; primary-holder indicator @328; FICO zoned 3B @329; the remaining 168 bytes are spaces. | `CBIMPORT.cbl:293-312`, `CVCUS01Y.cpy` |
| FR-I-08 | **Account** records are written as 300-byte `CVACT01Y` records: id zoned 11B @0; status @11; current balance @12, credit limit @24, cash credit limit @36, current-cycle credit @78, current-cycle debit @90 as 12-byte zoned signed `S9(10)V99` with trailing overpunch; open/expiry/reissue dates @48/58/68; zip @102; group id @112; remaining 178 bytes spaces. | `CBIMPORT.cbl:328-341`, `CVACT01Y.cpy` |
| FR-I-09 | **Card xref** records are written as 50-byte `CVACT03Y` records: card number @0, customer id zoned 9B @16, account id zoned 11B @25, 14 trailing spaces. | `CBIMPORT.cbl:357-361`, `CVACT03Y.cpy` |
| FR-I-10 | **Transaction** records are written as 350-byte `CVTRA05Y` records: id @0; type @16; category zoned 4B @18; source @22; description @32; amount zoned signed 11B @132; merchant id zoned 9B @143; merchant name @152; city @202; zip @252; card number @262; original timestamp @278; processing timestamp @304; 20 trailing spaces. | `CBIMPORT.cbl:377-391`, `CVTRA05Y.cpy` |
| FR-I-11 | **Card** records are written as 150-byte `CVACT02Y` records: card number @0; account id zoned 11B @16; CVV zoned 3B @27; embossed name @30; expiry @80; active status @90; 59 trailing spaces. | `CBIMPORT.cbl:407-414`, `CVACT02Y.cpy` |
| FR-I-12 | Binary and packed export fields are decoded to their numeric value and re-emitted in the target's `DISPLAY` representation (zero-padded, sign as overpunch), so the output files are byte-compatible with the `app/data/ASCII/**` unload format. | `CVEXPORT.cpy` vs the target copybooks; `app/data/ASCII/acctdata.txt` |
| FR-I-13 | Negative amounts survive the decode/encode: a packed `D`-signed export amount becomes a negative overpunch in the target record. | `CVEXPORT.cpy:50-52, 71`, `CVACT01Y.cpy`, `CVTRA05Y.cpy` |

## Unknown records and the error file

| # | Requirement | Source |
|---|---|---|
| FR-I-14 | A record whose type is not `C`/`A`/`X`/`T`/`D` produces one 132-byte error record and processing continues with the next record; it is not written to any target file. | `CBIMPORT.cbl:283-284, 425-434` |
| FR-I-15 | The error record layout is: 26-byte timestamp, `\|`, 1-byte record type, `\|`, 7-digit zero-padded sequence number, `\|`, 50-byte message, 43 bytes of filler — 130 bytes, written into a 132-byte record, so two further spaces follow. | `CBIMPORT.cbl:107-108, 152-160, 437` |
| FR-I-16 | The error message text is `Unknown record type encountered`, space-padded to 50 bytes. | `CBIMPORT.cbl:432` |
| FR-I-17 | Quirk — the error timestamp is `FUNCTION CURRENT-DATE`, i.e. `YYYYMMDDhhmmssnn±hhmm` (21 characters) followed by 5 spaces, **not** the `YYYY-MM-DD HH:MM:SS.00` form used in the export record; the `WS-IMPORT-DATE` / `WS-IMPORT-TIME` fields built at initialisation are never used. | `CBIMPORT.cbl:178-188, 429` |
| FR-I-18 | Quirk — the sequence number is moved from a `9(9)` field into a `PIC 9(7)` field, so a sequence above 9 999 999 is reported by its **low-order 7 digits**. | `CBIMPORT.cbl:157, 431` |
| FR-I-19 | The unknown-type counter and the errors-written counter are both incremented for each unknown record. | `CBIMPORT.cbl:427, 446` |

## Operator output

| # | Requirement | Source |
|---|---|---|
| FR-I-20 | The run logs `CBIMPORT: Starting Customer Data Import`, `CBIMPORT: Import Date: <YYYY-MM-DD>` and `CBIMPORT: Import Time: <HH:MM:SS>` at the start. | `CBIMPORT.cbl:176-193` |
| FR-I-21 | Quirk — after processing, the run always logs `CBIMPORT: Import validation completed` and `CBIMPORT: No validation errors detected`, even when error records were written. No checksum or integrity validation is performed despite the program header's claim. | `CBIMPORT.cbl:449-452` |
| FR-I-22 | The run then logs `CBIMPORT: Import completed` and the counts `Total Records Read:`, `Customers Imported:`, `Accounts Imported:`, `XRefs Imported:`, `Transactions Imported:`, `Cards Imported:`, `Errors Written:`, `Unknown Record Types:`, each prefixed `CBIMPORT: ` and each count zero-padded to nine digits. | `CBIMPORT.cbl:465-478` |

## Failure paths

| # | Requirement | Source |
|---|---|---|
| FR-I-23 | A failure reading the export file logs `ERROR: Reading EXPORT-INPUT, Status: ` and abends. | `CBIMPORT.cbl:263-267` |
| FR-I-24 | A failure writing a target record logs `ERROR: Writing <customer\|account\|xref\|transaction\|card> record, Status: ` and abends. | `CBIMPORT.cbl:314-318, 343-347, 363-367, 393-397, 416-420` |
| FR-I-25 | Quirk — a failure writing an error record logs `ERROR: Writing error record, Status: ` and processing continues; the errors-written counter is still incremented. | `CBIMPORT.cbl:437-446` |
| FR-I-26 | The abend path logs `CBIMPORT: ABENDING PROGRAM` and terminates the step (boundary B-01). | `CBIMPORT.cbl:480-483` |

## Source-vs-JCL contradiction

| # | Observation | Source |
|---|---|---|
| FR-I-27 | The program opens a card output file (`CARDOUT`) that `CBIMPORT.jcl` never defines, so the shipped job cannot complete on z/OS — the `OPEN OUTPUT CARD-OUTPUT` fails and the program abends before reading anything. The migrated job resolves this in favour of the program: the card output file is produced, named like the other outputs. | `CBIMPORT.cbl:63-66, 233-238` vs `CBIMPORT.jcl:29-62` |
