# CBCUS01C — functional requirements (job READCUST)

Source: `app/cbl/CBCUS01C.cbl`, `app/jcl/READCUST.jcl`, copybook `app/cpy/CVCUS01Y.cpy`.
Function: read CUSTDAT in key order and print each customer record. No derived files, no validation.

Requirement ids are the stream ids from `../FileReadUtilities_functional_requirement.md`.

* **FR-U1** — job `READCUST`, one step `STEP05` (`READCUST.jcl:21`).
* **FR-G3** — CUSTDAT is browsed in ascending `CUST-ID` (`CBCUS01C.cbl:29-33`).
* **FR-G5** — SYSOUT opens with `START OF EXECUTION OF PROGRAM CBCUS01C` and closes with
  `END OF EXECUTION OF PROGRAM CBCUS01C` (`CBCUS01C.cbl:71,85`).
* **FR-U2** — each customer is printed **twice**, once from `1000-CUSTFILE-GET-NEXT` (`CBCUS01C.cbl:96`)
  and once from the main loop (`CBCUS01C.cbl:78`).
* **FR-U3/FR-G9** — the printed line is the 500-byte `CUSTOMER-RECORD` image: `CUST-ID 9(09)`,
  first/middle/last name `X(25)` each, three address lines `X(50)`, `CUST-ADDR-STATE-CD X(02)`,
  `CUST-ADDR-COUNTRY-CD X(03)`, `CUST-ADDR-ZIP X(10)`, two phone numbers `X(15)`, `CUST-SSN 9(09)`,
  `CUST-GOVT-ISSUED-ID X(20)`, `CUST-DOB-YYYY-MM-DD X(10)`, `CUST-EFT-ACCOUNT-ID X(10)`,
  `CUST-PRI-CARD-HOLDER-IND X(01)`, `CUST-FICO-CREDIT-SCORE 9(03)`, `FILLER X(168)`.
* **FR-G6** — an empty CUSTDAT yields only the start and end lines (`CBCUS01C.cbl:98-99`).
* **FR-U4/FR-G8/FR-G7** — `ERROR OPENING CUSTFILE` (`:129`), `ERROR READING CUSTOMER FILE` (`:110`),
  `ERROR CLOSING CUSTOMER FILE` (`:147`), each followed by `FILE STATUS IS: NNNN<nnnn>`,
  `ABENDING PROGRAM` and an abend with code `0999`, culprit `CBCUS01C`
  (`CBCUS01C.cbl:154-174`; the paragraphs are named `Z-ABEND-PROGRAM`/`Z-DISPLAY-IO-STATUS` here rather
  than `9999-`/`9910-`, with identical behaviour).
