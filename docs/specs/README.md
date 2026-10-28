# CardDemo — Extracted Specifications

Functional and technical specs pulled from the legacy CardDemo COBOL/CICS/JCL sources at `main` @ `7fd2344`. They use the same format as the Transaction Management spec that has already been signed off: each functional requirement (FR) is a trigger, then the visible result, then a `PROGRAM:lines` cite. Each spec also has business rules (BR), screen and I/O field specs, data access, a message catalogue with exact text, navigation, edge cases, and open questions.

## Spec set

| # | Spec | FRs | BRs | Open Qs |
|---|------|----:|----:|-------:|
| 00 | [Transaction Management (CT00/CT01/CT02)](../../migration/transaction-management/docs/TransactionManagement_functional_requirement.md) — existing, signed off | 32 | — | — |
| 01 | [01 — Sign-on, Main Menu and Admin Menu (CC00 / CM00 / CA00): Functional + Technical Specification](01-signon-menus.md) | 30 | 21 | 20 |
| 02 | [02 — Account Management (CAVW / CAUP) — Functional & Technical Specification](02-account-management.md) | 28 | 33 | 18 |
| 03 | [03 — Card Management (CCLI / CCDL / CCUP): Functional and Technical Specification](03-card-management.md) | 49 | 29 | 25 |
| 04 | [04 — User Security Administration (CU00 / CU01 / CU02 / CU03) — Functional & Technical Specification](04-user-management.md) | 53 | 27 | 21 |
| 05 | [05 — Bill Payment (CB00) and Transaction Reports (CR00): Functional and Technical Specification](05-billpay-reports.md) | 36 | 22 | 29 |
| 06 | [06 — Batch: Daily Transaction Validation/Posting, Interest & Fees, Transaction Report](06-batch-posting-interest.md) | 35 | 33 | 20 |
| 07 | [07 — Batch: Statements, Customer Import/Export, File-Read Utilities, Date/Wait Utilities, Job Scheduling](07-batch-statements-io-utilities.md) | 60 | 37 | 35 |
| 08 | [08 — Data Model: VSAM files, record layouts, keys, alternate indexes, reference data](08-data-model.md) | 30 | 30 | 22 |
| 09 | [09 — Optional Modules: Transaction Type (Db2), Pending Authorizations (IMS/Db2/MQ), VSAM-MQ Inquiry — Functional & Technical Specification](09-optional-modules.md) | 73 | 34 | 25 |
| | **Total (new)** | **394** | **266** | **215** |

## How the pieces fit

- **Online shell:** `CC00` sign-on (COSGN00C) routes to `CM00` main menu (COMEN01C) or `CA00` admin menu (COADM01C). From there, XCTL dispatches to each stream using the shared COMMAREA (`COCOM01Y`, `CDEMO-TO/FROM-PROGRAM`). See [01](01-signon-menus.md).
- **Online streams:** Account [02], Card [03], User admin [04], Bill pay + Reports [05], Transactions [00]. Optional Db2/IMS/MQ screens are in [09].
- **Batch:** daily posting, interest and the transaction report are in [06]. Statements, import/export, file utilities and the end-to-end run-book in scheduler order are in [07]. CR00 (CORPT00C) submits TRANREPT through the internal reader.
- **Data:** every VSAM file, key, AIX, record layout and reference-code table is in [08]. The other specs cite copybooks and do not repeat the layouts.

## Verification

- All 2,986 `PROGRAM:start-end` cites were checked automatically: each names a file that exists in `app/` and falls inside that file's line range.
- The quoted message texts were matched against the source. The only ones without an exact match are messages the code builds at run time with `STRING` (for example `'<name>' ' must be supplied.'`), and those are documented as templates.
- Behaviour was read from source only. Nothing was run, so these specs describe what the code says, not what was observed at run time.

## Open questions and apparent legacy bugs

Each spec's §9 lists these with cites. They are recorded as found and not fixed. A migration should decide case by case whether to keep the behaviour (parity) or fix it.

### 01 — Sign-on, Main Menu and Admin Menu (CC00 / CM00 / CA00): Functional + Technical Specification
- Q-01 Passwords are upper-cased before a plaintext compare, so they are case-insensitive; there is no hashing, lockout or audit (COSGN00C:135-136,223)
- Q-02 The PASSWD BMS field starts as '________' with FSET/DRK, so the 'Please enter Password ...' check may never fire, and a short password can keep trailing underscores (COSGN00.bms:175-180)
- Q-03 COSGN00C never loads DFHCOMMAREA; fields it does not set (TO-PROGRAM/TO-TRANID, customer/account/card) reach the menus uninitialised
- Q-04 A user type other than A/U goes to the main menu and skips the admin-only gate, which tests only CDEMO-USRTYP-USER (COMEN01C:136-137)
- Q-05 The main-menu admin-only gate is dormant: all COMEN02Y options are 'U', and option 8 used to be 'Transaction Add (Admin Only)' (commented out)
- Q-06 COMEN01C reads the table out of bounds: after an invalid option, CDEMO-MENU-OPT-USRTYPE(WS-OPTION) is still checked with index 0, >12 or non-numeric (COMEN01C:127-143)
- Q-07 The 'coming soon' message in COMEN01C keeps only the first word of the name and has no space before 'is' (dormant: no DUMMY entries)
- Q-08 The COADM01C 'This option is not installed ...' message leaves out the option name and is green; the main-menu equivalent names the option and is red
- Q-09 COMEN01C has no PGMIDERR handling except the COPAUS0C INQUIRE, so a missing option program abends the task
- Q-10 Admin options 5-6 (COTRTLIC/COTRTUPC) are not in the base CARDDEMO.CSD, so on a base install they show 'not installed'
- Q-11 COADM01C never re-checks CDEMO-USER-TYPE, and the CSD has RESSEC(NO)/CMDSEC(NO); admin access depends only on sign-on routing
- Q-12 Admins have no way to reach main-menu functions (accounts, cards, transactions, reports, bill pay); the intended role model needs confirming
- Q-13 On EIBCALEN=0 the menus move 'COSGN00C' to CDEMO-FROM-PROGRAM, not TO, and rely on TO-PROGRAM being low-values/spaces
- Q-14 All three programs ignore the RESP of RECEIVE MAP (e.g. MAPFAIL)
- Q-15 The tables declare more OCCURS than they populate (COMEN02Y 12 vs 11; COADM02Y 9 vs 6, with 10 BMS slots); a level-05 REDEFINES larger than its base is compiler-dependent
- Q-16 The sign-on CURTIME field is 9 bytes with INITIAL 'Ahh:mm:ss' (stray 'A'); the time value is 8 bytes
- Q-17 Dead or redundant code: duplicate FROM-PROGRAM move, unused USRSEC/CSUSR01Y in the menus, unused CCDA-THANK-YOU, a stray '.', an unused hidden 1-byte map field
- Q-18 The CSD PROGRAM(COSGN00C) definition has no LANGUAGE(COBOL), unlike the other programs
- Q-19 On an error re-send the password is echoed back to the terminal (dark field) because the output map REDEFINES the input map
- Q-20 Downstream PF3 targets are hard-coded ('COMEN01C'/'COADM01C') rather than read from CDEMO-FROM-PROGRAM

### 02 — Account Management (CAVW / CAUP) — Functional & Technical Specification
- Q-01: COACTUPC ACCT-UPDATE-RECORD omits ACCT-ADDR-ZIP (CVACT01Y), so the REWRITE puts GROUP-ID into the ADDR-ZIP bytes and corrupts both fields (COACTUPC:418-433, 4065-4071).
- Q-02: COULD-NOT-LOCK-CUST-FOR-UPDATE is not handled in 2000-DECIDE-ACTION, so the state falls to C and the user sees 'Changes committed to database' though nothing was written (COACTUPC:2606-2615, 3934-3942).
- Q-03: DID-NOT-FIND-ACCT-IN-ACCTDAT/CUSTDAT are never SET. After an account NOTFND the customer is still read; CAVW shows an unread account record and CAUP lets you edit an account that is not in the master.
- Q-04: After 'Record changed by some one else' the stored snapshot is not refreshed, so the screen shows stale values and every retry fails until F12, whose label is hidden in state S.
- Q-05: The phone all-blank test checks NUMA=SPACES where NUMC is meant (COACTUPC:2238-2239). It is hidden in practice because blank inputs are LOW-VALUES.
- Q-06: Customer id and Country are protected on screen, but Country is still edited as required alpha and CUST-UPDATE-ID is taken from screen input. Which customer fields should be maintainable?
- Q-07: There are no cross-field money/date rules (cash limit <= credit limit, non-negative limits, expiry >= open). Add them in the target or keep the legacy behaviour?
- Q-08: The state+ZIP combo table has military codes (AA/AE/AP) that the state list rejects, so those combos can never match. The VALID-PHONE-AREA-CODE and EASY-RECOG area-code tables are unused.
- Q-09: CAVW never shows 'Displaying details of given Account'. After a successful lookup the info line goes back to the input prompt.
- Q-10: Message inconsistencies: 'Account Filter must  be...' (double space) vs 'Account Number if supplied must be...'; 'Current Cycle Credit/Debit Limit' labels; 'some one'. Keep verbatim or correct?
- Q-11: PF3 always sets CDEMO-USRTYP-USER, so admin type is lost in the COMMAREA on exit.
- Q-12: The COCRDLI last-mapset check and the card-program literals suggest a deep-link from Card Management, but no caller passes an account and CDEMO-ACCT-ID is never pre-loaded.
- Q-13: Dead or unreachable code: 2000 WHEN state C, CAVW post-EVALUATE IF, duplicate 0000-MAIN-EXIT, 1230/1240 alphanum edits, SEND-LONG-TEXT, redundant reissue-date MOVE, duplicate 88 name DID-NOT-FIND-ACCT-IN-CARDXREF.
- Q-14: 9700-CHECK-CHANGE-IN-REC exits with GO TO 9600-WRITE-PROCESSING-EXIT from inside a nested PERFORM.
- Q-15: RECEIVE/SEND MAP RESP values are never checked, so a MAPFAIL after CLEAR/PA is handled as an empty ENTER.
- Q-16: Blank optional fields are stored as LOW-VALUES; an empty phone is stored as '(' LOW ')' LOW '-' LOW. The target needs a NULL-vs-spaces decision.
- Q-17: Only the first CXACAIX (non-unique AIX) hit is used for CDEMO-CARD-NUM, so accounts with several cards are not told apart.
- Q-18: Money input with more than 10 integer digits is silently truncated by NUMVAL-C COMPUTE (no ON SIZE ERROR).

### 03 — Card Management (CCLI / CCDL / CCUP): Functional and Technical Specification
- Q-01 Admin-vs-regular card filtering promised in COCRDLIC header (4-7) is not implemented; all users see all cards and COMMAREA account is never applied
- Q-02 Every XCTL forces CDEMO-USRTYP-USER and admins have no menu path to card screens (COSGN00C->COADM01C; COADM02Y has no card options)
- Q-03 Detail/update look up by card number only; account never cross-checked and COCRDUPC rewrites CARD-ACCT-ID with the screen account (1463)
- Q-04 COCRDUPC overwrites CVV from never-populated CCUP-NEW-CVV-CD (586, 1464-1465)
- Q-05 Hidden expiry day not validated against the new month; no future-date check
- Q-06 Month/year range checks via 88s on PIC 9 REDEFINES of PIC X input behave compiler-dependently for non-numeric input
- Q-07 List re-read after a selection error restarts at file start (WS-CARD-RID-CARDNUM not set) while the page number stays
- Q-08 Unfiltered look-ahead record falsely reports 'next page exists' when filters are active
- Q-09 A new filter on page>1 starts from the current page's first key and PF7 treats start-of-file as a file error
- Q-10 WS-CA-SCREEN-NUM PIC 9(1) wraps after page 9
- Q-11 First-card paging key goes stale on an empty filtered page
- Q-12 VIEW/UPDATE-REQUESTED-ON(0) evaluated with subscript 0 when nothing is selected (relies on NOSSRANGE)
- Q-13 Row-1 attribute handling differs from rows 2-7; BMS CRDSTP2-7 unused and CRDSTP1 missing
- Q-14 INITIALIZE CARDDEMO-COMMAREA on PF3 return / fresh entry wipes signed-on user context
- Q-15 Duplicate WHEN PFK07 AND CA-FIRST-PAGE in COCRDLIC
- Q-16 LIT-CCLISTMAP = 'CCRDSLA' (wrong map name) in COCRDSLC/COCRDUPC
- Q-17 COCRDUPC 2000-DECIDE-ACTION done-state branch and WHEN OTHER abend are unreachable
- Q-18 Any successful update stores the name upper-cased; case-only name change rejected as no change
- Q-19 CARDXREF (CVACT03Y) not used despite the Stream-Map; 9150 CARDAIX read is dead code; CVCUS01Y unused
- Q-20 Name check depends on LENGTH(TRIM(all spaces)) = 0 on the target runtime
- Q-21 DFHBMDAR moved into the EXPDAY colour byte
- Q-22 Concurrency check excludes CARD-ACCT-ID
- Q-23 Detail screen from list re-reads the same card on ENTER; no way to change search keys
- Q-24 '11/16 DIGIT' messages but only a NUMERIC class test, no length check
- Q-25 Inactive or expired cards remain editable; status/expiry never gate updates

### 04 — User Security Administration (CU00 / CU01 / CU02 / CU03) — Functional & Technical Specification
- Q-1 Sign-on upper-cases the user ID and password, but CU01/CU02 store them as typed, so users created in lower case can never sign on (COUSR01C:154-157, COSGN00C:132-136)
- Q-2 User Type is never checked against A/U; any character is accepted, and anything other than 'A' is treated as a regular user (COUSR01C:142-147, COUSR02C:204-209)
- Q-3 CU02 PF3 (Save&Exit) leaves the screen even when validation fails or nothing changed, so the error message is never seen (COUSR02C:111-119)
- Q-4 CU02 save goes on to REWRITE even when the re-READ failed, so 'User ID NOT found...' can be replaced by 'Unable to Update User...' (inference, COUSR02C:215-237)
- Q-5 CU03 PF5 issues DELETE even when the preceding READ UPDATE failed; the DELETE NOTFND branch is effectively unreachable (inference, COUSR03C:188-192, 305-336)
- Q-6 The CU03 delete-failure message reads 'Unable to Update User...', a copy-paste from update (COUSR03C:332)
- Q-7 CU02/CU03 PF5 act on whatever ID is in the field at that moment, not the one last fetched, so CU03 can delete a user that was never displayed (COUSR03C:174-192)
- Q-8 The delete 'confirm' is only pressing PF5 after a fetch; there is no Y/N confirmation field
- Q-9 There is no optimistic-lock or version check across the pseudo-conversation, so the last writer wins
- Q-10 Footer text and key handling disagree: COUSR01 shows F12=Exit but COUSR01C has no PF12 branch (it gives 'Invalid key pressed'); COUSR03C handles PF12 but the footer does not show it
- Q-11 STARTBR NOTFND shows the misleading 'You are at the top of the page...', then runs READNEXT (on PF8) and ENDBR (which has no RESP) with no active browse, which may cause INVREQ or an abend (inference, COUSR00C:597-606, 687-691)
- Q-12 The 'Invalid selection...' message can be overwritten by the end-of-file message from the page refresh that follows (COUSR00C:210-228, 634-638)
- Q-13 Backward paging that reaches the start of the file before filling 10 rows leaves the top rows blank and does not refresh USRID-FIRST (inference)
- Q-14 Browse order and search depend on EBCDIC collation; on an ASCII platform the list order will differ unless collation is pinned
- Q-15 CU02/CU03 depend on CDEMO-CU02/CU03-USR-SELECTED sitting at the same byte offset as the CU00 field; entered from the admin menu, those bytes are uninitialised working storage (inference)
- Q-16 CU00-CU03 do no authorisation check (CDEMO-USER-TYPE unused; RESSEC(NO)); nothing stops an admin deleting or demoting themselves or the last admin
- Q-17 Passwords are stored in clear text, and CU02 sends the current password to the terminal, hidden only by the DRK attribute
- Q-18 The success messages use DELIMITED BY SPACE, so an ID containing a space is cut short in the message
- Q-19 Dead code: WS-USER-DATA in COUSR00C, and WS-USR-MODIFIED in COUSR03C, which is reset but never tested
- Q-20 DUSRSECJ is a destructive full reload with no COND= checks and is not in the Control-M/CA-7 schedule; the seed password PASSWORD needs a decision
- Q-21 The CSD defines FILE USRSEC with RECORDFORMAT(V) while the cluster is fixed RECORDSIZE(80,80)

### 05 — Bill Payment (CB00) and Transaction Reports (CR00): Functional and Technical Specification
- Q-BP-01: Account balance is reduced and rewritten even when the TRANSACT WRITE fails (DUPREC/other) — no rollback.
- Q-BP-02: Payment proceeds after CXACAIX NOTFND/error, writing a transaction with stale/uninitialised card number.
- Q-BP-03: CXACAIX is NONUNIQUEKEY; accounts with >1 card likely get RESP=DUPKEY -> 'Unable to lookup XREF AIX file...' and flow continues (inference).
- Q-BP-04: TRAN-AMT S9(09)V99 is smaller than ACCT-CURR-BAL S9(10)V99 — balances >= 1e9 truncate.
- Q-BP-05: Acct ID input has no numeric/justification edit; short input yields NOTFND rather than a validation message.
- Q-BP-06: max-key+1 Tran ID generation has no enqueue — concurrent payments can collide.
- Q-BP-07: TRAN-SOURCE 'POS TERM' used for an online bill payment — intended?
- Q-BP-08: Payment does not update ACCT-CURR-CYC-CREDIT/DEBIT and bypasses daily-tran posting.
- Q-BP-09: CDEMO-CB00-TRN-SELECTED pre-fill path is dead code (no producer) and reads uninitialised storage.
- Q-BP-10: Success message contains a double space ('Payment successful.  Your Transaction ID is ...').
- Q-BP-11: Look-up-only path issues READ UPDATE, holding an unnecessary lock.
- Q-BP-12: Error after green success message keeps green colour (double SEND).
- Q-BP-13: CURBAL moved after error paths — redisplay contents unclear.
- Q-BP-14: Unused working storage (WS-TRAN-AMT, WS-TRAN-DATE, WS-USR-MODIFIED).
- Q-RP-01: CEEDAYS msg 2513 ('Unsupp. Range') is accepted as a valid date.
- Q-RP-02: No start <= end check for Custom range.
- Q-RP-03: NUMVAL-C on non-numeric input may make NOT NUMERIC checks unreachable and lets 00 through (inference).
- Q-RP-04: TRANREPT.prc begins with '//REPROC PROC' though invoked as PROC=TRANREPT.
- Q-RP-05: REPRO control member AWS.M2.CARDDEMO.CNTL(REPROCT) is not in the repo.
- Q-RP-06: Standalone TRANREPT.jcl has two steps named STEP05R and hard-coded 2022 dates.
- Q-RP-07: TRAN-CARD-NUM is X(16) but sorted as ZD.
- Q-RP-08: Generated JCL hard-codes CLASS/MSGCLASS/PROCLIB/job name; &SYSUID resolves to CICS region user (inference).
- Q-RP-09: No job-completion or job-number feedback to the user.
- Q-RP-10: CR00 PF3 always returns to COMEN01C, ignoring CDEMO-FROM-PROGRAM (unlike CB00).
- Q-RP-11: Stream-Map lists TCATBALF for CR00 but CORPT00C/TRANREPT never touch it.
- Q-RP-12: Unused declarations in CORPT00C (CVTRA05Y, WS-TRANSACT-FILE, WS-REC-COUNT) and paragraph typo WIRTE-JOBSUB-TDQ.
- Q-RP-13: CBTRN03C NEXT SENTENCE on out-of-range record exits the loop; EOF branch re-adds last amount (batch owner).
- Q-RP-14: TRANREPT is not in Control-M/CA7 schedules — on-demand only.
- Q-RP-15: CBADMCDJ.jcl CSD load defines COBIL00C but not CORPT00C/CB00/CR00; CARDDEMO.CSD defines all — which is authoritative?

### 06 — Batch: Daily Transaction Validation/Posting, Interest & Fees, Transaction Report
- CBACT04C never rewrites the final account: the ELSE PERFORM 1050-UPDATE-ACCOUNT branch inside PERFORM UNTIL END-OF-FILE='Y' can never run, so the last account gets no interest added to its balance and its cycle totals are not reset.
- CBACT04C does not reset or roll TCATBAL balances after charging interest, so rerunning INTCALC charges interest again on the same balances.
- INTCALC TRAN-ID is PARM-DATE plus a sequence that restarts at 000001 each run, and the PARM is hard-coded '2022071800', so reruns create duplicate keys in COMBTRAN's REPRO; PARM is not validated.
- Interest is charged on balances of either sign (credit balances get negative interest) and is truncated, not rounded.
- CBTRN03C's NEXT SENTENCE on an out-of-range date leaves the whole PERFORM loop, ending the report without totals, instead of skipping the record; standalone TRANREPT.jcl hard-codes SORT dates separately from the DATEPARM dataset.
- At EOF CBTRN03C adds the last record's TRAN-AMT a second time, so the final Page Total and Grand Total are overstated by that amount; on an empty file TRAN-AMT is uninitialised.
- CBTRN03C never prints the Account Total for the last card group.
- CBTRN03C's 'Account Total' breaks on card number, not account ID.
- The page break uses MOD(line-counter,20) on a counter that is never reset and also counts total/dash lines (+2 at a time), so a multiple of 20 can be skipped; no page-eject control is written.
- CBTRN02C opens the populated TRANSACT KSDS as OUTPUT (random access); the intended open mode and whether POSTTRAN must follow TRANBKP are unclear.
- CBTRN02C posting is not atomic: TCATBAL and the account are updated before the TRANSACT WRITE, which can still fail and abend.
- A CBTRN02C account REWRITE failure sets reason 109, but the transaction is still posted and not counted as a reject.
- When both 102 (overlimit) and 103 (expired) apply, only 103 is recorded.
- The over-limit formula CYC-CREDIT - CYC-DEBIT + amount has an unclear sign convention; cash credit limit and active status are not checked.
- Wrong messages/status in error paths: CBTRN01C's DALYTRAN close reports 'ERROR CLOSING CUSTOMER FILE', CBTRN02C's DALYREJS close shows XREFFILE-STATUS, and CBACT04C's DISCGRP open reports 'ERROR OPENING DALY REJECTS FILE'.
- CBTRN01C is an unscheduled prototype (no JCL, writes nothing) and runs one extra XREF/account lookup after EOF.
- TRANREPT.jcl has two steps named STEP05R, TRANREPT.prc's PROC statement is named REPROC, and the REPROC SYSIN member CNTL(REPROCT) is not in the repo.
- COMBTRAN reads TRANSACT.BKUP(0), which TRANBKP or TRANREPT may have created, and its REPRO assumes an emptied KSDS, but the monthly Control-M folder has no TRANBKP; TRANBKP deletes the TRANSACT AIX without redefining it.
- Which card CBACT04C puts on an interest transaction is undefined when an account has several cards (XREF alternate-key read).
- Interest type '01' / category '05' must exist in TRANTYPE/TRANCATG, or CBTRN03C abends on a missing key.

### 07 — Batch: Statements, Customer Import/Export, File-Read Utilities, Date/Wait Utilities, Job Scheduling
- CBSTM03A buffers at most 51 cards x 10 transactions with no bounds check (overflow risk)
- Statement name/address STRING DELIMITED BY ' ' truncates multi-word values such as the city
- HTML name DELIMITED BY '  ' shows only the first name when the middle name is blank; L23-NAME (50) truncates ST-NAME (75)
- HTMLFILE concatenates one full <html> document per card, so the file is not a single valid HTML document
- HTML statement omits the Total EXP line that the text statement prints
- An empty TRNXFILE abends CREASTMT (first READ returns 10)
- ST-CURR-BAL 9(9).99- drops the high-order digit of S9(10)V99; description truncated to 49 chars
- CREASTMT OUTREC copies 50 of 52 timestamp bytes, so the last 2 bytes of PROC-TS are lost
- CBSTM03B declares W/Z ops that are not implemented; unsupported ops return a stale status
- CEE3ABD is called without ABCODE/TIMING in CBSTM03A/CBEXPORT/CBIMPORT, so the abend code is undefined
- EXPORT.DATA KEYS(4 28) is off by one vs EXPORT-SEQUENCE-NUM at offset 27
- RECORD KEY points at a WORKING-STORAGE field (CVEXPORT), not the FD record
- CBIMPORT.jcl has no CARDOUT DD, so card import abends at open
- CBIMPORT 3000-VALIDATE-IMPORT is a stub despite the checksum claim in its header
- CBIMPORT ERR-SEQUENCE 9(7) truncates the 9-digit sequence; ERR-TIMESTAMP gets the raw CURRENT-DATE
- CBIMPORT outputs are NEW with no pre-delete, so reruns fail; nothing reloads the *.IMPORT files into VSAM
- Branch 0001 and region NORTH are hard-coded in CBEXPORT
- CBEXPORT/CBIMPORT are in neither scheduler
- CBACT01C copies cycle debit only when it is zero (sets 2525.00); non-zero values are never moved
- CBACT01C array/VB outputs hold hard-coded test values; COBDATFT error message is ignored
- CBACT03C and CBCUS01C display every record twice
- JCL typos: NOTIFY=&SYUID (READCUST), UNIT=SYSAD (READACCT, ESDSRRDS)
- DEFCUST deletes and defines different cluster names, KEYS(10 0) vs 9-byte CUST-ID, duplicate STEP05 names
- TRANREPT GDG defined twice: LIMIT(10) in REPTFILE vs LIMIT(5) SCRATCH in DEFGDGB
- Security: FTPJCL holds a plaintext host, user and password; ESDSRRDS holds plaintext user passwords
- INTRDRJ1/2 and FTPJCL use HLQ AWS.M2.CARDEMO (missing D)
- CA-7 triggers CLOSEFIL1/CLOSEFIL2, which have no JCL, and the optional IMS job CBPAUP0J
- Control-M MONTHLY folder has no monthly calendar rule; duplicate JOBISN=4
- Control-M vs CA-7 disagree on job coverage; which one is authoritative?
- CREASTMT has garbage in a comment field; HTMLFILE LRECL is 80 in STEP030 vs 100 in STEP040
- MVSWAIT writes X'FF' into the caller's save area for no stated purpose
- CSUTLDTC 88-level FC-INVALID-DATE actually means the date is valid
- CBADMCDJ duplicates MAPSET/PROGRAM definitions, references missing programs, CCDM/CCAD mismatch
- CBSTM03A relies on ALTER/GO TO and PSA/TCB/TIOT control-block walking (not portable)
- COBDATFT barely validates input (type-2 separator check is commented out)

### 08 — Data Model: VSAM files, record layouts, keys, alternate indexes, reference data
- Q-01 CBEXPORT.jcl defines EXPORT.DATA KEYS(4 28) but RECORD KEY EXPORT-SEQUENCE-NUM is at offset 27 (key mismatch)
- Q-02 CBIMPORT opens CARD-OUTPUT (DD CARDOUT) but CBIMPORT.jcl has no CARDOUT DD, so card import abends at open
- Q-03 acctdata.txt puts 'A000000000' in ACCT-ADDR-ZIP and leaves ACCT-GROUP-ID blank on all 50 rows, so interest always falls back to DEFAULT
- Q-04 No DISCGRP row for type 01 / cat 0005 (Interest Amount); such a TCATBAL row would abend after the DEFAULT fallback
- Q-05 Disclosure rate for 07/0001 differs: A000000000 = 15.00 vs DEFAULT = 0.00
- Q-06 TRANFILE closes/reopens CICS file CXACAIX (the CARDXREF AIX) while rebuilding the TRANSACT AIX; looks like copy-paste
- Q-07 DEFCUST deletes AWS.CCDA.CUSTDATA.CLUSTER but defines AWS.CUSTDATA.CLUSTER with KEYS(10 0) vs the 9-byte CUST-ID; nothing uses it
- Q-08 TRANREPT GDG defined twice: LIMIT(5) SCRATCH (DEFGDGB) vs LIMIT(10) without SCRATCH (REPTFILE)
- Q-09 TRANBKP deletes the TRANSACT AIX and never rebuilds it; TRANIDX must run afterwards
- Q-10 CBTRN02C opens the TRANSACT KSDS with OPEN OUTPUT; this only works on an empty KSDS (run-order dependency on TRANBKP)
- Q-11 COTRN01C (view) does READ UPDATE on TRANSACT with no REWRITE, holding a lock (CT01 scope)
- Q-12 CBACT03C and CBCUS01C display every record twice
- Q-13 CBACT01C writes fabricated values (CYC-DEBIT 2525.00 when zero, hard-coded array amounts)
- Q-14 CREASTMT OUTREC copies only 50 bytes at col 279, so the last 2 bytes of TRNX-PROC-TS are lost
- Q-15 COMMAREA CDEMO-CARD-NUM is 9(16) while file keys are X(16)
- Q-16 Passwords, CVV, SSN and government id stored in clear; all sample users have password PASSWORD; no dataset encryption
- Q-17 CUSTREC vs CVCUS01Y: byte-identical, but the DOB field name differs and CUSTREC has TABs
- Q-18 Unused artifacts: UNUSED1Y copybook, USRSEC ESDS/RRDS; OPENFIL/CLOSEFIL skip CARDDAT/CARDAIX/CUSTDAT
- Q-19 Misspelt ACCT-/CARD-EXPIRAION-DATE propagated into CVEXPORT and CBACT01C
- Q-20 LISTCAT REC-TOTALs (TCATBALF 100, TRANSACT 311) differ from the sample data; the DALYTRAN.PS.INIT source is not in app/data/ASCII
- Q-21 Sample FICO scores include 001; no domain validation at the file layer
- Q-22 CSD RECORDFORMAT(V) on all files while every cluster is fixed-length

### 09 — Optional Modules: Transaction Type (Db2), Pending Authorizations (IMS/Db2/MQ), VSAM-MQ Inquiry — Functional & Technical Specification
- CREADB21 JOB card has TYPRUN=SCAN, so it never creates or loads the Db2 objects (CREADB21.jcl:2)
- Seed type 06 description misspelt 'REVERAL' (DB2LTTYP.ctl:22)
- COTRTUPC/COTRTLIC set the VARCHAR length to 50, so descriptions are stored space-padded (COTRTUPC:1541-1542; COTRTLIC:1843-1844)
- COTRTLIC checks the filter-changed flags before they are set for the current input; that reset path is dead (COTRTLIC:960-976, 991-994)
- COTRTLIC backward read treats SQLCODE +100 as a Db2 error (COTRTLIC:1761-1790)
- COTRTLIC 'succeeded' 88-levels share LOW-VALUES with 'not requested' (COTRTLIC:411-418)
- COTRTLIC PF2 to CTTU sets user type USER and passes no selected type (COTRTLIC:634; COTRTUPC:366-374)
- BMS COTRTLI defines an unused 8th row whose names collide with the attribute-suffix convention (COTRTLI.bms:280-300)
- COTRTUPC: DATA-WAS-CHANGED is never set, F6=Add is not handled, and the -532 path does not set the failed state (COTRTUPC:1585, 1638-1649)
- COBTUPDT has no explicit COMMIT; its '9999-ABEND' only sets RC=4 and continues (COBTUPDT:230-233)
- COPAUA0C reply STRING pointer is never reset, so replies from the 2nd message on are corrupt or overflow (COPAUA0C:46, 720-731)
- COPAUA0C declined-amount total uses the previous message's PA-TRANSACTION-AMT (COPAUA0C:820-821, 885)
- COPAUA0C uses stale ACCOUNT-RECORD limits when the account read fails; reasons 4200/4300/5100/5200 are unreachable (COPAUA0C:810-811, 699-717)
- COPAUA0C MQGET is NO-SYNCPOINT, so a request can be lost on a critical failure (COPAUA0C:389)
- README vs code mismatches: PSB (PSBPAUTL vs PSBPAUTB), DB2ENTRY (DB201PLN vs AWS01PLN), MQ queue names and message formats
- COPAUS0C: not-found reads do not set the error flag (stale data, multiple SENDs); ACCSTAT never populated; -zzzz9.99 amount fields overflow; 20-page key table not bounds-checked
- CPVS/CPVD show PA-APPROVED-AMT, so declined auths display 0.00
- COPAUS2C AUTH_TS is a hybrid of the request date and the processing-time key; schema name is hard-coded CARDDEMO
- CBPAUP0C summary-delete test checks the approved count twice and never the declined count (CBPAUP0C:156)
- CBPAUP0C never REPLs summary adjustments; credit balance is never adjusted (CBPAUP0C:287-293)
- CBPAUP0J SYSIN expiry '00' purges everything; checkpoint-frequency type mismatch; checkpoint id never increments; YYDDD diff wrong across year boundaries
- CBPAUP0J STEPLIB is a placeholder XXXXXXXX.PROD.LOADLIB
- CDRD/CDRA: README formats and queues differ from code; request ReplyToQ ignored; RESP2 self-move; wrong queue name on close error; recursive termination; 'CICS RETREIVE' typo; 28-char message truncated to 25
- CDRA reply numerics are unedited zoned decimals; CSD descriptions say 'LIST CARDS'; no MQ trigger/connection resource definitions supplied
- PA-CASH-BALANCE forced to 0 on every approval (COPAUA0C:818)
