# CBACT02C — functional requirements (job READCARD)

Source: `app/cbl/CBACT02C.cbl`, `app/jcl/READCARD.jcl`, copybook `app/cpy/CVACT02Y.cpy`.
Function: read CARDDAT in key order and print each card record. No derived files, no validation.

Requirement ids are the stream ids from `../FileReadUtilities_functional_requirement.md`.

* **FR-C1** — job `READCARD`, one step `STEP05` (`READCARD.jcl:22`).
* **FR-G3** — CARDDAT is browsed in ascending `CARD-NUM` (`CBACT02C.cbl:29-33`).
* **FR-G5** — SYSOUT opens with `START OF EXECUTION OF PROGRAM CBACT02C` and closes with
  `END OF EXECUTION OF PROGRAM CBACT02C` (`CBACT02C.cbl:71,85`).
* **FR-C2** — each card is printed exactly once: the `DISPLAY CARD-RECORD` in `1000-CARDFILE-GET-NEXT` is
  commented out (`CBACT02C.cbl:96`), so only the main loop prints (`CBACT02C.cbl:78`). This is the one
  program of the stream that does not double-print.
* **FR-C3/FR-G9** — the printed line is the 150-byte `CARD-RECORD` image: `CARD-NUM X(16)`,
  `CARD-ACCT-ID 9(11)`, `CARD-CVV-CD 9(03)`, `CARD-EMBOSSED-NAME X(50)`, `CARD-EXPIRAION-DATE X(10)`,
  `CARD-ACTIVE-STATUS X(01)`, `FILLER X(59)`.
* **FR-G6** — an empty CARDDAT yields only the start and end lines; file status `10` ends the loop
  normally (`CBACT02C.cbl:98-99`).
* **FR-C4/FR-G8/FR-G7** — `ERROR OPENING CARDFILE` (`:129`), `ERROR READING CARDFILE` (`:110`),
  `ERROR CLOSING CARDFILE` (`:147`), each followed by `FILE STATUS IS: NNNN<nnnn>`, `ABENDING PROGRAM` and
  an abend with code `0999`, culprit `CBACT02C` (`CBACT02C.cbl:154-174`).
