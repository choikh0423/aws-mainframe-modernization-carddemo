# COCRDSLC (CCDL) — Functional Requirements

**Program:** `app/cbl/COCRDSLC.cbl` · **Mapset/map:** `COCRDSL` / `CCRDSLA`
(`app/bms/COCRDSL.bms`) · **File:** `CARDDAT` keyed read; `CARDAIX` paragraph present but never
performed · **Stream FRs:** FR-D1…FR-D11.

## 1. Entry conditions
| Condition | Behaviour | Cite |
|-----------|-----------|------|
| `EIBCALEN = 0`, or from `COMEN01C` without re-entry | Empty commarea, prompt screen | 268-279 |
| `CDEMO-PGM-ENTER` and `CDEMO-FROM-PROGRAM = COCRDLIC` | Keys taken from the commarea, card read and displayed without editing the keys | 339-348 |
| `CDEMO-PGM-ENTER` from elsewhere | Prompt screen | 349-356 |
| `CDEMO-PGM-REENTER` | Receive map → edit → (if clean) read → display | 357-371 |
| AID not in {ENTER, PF3} | Remapped to ENTER | 291-299 |

## 2. Screen (BMS `COCRDSL.bms`)
Title `View Credit Card Detail`. Fields in order: `Account Number    :` (11),
`Card Number       :` (16), `Name on card      :` (50), `Card Active Y/N   : ` (1),
`Expiry Date       : ` — month (2) and year (4), day protected/dark.
PF caption `ENTER=Search Cards  F3=Exit`.

## 3. Requirements
| ID | Requirement | Cite |
|----|-------------|------|
| D-1 | `*` or spaces in the account or card field is normalised to LOW-VALUES before editing | 615-627 |
| D-2 | Blank / all-zero account → `Account number not provided` | 651-661 |
| D-3 | Non-numeric account → `ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER` | 665-674 |
| D-4 | Blank / all-zero card → `Card number not provided` | 691-702 |
| D-5 | Non-numeric card → `CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER` | 706-715 |
| D-6 | Both keys blank → `No input received` (replaces the two "not provided" prompts) | 637-640 |
| D-7 | The card is read from `CARDDAT` **by card number only**; the account number is validated but not used in the read | 736-750 |
| D-8 | A successful read displays embossed name, expiry month, expiry year and active status, with info line `   Displaying requested details` | 474-485, 129-130 |
| D-9 | `NOTFND` → `Did not find cards for this search condition` | 755-761 |
| D-10 | Any other RESP → `File Error: READ     on CARDDAT   returned RESP <r>,RESP2 <r2>` | 762-772, 102-121 |
| D-11 | With no keys yet, the info line is `Please enter Account and Card Number` | 459-460, 131-132 |
| D-12 | PF3 returns to `CDEMO-FROM-PROGRAM`/`CDEMO-FROM-TRANID` when set, otherwise `COMEN01C`/`CM00`, and sets the message `PF03 pressed.Exiting              ` | 305-334, 136-137 |

## 4. Messages (verbatim)
`   Displaying requested details` · `Please enter Account and Card Number` ·
`PF03 pressed.Exiting              ` · `Account number not provided` ·
`Card number not provided` · `No input received` ·
`ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER` ·
`CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER` ·
`Did not find cards for this search condition` ·
`Did not find this account in cards database` (owned by the unreachable
`9150-GETCARD-BYACCT`, 779-806) · `Error reading Card Data File` · `File Error: …`.

## 5. Quirks preserved
Q-3 the account number never constrains the read, so a mismatched account still shows the card ·
Q-6 a lone `*` means "blank", not a wildcard · Q-10 both keys are mandatory although only the
card number is used.
