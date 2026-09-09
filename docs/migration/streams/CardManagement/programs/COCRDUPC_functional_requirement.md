# COCRDUPC (CCUP) — Functional Requirements

**Program:** `app/cbl/COCRDUPC.cbl` · **Mapset/map:** `COCRDUP` / `CCRDUPA`
(`app/bms/COCRDUP.bms`) · **File:** `CARDDAT` (`READ`, `READ UPDATE`, `REWRITE`) ·
**Stream FRs:** FR-U1…FR-U18.

## 1. Screen (BMS `COCRDUP.bms`)
Title `Update Credit Card Details`. Fields in order: `Account Number    :` (11),
`Card Number       :` (16), `Name on card      :` (50, editable),
`Card Active Y/N   : ` (1, editable), `Expiry Date       : ` — month (2, editable),
year (4, editable), day (2, protected/dark). PF captions `ENTER=Process F3=Exit` and
`F5=Save F12=Cancel`.

## 2. State machine (`CCUP-CHANGE-ACTION`, 948-1031)
```
DETAILS-NOT-FETCHED --(keys valid, card read)--> SHOW-DETAILS
SHOW-DETAILS --(no edits)-------------------->  NO-CHANGES-DETECTED
SHOW-DETAILS --(edits invalid)--------------->  CHANGES-NOT-OK
SHOW-DETAILS --(edits valid)----------------->  CHANGES-OK-NOT-CONFIRMED
CHANGES-OK-NOT-CONFIRMED --PF5-->  CHANGES-OKAYED-AND-DONE | CHANGES-FAILED | SHOW-DETAILS (record changed)
CHANGES-OK-NOT-CONFIRMED --PF12--> SHOW-DETAILS (re-read from file)
```

## 3. Requirements
| ID | Requirement | Cite |
|----|-------------|------|
| U-1 | `*` or spaces normalise to LOW-VALUES for account, card, name, status, expiry month and expiry year; the expiry **day** is taken as-is from the protected field | 585-636 |
| U-2 | Before details are fetched only the search keys are edited: blank/zero account → `Account number not provided`; non-numeric → `ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER` | 645-661, 721-756 |
| U-3 | Same for the card: `Card number not provided` / `CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER` | 762-799 |
| U-4 | Both keys blank → `No input received` | 656-659 |
| U-5 | The card is read by **card number only**; `NOTFND` → `Did not find cards for this search condition`, other RESP → `File Error: …` | 1376-1412 |
| U-6 | A successful read stores the fetched values (CVV, upper-cased embossed name, expiry year/month/day, status) as the "old" snapshot and shows `Details of selected card shown above` | 1343-1369, 157-158 |
| U-7 | With no keys yet, the message is `Please enter Account and Card Number` | 159-161 |
| U-8 | New vs old comparison is done on the upper-cased field group; equal → `No change detected with respect to values fetched.` and no field-level edits run | 680-693, 179-180 |
| U-9 | Name blank → `Card name not provided` | 811-819 |
| U-10 | Name containing anything other than A–Z, a–z and spaces → `Card name can only contain alphabets and spaces` | 822-837 |
| U-11 | Status blank or not `Y`/`N` → `Card Active Status must be Y or N` | 845-872, 91 |
| U-12 | Expiry month blank, non-numeric or outside 1–12 → `Card expiry month must be between 1 and 12` | 877-907, 92-95 |
| U-13 | Expiry year blank, non-numeric or outside 1950–2099 → `Invalid card expiry year` | 913-943, 96-99 |
| U-14 | All edits clean → `CCUP-CHANGES-OK-NOT-CONFIRMED` and `Changes validated.Press F5 to save`; nothing is written | 710-714, 163-166 |
| U-15 | PF5 performs `READ UPDATE`; failure to lock → `Could not lock record for update` | 1420-1449, 171-172 |
| U-16 | The locked record is compared with the fetched snapshot (CVV, upper-cased name, year, month, day, status); any difference → `Record changed by some one else. Please review`, the snapshot is refreshed from the file and nothing is written | 1453-1457, 1498-1519, 173-174 |
| U-17 | The rewritten record keeps card number, account id and CVV, takes the new name and status, and builds the expiry date as `<new year>-<new month>-<old day>` | 1461-1483 |
| U-18 | A failing `REWRITE` → `Update of record failed`; success → `Changes committed to database` | 1488-1492, 167-170 |
| U-19 | PF12 re-reads the card and returns to `CCUP-SHOW-DETAILS` (cancel) | 958-966 |
| U-20 | PF3 returns to `CDEMO-FROM-PROGRAM` when set, else `COMEN01C`, with `PF03 pressed.Exiting              ` | 367-558 |

## 4. Messages (verbatim)
`Details of selected card shown above` · `Please enter Account and Card Number` ·
`Update card details presented above.` · `Changes validated.Press F5 to save` ·
`Changes committed to database` · `Changes unsuccessful. Please try again` ·
`No change detected with respect to values fetched.` · `Card name not provided` ·
`Card name can only contain alphabets and spaces` · `Card Active Status must be Y or N` ·
`Card expiry month must be between 1 and 12` · `Invalid card expiry year` ·
`Could not lock record for update` · `Record changed by some one else. Please review` ·
`Update of record failed` · `Account number not provided` · `Card number not provided` ·
`No input received` · `ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER` ·
`CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER` ·
`Did not find cards for this search condition` · `PF03 pressed.Exiting              `.

## 5. Quirks preserved
Q-3 the account number is validated but not used in the read · Q-7 the no-change comparison is
case-insensitive, so a pure case change is "no change" · Q-8 the expiry day is never editable and
is carried into the rewritten date · Q-9 only upper-case `Y`/`N` are accepted for the status.
