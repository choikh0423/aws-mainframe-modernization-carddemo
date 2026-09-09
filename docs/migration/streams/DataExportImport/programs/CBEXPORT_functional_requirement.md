# CBEXPORT — functional requirements

Source: `app/cbl/CBEXPORT.cbl`, `app/jcl/CBEXPORT.jcl`, `app/cpy/CVEXPORT.cpy`.
Every requirement is traceable to a source line and covered by a test (see the traceability table).

Offsets are 0-based byte offsets into the 500-byte export record. "Binary" is big-endian two's complement
(IBM `COMP`); "packed" is packed decimal (`COMP-3`) with the sign in the low nibble of the last byte —
`C` positive, `D` negative for signed PICs, `F` for unsigned; "zoned" is COBOL `DISPLAY`, signed values
carrying a trailing overpunch (`{`=+0 … `I`=+9, `}`=-0 … `R`=-9).

## Job and step structure

| # | Requirement | Source |
|---|---|---|
| FR-E-01 | The export runs as job `CBEXPORT` with two stages: a define/clear stage (legacy STEP01, `PGM=IDCAMS`) followed by the export pass (legacy STEP02, `PGM=CBEXPORT`). | `CBEXPORT.jcl:24-42` |
| FR-E-02 | The define/clear stage empties the export file, and succeeds when the file does not yet exist (the legacy `DELETE ... PURGE` is followed by `SET MAXCC = 0`). | `CBEXPORT.jcl:29-31` |
| FR-E-03 | Every run is a full re-export: the file produced by a run contains only that run's records. | `CBEXPORT.jcl:29-40` |
| FR-E-04 | A successful run ends with condition code 0; any abend path ends with condition code 12. | `CBEXPORT.cbl:576-579`, D-3 |

## Content and ordering

| # | Requirement | Source |
|---|---|---|
| FR-E-05 | Records are written in group order: all customers, then accounts, then card cross-references, then transactions, then cards. | `CBEXPORT.cbl:151-157` |
| FR-E-06 | Within a group, records follow ascending key order of the source file (`CUST-ID`, `ACCT-ID`, `XREF-CARD-NUM`, `TRAN-ID`, `CARD-NUM`). | `CBEXPORT.cbl:35-63` (`ACCESS MODE IS SEQUENTIAL` on INDEXED files) |
| FR-E-07 | Exactly one export record is written per source record; no record is filtered, merged or skipped. | `CBEXPORT.cbl:249-252, 318-321, 382-385, 437-440, 502-505` |
| FR-E-08 | Every record is exactly 500 bytes; the export file's length is 500 × record count. | `CBEXPORT.cbl:89-92` |

## Common record prefix

| # | Requirement | Source |
|---|---|---|
| FR-E-09 | Offset 0 holds the record type: `C` customer, `A` account, `X` xref, `T` transaction, `D` card. | `CBEXPORT.cbl:274, 343, 407, 462, 527` |
| FR-E-10 | Offset 1 holds a 26-byte timestamp `YYYY-MM-DD HH:MM:SS.00` — 22 characters left-justified, 4 trailing spaces. The `.00` is a literal; hundredths of a second are read but never used. | `CBEXPORT.cbl:175-195` |
| FR-E-11 | The timestamp is captured once per run and is identical on every record of that run. | `CBEXPORT.cbl:165, 275` |
| FR-E-12 | Offset 27 holds a 4-byte binary sequence number, starting at 1 and incremented by 1 for every record written, continuous across all five groups. | `CBEXPORT.cbl:276-277`, `CVEXPORT.cpy:16` |
| FR-E-13 | Offset 31 holds the branch id literal `0001` and offset 35 the region code literal `NORTH` on every record; neither is parameterised anywhere in the program or the JCL. | `CBEXPORT.cbl:278-279` |
| FR-E-14 | Bytes of the 460-byte data area not covered by the record type's mapped fields are spaces (the effect of `INITIALIZE EXPORT-RECORD` before each record). | `CBEXPORT.cbl:271, 340, 404, 459, 524` |

## Field mapping per record type

| # | Requirement | Source |
|---|---|---|
| FR-E-15 | **Customer (`C`)**, offsets relative to 40: id binary 4B @0; first/middle/last name 25B @4/29/54; address lines 1-3 50B @79/129/179; state 2B @229; country 3B @231; zip 10B @234; phone 1/2 15B @244/259; SSN zoned 9B @274; government id 20B @283; date of birth 10B @303; EFT account id 10B @313; primary-cardholder indicator 1B @323; FICO score packed unsigned 2B @324. | `CBEXPORT.cbl:282-299`, `CVEXPORT.cpy:24-42` |
| FR-E-16 | **Account (`A`)**: id zoned 11B @0; status 1B @11; current balance packed signed 7B @12; credit limit zoned signed 12B @19; cash credit limit packed signed 7B @31; open/expiry/reissue dates 10B @38/48/58; current-cycle credit zoned signed 12B @68; current-cycle debit binary signed 8B @80; zip 10B @88; group id 10B @98. | `CBEXPORT.cbl:351-362`, `CVEXPORT.cpy:47-60` |
| FR-E-17 | **Card xref (`X`)**: card number 16B @0; customer id zoned 9B @16; account id binary 8B @25. | `CBEXPORT.cbl:415-417`, `CVEXPORT.cpy:84-88` |
| FR-E-18 | **Transaction (`T`)**: id 16B @0; type code 2B @16; category code zoned 4B @18; source 10B @22; description 100B @32; amount packed signed 6B @132; merchant id binary 4B @138; merchant name 50B @142; merchant city 50B @192; merchant zip 10B @242; card number 16B @252; original timestamp 26B @268; processing timestamp 26B @294. | `CBEXPORT.cbl:470-482`, `CVEXPORT.cpy:65-79` |
| FR-E-19 | **Card (`D`)**: card number 16B @0; account id binary 8B @16; CVV binary 2B @24; embossed name 50B @26; expiry date 10B @76; active status 1B @86. | `CBEXPORT.cbl:535-540`, `CVEXPORT.cpy:93-100` |
| FR-E-20 | Character fields are left-justified and space-padded to their PIC length; values are copied without transformation, truncation or reformatting. | COBOL `MOVE` semantics, all mapping paragraphs |
| FR-E-21 | Negative amounts are representable and round-trip: packed fields carry sign nibble `D`, zoned fields the negative overpunch, binary fields two's complement. | `CVEXPORT.cpy:50-57, 71` |

## Operator output and failure paths

| # | Requirement | Source |
|---|---|---|
| FR-E-22 | The run logs, verbatim and in order: `CBEXPORT: Starting Customer Data Export`, `CBEXPORT: Export Date: <YYYY-MM-DD>`, `CBEXPORT: Export Time: <HH:MM:SS>`, then per group `CBEXPORT: Processing <…> records` / `CBEXPORT: <group> exported: <count>`. | `CBEXPORT.cbl:163-169, 245-255, 314-324, 378-388, 433-443, 498-508` |
| FR-E-23 | At the end the run logs `CBEXPORT: Export completed` followed by `Customers Exported:`, `Accounts Exported:`, `XRefs Exported:`, `Transactions Exported:`, `Cards Exported:` and `Total Records Exported:` lines, each prefixed `CBEXPORT: `. | `CBEXPORT.cbl:563-573` |
| FR-E-24 | Every count is rendered as the COBOL `DISPLAY` of a `PIC 9(9)` field, i.e. zero-padded to nine digits. | `CBEXPORT.cbl:139-144` |
| FR-E-25 | The total equals the sum of the five per-type counts. | `CBEXPORT.cbl:310, 373, 428, 493, 551` |
| FR-E-26 | A failure to read a source record logs `ERROR: Reading <FILE-NAME>, Status: ` with the file status and abends the run; a failure to write logs `ERROR: Writing export record, Status: ` and abends. | `CBEXPORT.cbl:262-266, 303-307` |
| FR-E-27 | The abend path logs `CBEXPORT: ABENDING PROGRAM` and terminates the step (the `CALL 'CEE3ABD'` seam, boundary B-01). | `CBEXPORT.cbl:576-579` |
