# CBACT03C — functional requirements (job READXREF)

Source: `app/cbl/CBACT03C.cbl`, `app/jcl/READXREF.jcl`, copybook `app/cpy/CVACT03Y.cpy`.
Function: read the card cross-reference file in key order and print each record. No derived files, no
validation.

Requirement ids are the stream ids from `../FileReadUtilities_functional_requirement.md`.

* **FR-X1** — job `READXREF`, one step `STEP05` (`READXREF.jcl:22`).
* **FR-G3** — CCXREF is browsed in ascending `XREF-CARD-NUM` (`CBACT03C.cbl:29-33`).
* **FR-G5** — SYSOUT opens with `START OF EXECUTION OF PROGRAM CBACT03C` and closes with
  `END OF EXECUTION OF PROGRAM CBACT03C` (`CBACT03C.cbl:71,85`).
* **FR-X2** — each record is printed **twice**, once from `1000-XREFFILE-GET-NEXT` (`CBACT03C.cbl:96`) and
  once from the main loop (`CBACT03C.cbl:78`). The two lines are identical and adjacent.
* **FR-X3/FR-G9** — the printed line is the 50-byte `CARD-XREF-RECORD` image: `XREF-CARD-NUM X(16)`,
  `XREF-CUST-ID 9(09)`, `XREF-ACCT-ID 9(11)`, `FILLER X(14)`.
* **FR-G6** — an empty CCXREF yields only the start and end lines (`CBACT03C.cbl:98-99`).
* **FR-X4/FR-G8/FR-G7** — `ERROR OPENING XREFFILE` (`:129`), `ERROR READING XREFFILE` (`:110`),
  `ERROR CLOSING XREFFILE` (`:147`), each followed by `FILE STATUS IS: NNNN<nnnn>`, `ABENDING PROGRAM` and
  an abend with code `0999`, culprit `CBACT03C` (`CBACT03C.cbl:154-174`).
