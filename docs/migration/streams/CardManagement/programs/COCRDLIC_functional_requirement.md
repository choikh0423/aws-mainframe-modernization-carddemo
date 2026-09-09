# COCRDLIC (CCLI) — Functional Requirements

**Program:** `app/cbl/COCRDLIC.cbl` · **Mapset/map:** `COCRDLI` / `CCRDLIA`
(`app/bms/COCRDLI.bms`) · **File:** `CARDDAT` (browse), alternate path `CARDAIX` (declared,
unused) · **Stream FRs:** FR-L1…FR-L18.

## 1. Entry conditions
| Condition | Behaviour | Cite |
|-----------|-----------|------|
| `EIBCALEN = 0` | Fresh commarea; page 1; last page not shown | 315-325 |
| `CDEMO-PGM-ENTER` and `CDEMO-FROM-PROGRAM ≠ COCRDLIC` | Private commarea reset; restart at page 1 | 336-343 |
| `EIBCALEN > 0` and `CDEMO-FROM-PROGRAM = COCRDLIC` | Receive and edit the map | 357-362 |
| AID not in {ENTER, PF3, PF7, PF8} | Remapped to ENTER (no message) | 370-380 |

## 2. Screen (BMS `COCRDLI.bms`)
Title `List Credit Cards`. Search: `Account Number    :` (`ACCTSID`, 11),
`Credit Card Number:` (`CARDSID`, 16). Headings: `Select    `, `Account Number`,
` Card Number `, `Active `. Seven rows `CRDSELn`(1) `ACCTNOn`(11) `CRDNUMn`(16) `CRDSTSn`(1).
`INFOMSG`(45), `ERRMSG`(78). PF caption `  F3=Exit F7=Backward  F8=Forward`.

## 3. Requirements
| ID | Requirement | Cite |
|----|-------------|------|
| L-1 | A page holds at most 7 card rows (`WS-MAX-SCREEN-LINES = 7`) | 177-178, 1191 |
| L-2 | Rows are produced by `STARTBR … GTEQ` + `READNEXT` over `CARDDAT` in card-number order | 1129-1154 |
| L-3 | A blank / all-zero / LOW-VALUES account filter means "no account filter" | 1007-1013 |
| L-4 | A non-numeric account filter raises `ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER`, sets `INPUT-ERROR`, protects the select column and suppresses the browse | 1017-1025, 431-435 |
| L-5 | A blank / all-zero / LOW-VALUES card filter means "no card filter" | 1042-1048 |
| L-6 | A non-numeric card filter raises `CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER` only when the error line is still empty | 1052-1060 |
| L-7 | A valid account filter keeps only rows whose `CARD-ACCT-ID` equals it; a valid card filter keeps only the row whose `CARD-NUM` equals it (filtering happens after the read, not by key) | 1382-1405 |
| L-8 | PF8 pages forward only when a next page exists, and increments the page number | 486-497 |
| L-9 | PF7 pages backward only when not on page 1, and decrements the page number | 501-513 |
| L-10 | PF7 on page 1 redisplays page 1 with `NO PREVIOUS PAGES TO DISPLAY` | 444-454, 901-904 |
| L-11 | Reaching end-of-file while filling a page sets `NO MORE RECORDS TO SHOW` (if the error line is empty) and marks "no next page" | 1215-1221, 1233-1240 |
| L-12 | PF8 with no next page when the last page was already shown gives `NO MORE PAGES TO DISPLAY` | 905-909 |
| L-13 | Page 1 with zero rows sets `NO RECORDS FOUND FOR THIS SEARCH CONDITION.` in `WS-INFO-MSG`, but `1100-SCREEN-INIT` clears that field before `1400-SETUP-MESSAGE` runs, so the literal never reaches the screen: the operator sees `NO MORE RECORDS TO SHOW` on the error line and the normal info line (quirk Q-11) | 1241-1245, 668-670, 895-930 |
| L-14 | Otherwise the info line is `TYPE S FOR DETAIL, U TO UPDATE ANY RECORD` | 115-116, 910-921 |
| L-15 | Selection flags are only edited when no filter error was raised | 1075-1077 |
| L-16 | More than one `S`/`U` across the seven rows → `PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE`; the offending rows are marked | 1079-1095 |
| L-17 | A flag that is not `S`, `U`, space or LOW-VALUES → `INVALID ACTION CODE` | 1106-1113 |
| L-18 | `S` on the selected row transfers to `COCRDSLC` (`COCRDSL`/`CCRDSLA`) with `CDEMO-ACCT-ID`/`CDEMO-CARD-NUM` from that row | 517-541 |
| L-19 | `U` on the selected row transfers to `COCRDUPC` (`COCRDUP`/`CCRDUPA`) with the same values | 545-569 |
| L-20 | PF3 transfers to `COMEN01C` and sets the message `PF03 PRESSED.EXITING` | 384-406, 119-120 |
| L-21 | Any AID other than PF7/PF8 resets the "last page shown" flag | 410-414 |

## 4. Messages (verbatim)
`PF03 PRESSED.EXITING` · `NO RECORDS FOUND FOR THIS SEARCH CONDITION.` ·
`PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE` · `INVALID ACTION CODE` ·
`NO MORE PAGES TO DISPLAY` · `NO MORE RECORDS TO SHOW` · `NO PREVIOUS PAGES TO DISPLAY` ·
`TYPE S FOR DETAIL, U TO UPDATE ANY RECORD` ·
`ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER` ·
`CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER` ·
`File Error: <op> on <file> returned RESP <r>,RESP2 <r2>` (153-171).

## 5. Quirks preserved
Q-1 invalid PF key silently behaves as ENTER · Q-2 the last flagged row wins but multi-select is
still refused · Q-4 `NO MORE RECORDS TO SHOW` can appear on a full page · Q-5 a filter error
hides selection errors on the same submit.
