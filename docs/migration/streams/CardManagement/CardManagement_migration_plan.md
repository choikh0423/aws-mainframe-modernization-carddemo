# S-03 CardManagement — Migration plan

How CCLI / CCDL / CCUP were implemented on the consolidated Spring Boot + React target, the
boundary decisions taken, and the legacy-vs-migrated parity evidence.

Companion documents: `CardManagement_analysis.md` (source analysis, line cites),
`CardManagement_functional_requirement.md` (the numbered FRs and the requirement → test matrix),
`programs/COCRDLIC|COCRDSLC|COCRDUPC_functional_requirement.md` (per-program detail).

---

## 1. Scope delivered

| Legacy | Migrated to | Screen |
|---|---|---|
| CCLI `COCRDLIC` / `COCRDLI.bms` | `com.carddemo.card` — `CardListService`, `CardSelectionService`, `CardListController` | `frontend/src/pages/card/CardListPage.js` |
| CCDL `COCRDSLC` / `COCRDSL.bms` | `CardDetailService`, `CardDetailController` | `frontend/src/pages/card/CardDetailPage.js` |
| CCUP `COCRDUPC` / `COCRDUP.bms` | `CardUpdateService`, `CardUpdateValidator`, `CardUpdateController` | `frontend/src/pages/card/CardUpdatePage.js` |
| CDV1 `COCRDSEC` | **not migrated** — no source in the repo (inventory risk R-1 / boundary B-11) | — |

Data: the shared `cards` (CARDDAT / `CVACT02Y`) and `card_xref` (CCXREF / `CVACT03Y`) tables and their
existing entities `CardRecord` / `CardXrefRecord` in `com.carddemo.common.domain`. No entity, column or
existing migration was changed, and the stream needed no migration of its own — the `V300`–`V399` band
is unused. The pre-existing column typo `expiraion_date` was left alone (it is shared).

---

## 2. What the code looks like

```
backend/src/main/java/com/carddemo/card/
  controller/   CardListController      GET  /api/cards            (CCLI page: ENTER / PF7 / PF8)
                                        POST /api/cards/selection  (the seven row flags)
                CardDetailController    GET  /api/cards/detail     (CCDL)
                CardUpdateController    GET  /api/cards/update     (CCUP fetch, also PF12)
                                        POST /api/cards/update     (CCUP validate, and save when confirmed)
  service/      CardListService         browse + client-side filters + paging latches
                CardSelectionService    S/U edits and the XCTL target
                CardDetailService       key edits + read by card number
                CardUpdateService       fetch, compare, validate, lock, rewrite
  validator/    CardSearchKeyValidator  the shared account/card key edits (CCLI filters, CCDL/CCUP keys)
                CardUpdateValidator     name / status / expiry month / expiry year edits, in map order
  repository/   CardBrowseRepository    stream-private browse and SELECT … FOR UPDATE queries
  message/      CardManagementMessages  every legacy literal, verbatim
  exception/    CardValidationException, CardNotFoundException, CardUpdateFailedException,
                CardRecordChangedException + CardManagementExceptionHandler (stream-local @RestControllerAdvice)
  dto/          request/response records for the three screens
```

Frontend: three page components under `frontend/src/pages/card/`, one API helper module
`frontend/src/api/cards.js`, and exactly three changed lines in `frontend/src/routes/registry.js`
(the `element: null` placeholders for `COCRDLIC`, `COCRDSLC`, `COCRDUPC`).

---

## 3. Boundary decisions

| ID | Decision | Why |
|---|---|---|
| B-C1 | **COMMAREA → explicit page state on the wire.** The CCLI paging fields (`CA-FIRST-CARD-NUM`, `CA-LAST-CARD-NUM`, `CA-NEXT-PAGE-EXISTS`, `CA-LAST-PAGE-SHOWN`, `WS-CA-SCREEN-NUM`) travel as request parameters and are echoed in the response instead of living in a server session. | The target is stateless REST; the legacy COMMAREA is per-terminal state, and round-tripping it keeps the paging latches (FR-L7…FR-L11) behaviourally identical without inventing a session store. |
| B-C2 | **`CCARD-NEXT-PROG` / `CDEMO-TO-PROGRAM` XCTL → returned target descriptor.** `POST /api/cards/selection` returns `{program, tranId, mapset, map, acctId, cardNum}`; the React screen resolves the route through the shared registry (`pathForProgram`). | Keeps the routing literals traced to source (`COCRDSLC`/`CCDL`, `COCRDUPC`/`CCUP`) rather than hard-coding URLs in the UI, per the README's route contract. |
| B-C3 | **VSAM browse (`STARTBR`/`READNEXT`/`READPREV`) → keyset paging on `cards.card_num`.** Forward: `card_num >= :key` for the first page and `card_num > :key` to continue, `limit 8` to decide "is there a next page"; backward: `card_num < :key order by card_num desc, limit 7`, re-sorted ascending. | Reproduces the exact record sequence of a keyed browse (offset paging would not, because the filters are applied after the read — see B-C4). |
| B-C4 | **Filters stay client-side, exactly as in `9500-FILTER-RECORDS`.** Rows are read in card-number order and then tested for equality on account and card. The two filters are independent and ANDed, so a mismatched pair legitimately matches nothing (FR-L12). | The legacy program does not use the `CXACAIX` alternate index here; an indexed query would change which records are read and therefore the paging boundaries. |
| B-C5 | **Optimistic concurrency by field comparison, plus a pessimistic read for the write.** `POST /api/cards/update` with `confirmed=true` re-reads the row with `SELECT … FOR UPDATE` (`CardBrowseRepository#findForUpdate`) and compares CVV, embossed name, expiry year/month/day and status against the snapshot the screen was given, before rewriting. | This is the legacy sequence: `READ … UPDATE` followed by the field-by-field comparison in `9600-WRITE-PROCESSING` (`COCRDUPC:1453-1457`, `:1498-1519`). Mismatch → `Record changed by some one else. Please review` and the refreshed values (FR-U16); an unreadable row → `Could not lock record for update` (FR-U19). |
| B-C6 | **PF5 confirmation is a request flag, not server state.** The unconfirmed POST validates and answers `Changes validated.Press F5 to save` without writing; the same payload with `confirmed=true` writes. | The legacy `CCUP-CHANGES-OK-NOT-CONFIRMED` → `CCUP-CHANGES-OKAYED-AND-DONE` handshake lives in the COMMAREA; making it a flag keeps the two-step handshake (FR-U14, FR-U15) with no session. |
| B-C7 | **Expiry stays one `DATE` column.** The screens edit month and year only; the rewrite keeps the stored day (`YYYY-MM-DD`). | `cards.expiraion_date` is a shared column and the legacy map has no day field (quirk Q-8). |
| B-C8 | **Error contract:** validation → 400, unknown card → 404, lock/update failure and concurrent change → 409, all with `{message}` carrying the verbatim legacy literal; the concurrent-change body additionally carries the refreshed field values. The advice is `@RestControllerAdvice(assignableTypes = …)` over this stream's controllers only. | Lane rule: no shared exception handling was touched. |
| B-C9 | **CDV1 / `COCRDSEC` deferred, not stubbed.** | The CSD defines the transaction (`app/csd/CARDDEMO.CSD:388`) but the program is absent from the repo — inventory risk R-1, boundary register B-11. Nothing was invented. |
| B-C10 | **No frontend test harness was introduced.** The purely presentational requirements (FR-L18, FR-D2, FR-D11, FR-U2, FR-U18 — PF3 targets and the "please enter" prompts) are covered by the screen literals and review, not by an automated test. | The repository (including the reference stream `com.carddemo.transaction`) has no frontend test setup; adding one would be a shared-tooling change outside this lane. |

---

## 4. Fidelity notes

* Every message string is a constant in `CardManagementMessages`, copied character-for-character from
  the COBOL literals — including `   Displaying requested details` (three leading blanks) and
  `Changes validated.Press F5 to save` (no space after the full stop).
* Screen labels, field order, titles and PF-key captions are copied from the BMS maps; the page number
  is rendered plain because `PAGENO` is `LENGTH=3` (`COCRDLI.bms`), not zero-padded.
* Account numbers render as 11 digits zero-padded, card numbers as the stored 16 characters (FR-X1).
* Quirks preserved deliberately: Q-1 (invalid PF key behaves as ENTER), Q-3 (the account number is
  validated but the read is by card number only, so a mismatched pair still displays the card),
  Q-5 (a filter error hides an invalid selection flag on the same submit), Q-6 (`*` means blank),
  Q-7 (case-insensitive no-change detection), Q-8 (expiry day is never editable),
  Q-9 (status must be upper case), Q-10 (both keys mandatory), Q-11 (`NO RECORDS FOUND FOR THIS SEARCH
  CONDITION.` is dead code and never reaches the screen). Each is recorded in the analysis and in the FR.

---

## 5. Parity — legacy behaviour vs migrated behaviour

Fixtures: `app/data/ASCII/carddata.txt` and `app/data/ASCII/cardxref.txt`, loaded into `cards` /
`card_xref` by the foundation's generated seed `db/seed/R__seed_carddemo_data.sql`, so the tests run
against exactly the legacy data.

| Path | Legacy (`COCRDLIC`/`COCRDSLC`/`COCRDUPC`) | Migrated | Evidence |
|---|---|---|---|
| Open CCLI | 7 rows in card-number order from the top of CARDDAT, page 1, info line `TYPE S FOR DETAIL, U TO UPDATE ANY RECORD` | identical | `CardListServiceTest#frL1_…`, `CardManagementControllerTest#ccli_firstPageReturnsSevenRowsAndThePageState` |
| PF8 / PF7 | next / previous 7 rows, page number moves only when the page exists | identical | `CardListServiceTest#frL7_…`, `#frL10_…` |
| PF7 on page 1 | `NO PREVIOUS PAGES TO DISPLAY` | identical | `CardListServiceTest#frL11_…` |
| End of browse, then PF8 again | `NO MORE RECORDS TO SHOW`, then `NO MORE PAGES TO DISPLAY` | identical | `CardListServiceTest#frL8_frL9_…` |
| Account filter `00000000011` | only that account's cards | identical | `CardListServiceTest#frL3_…` |
| Bad filter | `ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER`, nothing listed | identical (HTTP 400 carrying the literal) | `CardListServiceTest#frL5_…`, `CardManagementControllerTest#ccli_aMalformedAccountFilterIsRejectedWithTheLegacyText` |
| `S` on one row | XCTL `COCRDSLC` (CCDL) with the row's keys | target descriptor with the same literals | `CardSelectionServiceTest#frL13_…` |
| Two flags | `PLEASE SELECT ONLY ONE RECORD TO VIEW OR UPDATE` | identical | `CardSelectionServiceTest#frL15_…` |
| CCDL valid pair | details + `   Displaying requested details` | identical | `CardDetailServiceTest#frD1_…` |
| CCDL wrong account, right card | card is displayed anyway (Q-3) | identical | `CardDetailServiceTest#frD9_…` |
| CCDL unknown card | `Did not find cards for this search condition` | identical (HTTP 404 carrying the literal) | `CardDetailServiceTest#frD8_…` |
| CCUP fetch | editable name/status/month/year, `Details of selected card shown above` | identical | `CardUpdateServiceTest#frU1_frU17_…` |
| CCUP resubmit unchanged | `No change detected with respect to values fetched.`, nothing written | identical | `CardUpdateServiceTest#frU8_…` |
| CCUP valid edit, no PF5 | `Changes validated.Press F5 to save`, nothing written | identical | `CardUpdateServiceTest#frU14_…` |
| CCUP PF5 | row rewritten (day, CVV, account preserved), `Changes committed to database` | identical | `CardUpdateServiceTest#frU15_…`, `CardManagementControllerTest#ccup_loadThenValidateThenSave` |
| CCUP after someone else changed the row | `Record changed by some one else. Please review` + refreshed values | identical (HTTP 409) | `CardUpdateServiceTest#frU16_…`, `CardManagementControllerTest#ccup_aRecordChangedMeanwhileIsRejectedWithTheRefreshedValues` |

---

## 6. Verification

```bash
cd migration/carddemo/backend && mvn -B test          # whole suite green, incl. 61 stream tests
source ~/.nvm/nvm.sh && nvm use 20
cd migration/carddemo/frontend && npm ci && CI=true npm run build
```

---

## 7. Deferred / raised

* **CDV1 / `COCRDSEC`** — deferred, source absent (R-1, B-11). No stub, no route, no placeholder logic.
* **Shared schema** — nothing missing for this stream; `cards` and `card_xref` cover CARDDAT and CCXREF.
  The `expiraion_date` spelling in the shared table is a foundation-level typo, left untouched.
* **Frontend tests** — none exist in the repository; see B-C10.
