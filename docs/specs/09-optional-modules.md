# 09 — Optional Modules: Transaction Type (Db2), Pending Authorizations (IMS/Db2/MQ), VSAM-MQ Inquiry — Functional & Technical Specification

**SOURCE:** `choikh0423/aws-mainframe-modernization-carddemo` @ `7fd2344fe22ea20b31dc8ae0d662d9379ec7d284`
**Scope:** `app/app-transaction-type-db2/**`, `app/app-authorization-ims-db2-mq/**`, `app/app-vsam-mq/**` (READMEs, COBOL, BMS, copybooks, DDL/DCL, DBD/PSB, CSD, JCL, CTL).
**Out of scope (linked, not repeated):** CT00/CT01/CT02 Transaction Management — see [`TransactionManagement_functional_requirement.md`](../../migration/transaction-management/docs/TransactionManagement_functional_requirement.md). Stream inventory: [`CardDemo-Stream-Map.md`](../../migration/transaction-management/docs/CardDemo-Stream-Map.md) rows #8, #9, #10.

| Module | Entry transactions / jobs | Programs | Infra dependencies |
|---|---|---|---|
| A. Transaction Type Mgmt | CICS **CTLI**, **CTTU**; batch **CREADB21**, **TRANEXTR**, **MNTTRDB2** | COTRTLIC, COTRTUPC, COBTUPDT (+ DSNTEP4, DSNTIAD, DSNTIAUL, IEBGENER) | **Db2** (plan CARDDEMO, DB2ENTRY CARDDEMO), CICS-Db2 attach |
| B. Pending Authorizations | CICS **CP00** (MQ-triggered), **CPVS**, **CPVD**; batch **CBPAUP0J**, **LOADPADB**, **UNLDPADB**, **UNLDGSAM**, **DBPAUTP0** | COPAUA0C, COPAUS0C, COPAUS1C, COPAUS2C, CBPAUP0C, PAUDBLOD, PAUDBUNL, DBUNLDGS | **IMS DB** (HIDAM DBPAUTP0/DBPAUTX0, PSB PSBPAUTB), **Db2** (AUTHFRDS, plan AWS01PLN), **MQ** (request/reply queues), VSAM (CCXREF, CXACAIX, ACCTDAT, CUSTDAT), TDQ CSSL |
| C. VSAM-MQ Inquiry | CICS **CDRD**, **CDRA** (MQ-triggered) | CODATE01, COACCT01 | **MQ** (trigger monitor, request/reply/error queues), VSAM ACCTDAT |

**Purpose.** These are self-contained extensions to base CardDemo. Module A maintains the transaction-type reference table in Db2 (online list/update/delete and add/edit, batch maintenance and a Db2→flat-file extract for the VSAM side). Module B is an MQ-driven credit-card authorization engine that decides approve/decline, persists pending authorizations in an IMS HIDAM database, lets an operator browse them and flag fraud into Db2, and purges expired ones in batch. Module C demonstrates MQ request/response services (system date, account inquiry from VSAM).

Notation: cites are `PROGRAM:start-end` against the files above (`.bms`, `.jcl`, `.ctl`, `.ddl`, `.dbd`, `.psb`, `.csd` files cited by file name). All line numbers are physical file lines.

---

## 1. Functional requirements

### A. Transaction Type Management (Db2)

#### CTLI — Transaction Type List / Update / Delete (COTRTLIC, map COTRTLI/CTRTLIA)
| ID | As a user I can… | Expected visible result | Source cite |
|---|---|---|---|
| FR-OTL1 | Open CTLI (direct or from Admin menu option) | Page 1 of up to 7 types (Type, Description) ordered by type code; header with title/date/time/tran/program and page number; info line "Type U to update, D to delete any record" | COTRTLIC:515-554, 738-762, 1293-1323, 1603-1726, 1550-1552 |
| FR-OTL2 | Enter a 2-digit Type Filter and press ENTER | List restricted to that exact TR_TYPE; paging resets | COTRTLIC:1096-1140, 338-352 |
| FR-OTL3 | Enter a Description Filter and press ENTER | List restricted to rows whose description contains the text (`LIKE '%text%'`, case-sensitive Db2 compare); paging resets | COTRTLIC:1142-1178, 347-350 |
| FR-OTL4 | Enter a non-numeric type filter | Filter field red, rows protected, "TYPE CODE FILTER,IF SUPPLIED MUST BE A 2 DIGIT NUMBER" | COTRTLIC:1111-1118, 1477-1480 |
| FR-OTL5 | Enter filters matching no row | Filters red, rows protected, "No Records found for these filter conditions" | COTRTLIC:1239-1271 |
| FR-OTL6 | Press PF8 | Next 7 rows; at end "No more pages for these search conditions" / "No more pages to display" | COTRTLIC:766-776, 1657-1705, 1536-1549 |
| FR-OTL7 | Press PF7 | Previous 7 rows; on page 1 "No previous pages to display" | COTRTLIC:721-734, 780-790, 1532-1535, 1727-1799 |
| FR-OTL8 | Type `D` beside one row and press ENTER | Row highlighted (neutral), "Delete HIGHLIGHTED row ? Press F10 to confirm" | COTRTLIC:794-806, 1348-1354, 1514-1522 |
| FR-OTL9 | Press PF10 after FR-OTL8 (no filter/selection change) | Row deleted, committed; "HIGHLIGHTED row deleted.Hit Enter to continue"; next interaction restarts at page 1 | COTRTLIC:666-678, 810-834, 1896-1913, 1507-1508 |
| FR-OTL10 | PF10 delete of a type that still has categories | "Please delete associated child records first: SQLCODE:…" (Db2 RI, -532) | COTRTLIC:1914-1925; DB2CREAT.ctl:97-99 |
| FR-OTL11 | Type `U` beside one row, edit its description, press ENTER | Description field unprotected/highlighted, "Update HIGHLIGHTED row. Press F10 to save" | COTRTLIC:838-850, 1356-1370, 1523-1531 |
| FR-OTL12 | Press PF10 after FR-OTL11 | Description updated and committed, page re-read; "HIGHLIGHTED row was updated" | COTRTLIC:854-868, 1837-1860, 1509-1510 |
| FR-OTL13 | Select `U` but leave description unchanged (case-insensitive) | "No change detected with respect to database values." | COTRTLIC:1060-1073 |
| FR-OTL14 | Blank the description or use non-alphanumeric characters on a `U` row | Field red, `*` shown if blank; "Transaction Desc must be supplied." / "Transaction Desc can have numbers or alphabets only." | COTRTLIC:1083-1089, 1181-1237, 1414-1415 |
| FR-OTL15 | Select more than one row | Offending rows red; "Please select only 1 action" | COTRTLIC:1017-1052, 1343-1346 |
| FR-OTL16 | Enter a selection code other than D/U | Row red; "Action code selected is invalid" | COTRTLIC:1034-1038 |
| FR-OTL17 | Press PF2 | Transfer to CTTU (COTRTUPC) add/edit screen | COTRTLIC:630-652 |
| FR-OTL18 | Press PF3 | Return to Admin menu (COADM01C) or calling program | COTRTLIC:591-625 |
| FR-OTL19 | Press any other key | Treated as ENTER (screen refresh) | COTRTLIC:574-587 |
| FR-OTL20 | Open CTLI while Db2 is unavailable | Full-screen text "Db2 access failure.  SQLCODE:… <DSNTIAC text>" and task ends | COTRTLIC:684-691, 2085-2095; CSDB2RPY:21-48 |

#### CTTU — Transaction Type Add / Edit / Delete (COTRTUPC, map COTRTUP/CTRTUPA)
| ID | As a user I can… | Expected visible result | Source cite |
|---|---|---|---|
| FR-OTU1 | Open CTTU (from Admin menu, CTLI PF2, or direct) | Empty form, cursor on Type, info "Enter transaction type to be maintained" | COTRTUPC:366-381, 465-478, 1210-1218 |
| FR-OTU2 | Enter an existing type code and press ENTER | Code + description shown, description editable; info "Update transaction type details shown." ; F4/F12 shown | COTRTUPC:978-997, 1469-1527, 1226-1228, 1397-1422 |
| FR-OTU3 | Enter a type not in the table | "No record found for this key in database" + info "Press F05 to add. F12 to cancel" | COTRTUPC:1489-1494, 1219-1220 |
| FR-OTU4 | Press PF5 after FR-OTU3 | Form opens for new record: info "Enter new transaction type details." | COTRTUPC:503-508, 1235-1236 |
| FR-OTU5 | Change the description and press ENTER | Validated; info "Changes validated.Press F5 to save" ; fields protected | COTRTUPC:743-776, 1023-1030, 1237-1238, 1291-1295 |
| FR-OTU6 | Press PF5 after FR-OTU5 | Row updated (or inserted if missing) and committed; info "Changes committed to database" | COTRTUPC:514-520, 1531-1623, 1239-1240 |
| FR-OTU7 | Press PF4 on a displayed record | Info "Delete this record ? Press F4 to confirm" | COTRTUPC:493-498, 1229-1230 |
| FR-OTU8 | Press PF4 again | Row deleted and committed; info "Delete successful." | COTRTUPC:482-489, 1624-1637, 1233-1234 |
| FR-OTU9 | Delete a type with child categories | "Please delete associated child records first:SQLCODE :…" | COTRTUPC:1638-1649 |
| FR-OTU10 | Press PF12 during delete-confirm / pending-save | "Delete was cancelled" / "Update was cancelled", original values restored | COTRTUPC:524-533, 988-1010 |
| FR-OTU11 | Press PF12 on displayed / not-found / new record | Back to empty search form | COTRTUPC:405-419, 465-478 |
| FR-OTU12 | Leave type blank / non-numeric / `00` | "Tran Type code must be supplied." / "…must be numeric." / "…must not be zero." ; `*` in red when blank | COTRTUPC:820-847, 907-976, 1331-1340 |
| FR-OTU13 | Enter a blank or non-alphanumeric description | "Transaction Desc must be supplied." / "Transaction Desc can have numbers or alphabets only." | COTRTUPC:758-764, 849-905 |
| FR-OTU14 | Re-enter the same description (case-insensitive) | "No change detected with respect to values fetched." | COTRTUPC:783-816 |
| FR-OTU15 | Press a key not valid in the current state | "Invalid key pressed" | COTRTUPC:577-618, 539-542 |
| FR-OTU16 | Press PF3 | Commit and return to caller or Admin menu (COADM01C) | COTRTUPC:429-460 |

#### Batch — Transaction Type
| ID | Given input… the job… | Expected output | Source cite |
|---|---|---|---|
| FR-OTB1 | Given CNTL members DB2FREE/DB2CREAT/DB2LTTYP/DB2LTCAT, **CREADB21** frees plans, creates database CARDDEMO, tablespaces, both tables, indexes, FK, grants, and loads seed rows | 7 types, 18 type/category rows | CREADB21.jcl:40-84; DB2CREAT.ctl; DB2LTTYP.ctl:15-24; DB2LTCAT.ctl:15-38 |
| FR-OTB2 | Given a sequential INPFILE of 53-byte records (action, type, description), **MNTTRDB2/COBTUPDT** applies A=insert, U=update, D=delete, `*`=comment per record | SYSOUT lines per record ("PROCESSING   …", "RECORD INSERTED SUCCESSFULLY" etc.), RC=4 on any error | MNTTRDB2.jcl:21-30; COBTUPDT:31-46, 91-233 |
| FR-OTB3 | Given the Db2 tables, **TRANEXTR** backs up previous extracts to GDGs, deletes them and re-extracts both tables to 60-byte flat files | `&HLQ..TRANTYPE.PS` (type 2 + desc 50 + `00000000`), `&HLQ..TRANCATG.PS` (type 2 + cat 4 + desc 50 + `0000`), ordered by key | TRANEXTR.jcl:31-122 |

### B. Pending Authorizations (IMS / Db2 / MQ)

#### CP00 — Authorization request processor (COPAUA0C, MQ-triggered, no screen)
| ID | Given input… the program… | Expected output | Source cite |
|---|---|---|---|
| FR-OPA1 | Given a trigger on the request queue, CP00 opens the queue and processes messages until empty or >500 processed | One reply per request, one SYNCPOINT per request | COPAUA0C:220-247, 323-345, 40 |
| FR-OPA2 | Given a CSV request (18 fields, §6.3), it parses fields and amount | Parsed request (`PENDING-AUTH-REQUEST`) | COPAUA0C:351-380; CCPAURQY:19-36 |
| FR-OPA3 | Given a card in CCXREF with sufficient available credit | Reply `…,00,0000,<amount>,` (approved), detail segment with match status `P` | COPAUA0C:657-731, 902-906 |
| FR-OPA4 | Given amount > available credit | Reply resp code `05`, reason `4100`, approved amount 0; detail status `D` | COPAUA0C:665-689, 705-706 |
| FR-OPA5 | Given card not in CCXREF / account not in ACCTDAT | Reply `05` / `3100`; warning logged to CSSL; nothing written to IMS when card not in XREF | COPAUA0C:472-562, 463-465, 699-704 |
| FR-OPA6 | Given any processed request with card found | IMS summary (PAUTSUM0) inserted or replaced with counters/balances; detail (PAUTDTL1) inserted under it | COPAUA0C:786-933 |
| FR-OPA7 | Given an MQ/IMS/CICS critical failure | Error record to TDQ CSSL, PSB terminated, request queue closed, task returns | COPAUA0C:983-1023 |

#### CPVS — Authorization summary (COPAUS0C, map COPAU00/COPAU0A)
| ID | As a user I can… | Expected visible result | Source cite |
|---|---|---|---|
| FR-OPS1 | Open CPVS from the main menu with an account in context | Account/customer header (name, cust id, address, phone, credit/cash limit, approval/decline counts and amounts, credit/cash balance) and first 5 pending authorizations (Tran ID, date, time, type, A/D, status, amount) | COPAUS0C:202-219, 342-358, 750-808, 415-454, 522-605 |
| FR-OPS2 | Enter an Account Id and press ENTER | Same as FR-OPS1 for that account | COPAUS0C:261-338 |
| FR-OPS3 | Press ENTER with blank / non-numeric account | "Please enter Acct Id..." / "Acct Id must be Numeric ..." | COPAUS0C:264-280 |
| FR-OPS4 | Enter an account with no XREF/ACCT/CUST record | "Account:<id> not found in XREF file. Resp:… Reas:…" (or ACCT / "Customer:… not found in CUST file…") | COPAUS0C:812-963 |
| FR-OPS5 | Type `S`/`s` beside a row and press ENTER | Transfer to the details screen (CPVD) for that authorization | COPAUS0C:285-325 |
| FR-OPS6 | Type any other selection value | "Invalid selection. Valid value is S" | COPAUS0C:326-330 |
| FR-OPS7 | Press PF8 / PF7 | Next / previous 5 authorizations; at limits "You are already at the bottom of the page..." / "You are already at the top of the page..." | COPAUS0C:362-412 |
| FR-OPS8 | Press PF3 | Return to main menu COMEN01C | COPAUS0C:235-238, 665-677 |
| FR-OPS9 | Press another key | Invalid-key message (`CCDA-MSG-INVALID-KEY`) | COPAUS0C:245-249 |

#### CPVD — Authorization details / fraud marking (COPAUS1C + COPAUS2C, map COPAU01/COPAU1A)
| ID | As a user I can… | Expected visible result | Source cite |
|---|---|---|---|
| FR-OPD1 | Arrive from CPVS with a selected authorization | Card #, auth date/time, response A (green)/D (red), reason code + description, auth code, amount, POS mode, source, MCC, card expiry MM/YY, auth type, tran id, match status, fraud status, merchant name/id/city/state/zip | COPAUS1C:173-177, 208-228, 291-358 |
| FR-OPD2 | Press PF8 | Next authorization of the same account; at end "Already at the last Authorization..." | COPAUS1C:268-289 |
| FR-OPD3 | Press PF5 on a non-fraud authorization | Fraud row inserted into Db2 AUTHFRDS, IMS detail updated with fraud `F` + report date; "AUTH MARKED FRAUD..." | COPAUS1C:230-266, 520-538; COPAUS2C:89-220 |
| FR-OPD4 | Press PF5 on an authorization already marked fraud | Db2 row updated to `R`, IMS detail `R`; "AUTH FRAUD REMOVED..." | COPAUS1C:236-238, 534-535; COPAUS2C:203-204, 221-244 |
| FR-OPD5 | Fraud marking fails in Db2 or IMS | Message from COPAUS2C (" SYSTEM ERROR DB2: CODE:…") or " System error while FRAUD Tagging, ROLLBACK||…"; unit of work rolled back | COPAUS1C:256-261, 539-550; COPAUS2C:206-214 |
| FR-OPD6 | Press PF3 | Return to CPVS | COPAUS1C:184-186, 360-370 |
| FR-OPD7 | Press another key | Detail redisplayed with invalid-key message | COPAUS1C:193-197 |

#### Batch — Pending Authorizations
| ID | Given input… the job… | Expected output | Source cite |
|---|---|---|---|
| FR-OPB1 | Given SYSIN `expiry-days,chkp-freq,chkp-display-freq,debug` (JCL: `00,00001,00001,Y`), **CBPAUP0J/CBPAUP0C** walks every summary and deletes details older than expiry-days, then deletes summaries with no approved auths | Detail/summary DLETs, checkpoints, totals on SYSOUT | CBPAUP0J.jcl:24-37; CBPAUP0C:136-180, 277-349 |
| FR-OPB2 | **UNLDPADB/PAUDBUNL** unloads the IMS DB | ROOT.FILEO (LRECL 100) and CHILD.FILEO (LRECL 206: root key + detail) | UNLDPADB.JCL:25-59; PAUDBUNL:157-285 |
| FR-OPB3 | **LOADPADB/PAUDBLOD** reloads from those files | ISRT root; GU root by key then ISRT child; duplicates ("II") tolerated | LOADPADB.JCL:26-38; PAUDBLOD:169-338 |
| FR-OPB4 | **UNLDGSAM/DBUNLDGS** unloads the IMS DB to GSAM datasets | PAUTDB.ROOT.GSAM / PAUTDB.CHILD.GSAM | UNLDGSAM.JCL:26-43; DBUNLDGS:164-316; DLIGSAMP.PSB:18-23 |
| FR-OPB5 | **DBPAUTP0** job deletes and recreates an HD reorg unload (DFSURGU0) of DBPAUTP0 | `AWS.M2.CARDDEMO.IMSDATA.DBPAUTP0` (VB 27990) | DBPAUTP0.jcl:7-45 |

### C. VSAM-MQ Inquiry
| ID | Given input… the program… | Expected output | Source cite |
|---|---|---|---|
| FR-OMQ1 | Given any message on the triggered queue, **CDRD/CODATE01** replies with the current date/time | Reply text `SYSTEM DATE : MM-DD-YYYYSYSTEM TIME : HH:MM:SS` on `CARD.DEMO.REPLY.DATE`, correlated by request MsgId/CorrelId | CODATE01:140-167, 339-403 |
| FR-OMQ2 | Given a request `INQA` + 11-digit account id, **CDRA/COACCT01** reads ACCTDAT | Labelled account record (§6.4) on `CARD.DEMO.REPLY.ACCT` | COACCT01:390-427, 130-169 |
| FR-OMQ3 | Given an unknown account | Reply "INVALID REQUEST PARAMETERS ACCT ID : <key>" | COACCT01:428-435 |
| FR-OMQ4 | Given a function other than `INQA` or key 0 | Reply "INVALID REQUEST PARAMETERS ACCT ID : <key>FUNCTION : <func>" | COACCT01:448-456 |
| FR-OMQ5 | Given MQ/CICS/VSAM errors | Error record (MQ-ERR-DISPLAY) put to `CARD.DEMO.ERROR`; task ends | CODATE01:405-454; COACCT01:437-445, 501-550 |
| FR-OMQ6 | Given an empty queue | Program waits 5 s, then ends (SYNCPOINT between messages) | CODATE01:274-337; COACCT01:325-388 |

---

## 2. Screen / interface specification

### 2.1 COTRTLI / CTRTLIA (CTLI)
Header (all maps in scope share it): Tran (1,7 L4), TITLE01/02 (1,21 / 2,21 L40), Date (1,71 L8 mm/dd/yy), Prog (2,7 L8), Time (2,71 L8 hh:mm:ss) — COTRTLI.bms:29-74; filled COTRTLIC:1293-1315.

| Name | BMS field | Pos | Len | I/O | Attributes | Edit rules / source |
|---|---|---|---|---|---|---|
| Title | literal | 4,28 | 25 | O | "Maintain Transaction Type" | COTRTLI.bms:76-78 |
| Page | PAGENO | 4,76 | 3 | O | | WS-CA-SCREEN-NUM — COTRTLIC:1318 |
| Type Filter | TRTYPE | 6,44 | 2 | I/O | FSET,IC,UNPROT; ASKIP+blue while an action is pending; red on error | blank/spaces/`00` = no filter; else must be NUMERIC — COTRTLIC:1096-1123, 1438-1480 |
| Description Filter | TRDESC | 8,25 | 50 | I/O | FSET,UNPROT; same protect/red rules | non-blank → `LIKE '%'‖TRIM‖'%'` — COTRTLIC:1142-1163 |
| Select (×7) | TRTSEL1-7 | 12-18,6 | 1 | I/O | PROT in BMS; set FSE (unprotected) on rows with data unless rows protected | `D`, `U`, blank — COTRTLIC:178-188, 1329-1372 |
| Type (×7) | TRTTYP1-7 | 12-18,17 | 2 | O | PROT; neutral when row selected | COTRTLIC:1402 |
| Description (×7) | TRTYPD1-7 | 12-18,25 | 50 | I/O | UNPROT in BMS but program sets PRF; FSE only on `U` row; red if invalid; `*` if blanked | 1240 alphanumeric/space only, required — COTRTLIC:1337, 1356-1369 |
| (8th row) | TRTSELA/TRTTYPA/TRTDSCA | 19 | 1/2/50 | — | PROT | not referenced by program (see §9) — COTRTLI.bms:280-300 |
| Info | INFOMSG | 21,19 | 45 | O | PROT; neutral when shown | centred text — COTRTLIC:1562-1579 |
| Error | ERRMSG | 23,1 | 78 | O | ASKIP,BRT,FSET | WS-RETURN-MSG — COTRTLIC:1557 |
| Keys | BUTNF02/03/07/08/10 | 24 | | O | "F2=Add" "F3=Exit" "F7=Page Up" "F8=Page Dn" "F10=Save" | COTRTLI.bms:312-336 |

PF-key map (COTRTLIC:574-678): ENTER = validate/apply filters & selections; PF2 = XCTL COTRTUPC; PF3 = XCTL caller/COADM01C; PF7/PF8 = page back/forward; PF10 = confirm pending delete/update (only if no filter or selection change since ENTER, otherwise treated as ENTER); all other keys (incl. PF13-24 mapped to PF1-12 by CSSTRPFY) = ENTER.

### 2.2 COTRTUP / CTRTUPA (CTTU)
| Name | BMS field | Pos | Len | I/O | Attributes | Edit rules / source |
|---|---|---|---|---|---|---|
| Title | literal | 7,28 | 25 | O | "Maintain Transaction Type" | COTRTUP.bms:75-78 |
| Transaction Type | TRTYPCD | 12,26 | 2 | I/O | IC,UNPROT,underline; FSE only in search states; red + `*` on blank/invalid | required, TEST-NUMVAL numeric, ≠0, normalised to 2-digit zero-padded — COTRTUPC:820-847, 1276-1340 |
| Description | TRTYDSC | 14,26 | 50 | I/O | UNPROT; FSE in show/edit/new states; red + `*` via CSSETATY | required, letters/digits/spaces only — COTRTUPC:758-764, 849-905, 1358-1361 |
| Info | INFOMSG | 22,23 | 45 | O | ASKIP; DAR if empty, ASB otherwise | centred 40-byte WS-INFO-MSG — COTRTUPC:1251-1262, 1386-1391 |
| Error | ERRMSG | 23,1 | 78 | O | ASKIP,BRT,FSET red | COTRTUPC:1264 |
| Keys | FKEYS/FKEY04/05/06/12 | 24 | | O | "ENTER=Process F3=Exit" always; "F4=Delete", "F5=Save", "F12=Cancel" brightened by state; "F6=Add" never brightened | COTRTUP.bms:111-135; COTRTUPC:1397-1422 |

PF-key map (COTRTUPC:577-618): ENTER (not in delete-confirm); PF3 always; PF4 on show/confirm-delete; PF5 on changes-validated / not-found / delete-in-progress; PF12 on validated/show/not-found/confirm-delete/new; otherwise "Invalid key pressed".

State machine (`TTUP-CHANGE-ACTION`, COTRTUPC:294-327): blank/LOW (not fetched) → `S` show → `N` validated → `C` done; `X` not found → `R` new → `N`; `S` →PF4→ `9` confirm → `8` start → `7` done / `6` failed; `E` changes not OK; `L`/`F` lock/other failure; `B` backed-out; `K` invalid key.

### 2.3 COPAU00 / COPAU0A (CPVS)
| Name | BMS field | Pos | Len | I/O | Attributes | Edit / source |
|---|---|---|---|---|---|---|
| Title | literal | 3,30 | 19 | O | "View Authorizations" | COPAU00.bms:75 |
| Search Acct Id | ACCTID | 5,19 | 11 | I/O | FSET,UNPROT, cursor | required, NUMERIC — COPAUS0C:264-284 |
| Name / Customer Id | CNAME / CUSTID | 6,10 / 6,58 | 25 / 9 | O | ASKIP | first + middle initial + last — COPAUS0C:757-764 |
| Address 1/2 | ADDR001 / ADDR002 | 7,10 / 8,10 | 25 | O | ASKIP | "line1,line2" / "line3,ST,zip5" — COPAUS0C:766-777 |
| Acct Status | ACCSTAT | 7,58 | 1 | O | ASKIP | never populated (§9) |
| Phone | PHONE1 | 9,15 | 13 | O | ASKIP | COPAUS0C:779 |
| Approval # / Decline # | APPRCNT / DECLCNT | 9,58 / 9,76 | 3 | O | ASKIP | 9(03) — COPAUS0C:788-791 |
| Credit Lim / Cash Lim | CREDLIM / CASHLIM | 11,19 / 11,46 | 12 / 9 | O | ASKIP,FSET | `-zzzzzzz9.99` / `-zzzz9.99` — COPAUS0C:780-783 |
| Appr Amt / Decl Amt | APPRAMT / DECLAMT | 11,69 / 12,69 | 10 | O | ASKIP,FSET | `-zzzz9.99` — COPAUS0C:796-799 |
| Credit Bal / Cash Bal | CREDBAL / CASHBAL | 12,19 / 12,46 | 12 / 9 | O | ASKIP,FSET | COPAUS0C:792-795 |
| Sel (×5) | SEL0001-5 | 16-20,3 | 1 | I | FSET,UNPROT in BMS; program protects empty rows | `S`/`s` — COPAUS0C:285-331, 554, 614 |
| Transaction ID | TRNID01-5 | 16-20,8 | 16 | O | ASKIP | PA-TRANSACTION-ID |
| Date / Time | PDATE01-5 / PTIME01-5 | ,27 / ,38 | 8 | O | ASKIP | MM/DD/YY from YYMMDD; HH:MM:SS — COPAUS0C:527-534 |
| Type / A/D / STS | PTYPE / PAPRV / PSTAT | ,49 / ,58 / ,63 | 4/1/1 | O | ASKIP | auth type; `A` if resp `00` else `D`; match status — COPAUS0C:536-552 |
| Amount | PAMT001-5 | ,67 | 12 | O | ASKIP | PA-APPROVED-AMT `-zzzzzzz9.99` — COPAUS0C:525 |
| Instruction / Error | literal / ERRMSG | 22,12 / 23,1 | 52 / 78 | O | BRT | COPAU00.bms:497, 503 |
| Keys | literal | 24,1 | 48 | O | "ENTER=Continue  F3=Back  F7=Backward  F8=Forward" | COPAU00.bms:507-510 |

PF keys: ENTER, PF3 (→COMEN01C), PF7, PF8; others → invalid-key message (COPAUS0C:224-250).

### 2.4 COPAU01 / COPAU1A (CPVD)
All fields output-only (ASKIP): CARDNUM 7,11 L16; AUTHDT 7,43 L10; AUTHTM 7,68 L10; AUTHRSP 9,14 L1 (green A / red D); AUTHRSN 9,32 L20 (`nnnn-DESC`); AUTHCD 9,68 L6; AUTHAMT 11,11 L12; POSEMD 11,46 L4; AUTHSRC 11,68 L10; MCCCD 13,13 L4; CRDEXP 13,42 L5 (MM/YY); AUTHTYP 13,64 L14; TRNID 15,12 L15; AUTHMTC 15,46 L1; AUTHFRD 15,67 L10 (`F-MM/DD/YY`, `R-…`, `-`); MERNAME 19,9 L25; MERID 19,55 L15; MERCITY 21,9 L25; MERST 21,49 L2; MERZIP 21,61 L10; ERRMSG 23,1 L78. Title "View Authorization Details" (4,27). Keys line " F3=Back  F5=Mark/Remove Fraud  F8=Next Auth". — COPAU01.bms:75-288; COPAUS1C:291-358.

PF keys: ENTER (refresh), PF3 (→COPAUS0C), PF5 (toggle fraud), PF8 (next); other keys → redisplay with invalid-key message (COPAUS1C:180-198). No PF7.

### 2.5 Batch DD / file I/O
| Job / step | Program | DD | Dataset | Dir | Record |
|---|---|---|---|---|---|
| MNTTRDB2 STEP1 | COBTUPDT (IKJEFT01, plan CARDDEMO) | INPFILE | `INPFILE` | IN | 1 action + 2 type + 50 desc (COBTUPDT:39-46) |
| TRANEXTR STEP10/20 | IEBGENER | SYSUT1→SYSUT2 | TRANTYPE.PS → TRANTYPE.BKUP(+1); TRANCATG.PS → TRANCATG.PS.BKUP(+1) | IN/OUT | FB 60 |
| TRANEXTR STEP30 | IEFBR14 | DD01/DD02 | TRANTYPE.PS, TRANCATG.PS | DELETE | |
| TRANEXTR STEP40/50 | DSNTIAUL | SYSREC00 | TRANTYPE.PS / TRANCATG.PS | OUT | CHAR(60) |
| CREADB21 | IKJEFT01 → DSNTIAD / DSNTEP4 | SYSTSIN/SYSIN | `&LBNM..CNTL(DB2FREE/DB2TIAD1/DB2CREAT/DB2TEP41/DB2LTTYP/DB2LTCAT)` | IN | SQL |
| CBPAUP0J STEP01 | DFSRRC00 BMP CBPAUP0C PSBPAUTB | SYSIN | inline `00,00001,00001,Y` | IN | PRM-INFO (CBPAUP0C:98-108) |
| UNLDPADB STEP0/STEP01 | IEFBR14; DFSRRC00 DLI PAUDBUNL PAUTBUNL | OUTFIL1 / OUTFIL2; DDPAUTP0/DDPAUTX0 | PAUTDB.ROOT.FILEO (FB100) / PAUTDB.CHILD.FILEO (FB206) | OUT | root seg / key+detail |
| LOADPADB STEP01 | DFSRRC00 BMP PAUDBLOD PSBPAUTB | INFILE1 / INFILE2 | ROOT.FILEO / CHILD.FILEO | IN | as above |
| UNLDGSAM STEP01 | DFSRRC00 DLI DBUNLDGS DLIGSAMP | PASFILOP / PADFILOP | PAUTDB.ROOT.GSAM / CHILD.GSAM | OUT (GSAM) | 100 / 200 |
| DBPAUTP0 | IEFBR14; DFSRRC00 ULU DFSURGU0 | DFSURGU1 | IMSDATA.DBPAUTP0 | OUT | VB 27990 |

---

## 3. Business rules & calculations
| ID | Rule | Source cite |
|---|---|---|
| BR-01 | Transaction type code is CHAR(2) PK; description VARCHAR(50) NOT NULL; category PK (type, category) with FK to type ON DELETE RESTRICT | DB2CREAT.ctl:35-38, 75-79, 97-99; TRNTYCAT.ddl:1-7 |
| BR-02 | CTLI page size is 7 rows; forward cursor `TR_TYPE >= start` ascending, backward `TR_TYPE < start` descending; an 8th look-ahead fetch decides whether a next page exists | COTRTLIC:60, 338-368, 1657-1693 |
| BR-03 | CTLI type filter is an exact match on a 2-digit numeric code; `00`/blank means no filter | COTRTLIC:1101-1122 |
| BR-04 | CTLI description filter is substring `LIKE '%'‖TRIM(input)‖'%'` | COTRTLIC:1155-1162 |
| BR-05 | Changing either filter resets paging to page 1 and clears selections | COTRTLIC:1127-1137, 1166-1175 |
| BR-06 | Exactly one row action (D or U) may be selected; delete/update requires ENTER then PF10 with unchanged filters/selection | COTRTLIC:1003-1052, 666-678 |
| BR-07 | Description edit (both programs): required; after removing A-Z, a-z, 0-9 only spaces may remain | COTRTLIC:1181-1237; COTRTUPC:849-905 |
| BR-08 | "No change" comparison is case-insensitive on trimmed text and length | COTRTLIC:1064-1073; COTRTUPC:783-811 |
| BR-09 | CTTU type code: required, TEST-NUMVAL numeric, NUMVAL ≠ 0, stored as 2-digit zero-padded (e.g. `5` → `05`) | COTRTUPC:907-976, 834-842 |
| BR-10 | CTTU save is an upsert: UPDATE; if SQLCODE +100 then INSERT | COTRTUPC:1544-1560, 1596-1620 |
| BR-11 | All online Db2 changes commit via CICS SYNCPOINT immediately after a successful statement; PF3 exits also SYNCPOINT | COTRTUPC:1557, 1606, 1637, 453-455; COTRTLIC:1856, 1909, 616-618 |
| BR-12 | Db2 -911 on update = lock/deadlock message; -532 on delete = child rows exist | COTRTUPC:1561-1566, 1638; COTRTLIC:1870-1879, 1914 |
| BR-13 | COBTUPDT: action A/U/D/`*`; any other action, SQL error, or +100 on U/D sets RC=4 and processing continues | COBTUPDT:109-233 |
| BR-14 | TRANEXTR extract layouts: type = TR_TYPE ‖ CHAR(desc,50) ‖ `'00000000'`; category = code ‖ category ‖ CHAR(desc,50) ‖ `'0000'`; both CHAR(60) | TRANEXTR.jcl:76-84, 106-115 |
| BR-15 | CP00 available credit = summary credit limit − summary credit balance when a PAUTSUM0 exists, else ACCT-CREDIT-LIMIT − ACCT-CURR-BAL; decline when amount > available; decline when account not found | COPAUA0C:665-683 |
| BR-16 | Response code `00` approved (approved amount = request amount) / `05` declined (approved amount 0) | COPAUA0C:685-696 |
| BR-17 | Decline reason: `3100` card/account/customer not found; `4100` insufficient funds; `9000` other; `0000` when approved. (`4200`,`4300`,`5100`,`5200` are coded but never set) | COPAUA0C:698-718, 140-145 |
| BR-18 | Auth ID code returned = request AUTH-TIME | COPAUA0C:662 |
| BR-19 | Summary update: credit/cash limits copied from ACCTDAT; approved → approved count+1, approved amount += amt, credit balance += amt, cash balance := 0; declined → declined count+1, declined amount += PA-TRANSACTION-AMT | COPAUA0C:810-822 |
| BR-20 | Detail key PAUT9CTS (8 bytes) = COMP-3 (99999 − YYDDD) ‖ COMP-3 (999999999 − HHMMSSmmm) so newest sorts first | COPAUA0C:857-875; CIPAUDTY:19-21; DBPAUTP0.dbd:37 |
| BR-21 | Detail match status: `P` pending if approved, `D` declined; fraud flag/date blank at insert | COPAUA0C:902-909; CIPAUDTY:45-53 |
| BR-22 | CP00 processes at most 500 messages per trigger (stops when count > 500) and waits 5 s on an empty queue | COPAUA0C:40, 242, 339-343 |
| BR-23 | Reply message: non-persistent, MsgType REPLY, CorrelId = request CorrelId, expiry 50 (tenths of a second), to the request's ReplyToQ | COPAUA0C:741-756, 411-414 |
| BR-24 | CPVS page size 5; page-start keys kept for up to 20 pages; next-page determined by look-ahead GNP | COPAUS0C:117-126, 415-454 |
| BR-25 | CPVS/CPVD list/detail amount shown is PA-APPROVED-AMT (0 for declined auths); A/D derived from resp code `00` | COPAUS0C:525, 536-540; COPAUS1C:308-317 |
| BR-26 | CPVD reason description by binary search of a 10-entry table; unknown code shows `9999-ERROR` | COPAUS1C:56-73, 319-329 |
| BR-27 | Fraud toggle: if detail fraud = `F` then set `R` (remove) else set `F` (report); Db2 INSERT, or UPDATE on -803 duplicate; then IMS REPL; SYNCPOINT on success, ROLLBACK on any failure | COPAUS1C:236-261, 520-552; COPAUS2C:141-244 |
| BR-28 | AUTHFRDS key AUTH_TS = TIMESTAMP_FORMAT(YY-MM-DD from PA-AUTH-ORIG-DATE ‖ HH.MI.SS+ms from 999999999−PA-AUTH-TIME-9C ‖ `000`); FRAUD_RPT_DATE = CURRENT DATE | COPAUS2C:103-114, 171-172, 194 |
| BR-29 | Purge: detail expired when (current YYDDD − (99999 − PA-AUTH-DATE-9C)) ≥ expiry days; default expiry 5, checkpoint freq 5, display freq 10 when SYSIN invalid | CBPAUP0C:277-296, 196-209 |
| BR-30 | Purge adjusts in-memory summary counters (approved cnt/amt or declined cnt/amt) for each deleted detail; summary deleted when approved count ≤ 0 | CBPAUP0C:287-293, 156-158 |
| BR-31 | Purge checkpoints (`CHKP ID 'RMAD'nnnn`) after more than P-CHKP-FREQ summaries and at end | CBPAUP0C:160-169, 352-374 |
| BR-32 | CDRA request is valid only when function = `INQA` and account key > 0; key read from ACCTDAT by 11-byte account id | COACCT01:393-404 |
| BR-33 | CDRD/CDRA reply queues are fixed names (`CARD.DEMO.REPLY.DATE` / `CARD.DEMO.REPLY.ACCT`); request ReplyToQ ignored; reply MsgId/CorrelId copied from request | CODATE01:147, 373-374; COACCT01:198, 469-470 |
| BR-34 | CDRD/CDRA get under syncpoint with 5 s wait and commit (SYNCPOINT) between messages | CODATE01:274-299; COACCT01:325-350 |

---

## 4. Data access
| Program | Resource | Infra | Operation | Key | Record / copybook |
|---|---|---|---|---|---|
| COTRTLIC | CARDDEMO.TRANSACTION_TYPE | Db2 | BROWSE (cursors fwd/bwd), SELECT COUNT, UPDATE, DELETE | TR_TYPE | DCLTRTYP |
| COTRTLIC / COTRTUPC | SYSIBM.SYSDUMMY1 | Db2 | SELECT (priming, CTLI only) | — | CSDB2RPY |
| COTRTUPC | CARDDEMO.TRANSACTION_TYPE | Db2 | SELECT, UPDATE, INSERT, DELETE | TR_TYPE | DCLTRTYP (DCLTRCAT included, unused) |
| COBTUPDT | CARDDEMO.TRANSACTION_TYPE; INPFILE | Db2; QSAM | INSERT/UPDATE/DELETE; READ | TR_TYPE | DCLTRTYP |
| DSNTIAUL (TRANEXTR) | TRANSACTION_TYPE, TRANSACTION_TYPE_CATEGORY | Db2 | READ (unload) | — | — |
| COPAUA0C | CCXREF | VSAM KSDS | READ | card num (16) | CVACT03Y |
| COPAUA0C | ACCTDAT / CUSTDAT | VSAM KSDS | READ | acct id (11) / cust id (9) | CVACT01Y / CVCUS01Y |
| COPAUA0C | DBPAUTP0 PAUTSUM0 | IMS (PSBPAUTB) | GU, REPL / ISRT | ACCNTID | CIPAUSMY |
| COPAUA0C | DBPAUTP0 PAUTDTL1 | IMS | ISRT (path via PAUTSUM0) | PAUT9CTS | CIPAUDTY |
| COPAUA0C | request queue (trigger QNAME), ReplyToQ | MQ | MQGET (no syncpoint), MQPUT1 | CorrelId | CCPAURQY / CCPAURLY |
| COPAUA0C | TDQ CSSL | CICS TD | WRITEQ | — | CCPAUERY |
| COPAUS0C | CXACAIX (xref by account AIX) | VSAM AIX | READ | acct id | CVACT03Y |
| COPAUS0C | ACCTDAT / CUSTDAT | VSAM | READ | acct / cust | CVACT01Y / CVCUS01Y |
| COPAUS0C | PAUTSUM0 / PAUTDTL1 | IMS | GU summary; GNP details; GNP qualified (reposition) | ACCNTID / PAUT9CTS | CIPAUSMY / CIPAUDTY |
| COPAUS1C | PAUTSUM0 / PAUTDTL1 | IMS | GU; GNP qualified; GNP; REPL detail | ACCNTID / PAUT9CTS | CIPAUSMY / CIPAUDTY |
| COPAUS2C | CARDDEMO.AUTHFRDS | Db2 (AWS01PLN) | INSERT; UPDATE on duplicate | CARD_NUM, AUTH_TS | DCLAUTHFRDS (AUTHFRDS.dcl) |
| CBPAUP0C | PAUTSUM0 / PAUTDTL1 | IMS BMP | GN, GNP, DLET, DLET, CHKP | — | CIPAUSMY / CIPAUDTY |
| PAUDBUNL / DBUNLDGS | PAUTSUM0 / PAUTDTL1 → files / GSAM | IMS DLI | GN, GNP; WRITE / GSAM ISRT | — | CIPAUSMY / CIPAUDTY |
| PAUDBLOD | files → PAUTSUM0 / PAUTDTL1 | IMS BMP | READ; ISRT root; GU root + ISRT child | ACCNTID | CIPAUSMY / CIPAUDTY |
| CODATE01 | trigger queue, CARD.DEMO.REPLY.DATE, CARD.DEMO.ERROR | MQ | MQGET (syncpoint), MQPUT | — | text |
| COACCT01 | ACCTDAT; MQ queues as above (REPLY.ACCT) | VSAM; MQ | READ; MQGET/MQPUT | acct id 11 | CVACT01Y |

### 4.1 IMS structures
| Object | Definition | Cite |
|---|---|---|
| DBPAUTP0 | HIDAM/VSAM, DD DDPAUTP0; root **PAUTSUM0** 100 bytes, key ACCNTID (packed, 6 bytes, unique) with index LCHILD; child **PAUTDTL1** 200 bytes, key PAUT9CTS (8 bytes char, unique) | DBPAUTP0.dbd:18-37 |
| DBPAUTX0 | Primary index, segment PAUTINDX 6 bytes → PAUTSUM0 | DBPAUTX0.dbd:18-31 |
| PSBPAUTB | PCB DBPAUTP0 PROCOPT=AP, SENSEG PAUTSUM0/PAUTDTL1 (used by CP00, CPVS, CPVD, CBPAUP0C, PAUDBLOD) | PSBPAUTB.psb:17-20 |
| PSBPAUTL | PROCOPT=L, LANG=ASSEM (load PSB; not referenced by any program) | PSBPAUTL.psb:17-20 |
| PAUTBUNL / DLIGSAMP | PROCOPT=GOTP (+2 GSAM PCBs PROCOPT=LS for DLIGSAMP) | PAUTBUNL.PSB:18-21; DLIGSAMP.PSB:18-23 |
| PASFLDBD / PADFLDBD | GSAM/BSAM, RECORD 100 / 200, RECFM F | PASFLDBD.DBD:22-27; PADFLDBD.DBD:22-27 |
| CIPAUSMY (root) | ACCT-ID S9(11) C3, CUST-ID 9(9), AUTH-STATUS X, ACCOUNT-STATUS X(2)×5, CREDIT/CASH LIMIT, CREDIT/CASH BALANCE S9(9)V99 C3, APPROVED/DECLINED CNT S9(4) COMP, APPROVED/DECLINED AMT S9(9)V99 C3, FILLER 34 | CIPAUSMY:19-31 |
| CIPAUDTY (child) | key (DATE-9C S9(5) C3, TIME-9C S9(9) C3), orig date/time X6, card 16, auth type 4, expiry 4, msg type 6, source 6, auth id 6, resp code 2 (`00`=approved), reason 4, processing code 9(6), txn/approved amt S9(10)V99 C3, MCC 4, country 3, POS 9(2), merchant id 15/name 22/city 13/state 2/zip 9, tran id 15, match status (P/D/E/M), fraud (F/R), fraud rpt date X8, FILLER 17 | CIPAUDTY:19-54 |

### 4.2 Db2 DDL
| Table | Columns (type) | Key / index | Cite |
|---|---|---|---|
| CARDDEMO.TRANSACTION_TYPE | TR_TYPE CHAR(2), TR_DESCRIPTION VARCHAR(50) | PK TR_TYPE; XTRAN_TYPE | TRNTYPE.ddl:1-4; XTRNTYPE.ddl; DCLTRTYP:28-46 |
| CARDDEMO.TRANSACTION_TYPE_CATEGORY | TRC_TYPE_CODE CHAR(2), TRC_TYPE_CATEGORY CHAR(4), TRC_CAT_DATA VARCHAR(50) | PK (code, category); X_TRAN_TYPE_CATG; FK → TRANSACTION_TYPE RESTRICT | TRNTYCAT.ddl:1-7; DCLTRCAT:28-51 |
| CARDDEMO.AUTHFRDS | CARD_NUM CHAR16, AUTH_TS TIMESTAMP, AUTH_TYPE 4, CARD_EXPIRY_DATE 4, MESSAGE_TYPE 6, MESSAGE_SOURCE 6, AUTH_ID_CODE 6, AUTH_RESP_CODE 2, AUTH_RESP_REASON 4, PROCESSING_CODE 6, TRANSACTION_AMT/APPROVED_AMT DEC(12,2), MERCHANT_CATAGORY_CODE 4, ACQR_COUNTRY_CODE 3, POS_ENTRY_MODE SMALLINT, MERCHANT_ID 15, MERCHANT_NAME VARCHAR22, MERCHANT_CITY 13, MERCHANT_STATE 2, MERCHANT_ZIP 9, TRANSACTION_ID 15, MATCH_STATUS 1, AUTH_FRAUD 1, FRAUD_RPT_DATE DATE, ACCT_ID DEC(11), CUST_ID DEC(9) | PK (CARD_NUM, AUTH_TS); XAUTHFRD (CARD_NUM ASC, AUTH_TS DESC) | AUTHFRDS.ddl:1-28; XAUTHFRD.ddl:1-4; AUTHFRDS.dcl:24-86 |

Seed data: types 01 PURCHASE, 02 PAYMENT, 03 CREDIT, 04 AUTHORIZATION, 05 REFUND, 06 REVERAL, 07 ADJUSTMENT (DB2LTTYP.ctl:17-23); 18 categories (DB2LTCAT.ctl:20-37).

### 4.3 CICS resources
| CSD | Definitions | Cite |
|---|---|---|
| CRDDEMOD | MAPSET COTRTLI, COTRTUP; PROGRAM COTRTLIC (CTLI), COTRTUPC (CTTU); TRANSACTION CTLI, CTTU; DB2ENTRY CARDDEMO (PLAN CARDDEMO, THREADLIMIT 1); DB2TRAN CTLITRAN, CTTUTRAN | CRDDEMOD.csd:1-60 |
| CRDDEMO2 | MAPSET COPAU00, COPAU01; PROGRAM COPAUA0C, COPAUS0C, COPAUS1C, COPAUS2C; TRANSACTION CPVD, CPVS, CP00; DB2ENTRY AWS01PLN; DB2TRAN CPVDTRAN (CPVD) | CRDDEMO2.csd:1-78 |
| CRDDEMOM | PROGRAM COACCT01 (CDRA), CODATE01 (CDRD); TRANSACTION CDRA, CDRD; LIBRARY CARDDLIB | CRDDEMOM.csd:1-41 |

---

## 5. Message layouts (MQ)

### 5.1 CP00 request (comma-separated text, max 500 bytes) — COPAUA0C:103, 354-377; CCPAURQY:19-36
`AUTH-DATE(6),AUTH-TIME(6),CARD-NUM(16),AUTH-TYPE(4),CARD-EXPIRY-DATE(4),MESSAGE-TYPE(6),MESSAGE-SOURCE(6),PROCESSING-CODE(9(6)),TRANSACTION-AMT(text → NUMVAL),MERCHANT-CATAGORY-CODE(4),ACQR-COUNTRY-CODE(3),POS-ENTRY-MODE(9(2)),MERCHANT-ID(15),MERCHANT-NAME(22),MERCHANT-CITY(13),MERCHANT-STATE(2),MERCHANT-ZIP(9),TRANSACTION-ID(15)`

### 5.2 CP00 reply (comma-separated text, max 200 bytes) — COPAUA0C:720-731; CCPAURLY:19-24
`CARD-NUM(16),TRANSACTION-ID(15),AUTH-ID-CODE(6),AUTH-RESP-CODE(2),AUTH-RESP-REASON(4),APPROVED-AMT(-zzzzzzzzz9.99),` (note trailing comma). Queues per README: `AWS.M2.CARDDEMO.PAUTH.REQUEST` / `.REPLY` (code uses trigger QNAME and MQMD-REPLYTOQ).

### 5.3 CDRD — request content ignored; reply `SYSTEM DATE : ` + MM-DD-YYYY + `SYSTEM TIME : ` + HH:MM:SS, 1000-byte MQSTR — CODATE01:355-360, 371-375.

### 5.4 CDRA — request `WS-FUNC X(4)` (`INQA`), `WS-KEY 9(11)`, filler 985 (COACCT01:109-112). Reply (1000 bytes MQSTR, fixed labels, numerics as unedited zoned S9(10)V99): `ACCOUNT ID : `9(11) `ACCOUNT STATUS : `X `BALANCE : `S9(10)V99 `CREDIT LIMIT : `… `CASH LIMIT : `… `OPEN DATE : `X10 `EXPR DATE : `X10 `REIS DATE : `X10 `CREDIT BAL : `… `DEBIT BAL : `… `GROUP ID : `X10 — COACCT01:130-169.

### 5.5 Error records
* CDRD/CDRA → `CARD.DEMO.ERROR`: MQ-ERR-DISPLAY = para X25, msg X25, cond code 9(2), reason 9(5), queue X48 — CODATE01:58-67, 243.
* CP00 → TDQ `CSSL`: ERROR-LOG-RECORD (date, time, application `CP00`, program, location, level L/I/W/C, subsystem A/C/I/D/M/F, code1, code2, message X50, event key X20) — CCPAUERY:19-40; COPAUA0C:983-1006.

---

## 6. Message catalogue
### 6.1 CTLI (COTRTLIC)
| Text | Condition | Cite |
|---|---|---|
| "Type U to update, D to delete any record" | default info line | COTRTLIC:239-240, 1550-1552 |
| "Delete HIGHLIGHTED row ? Press F10 to confirm" | D selected, ENTER | 241-242, 1514-1522 |
| "Update HIGHLIGHTED row. Press F10 to save" | U selected, ENTER | 243-244, 1523-1531 |
| "HIGHLIGHTED row deleted.Hit Enter to continue" | delete committed | 245-246, 1507-1508 |
| "HIGHLIGHTED row was updated" | update committed | 247-248, 1509-1510 |
| "PF03 pressed. Exiting" | PF2 transfer (set, not displayed) | 251-252, 642 |
| "No records found for this search condition." | page 1 empty | 253-254, 1702-1705 |
| "No more pages for these search conditions" | PF8 hits end | 255-256, 1679, 1700 |
| "Please select only 1 action" | >1 selection | 257-258, 1049-1052 |
| "Action code selected is invalid" | code not D/U | 259-260, 1038 |
| "No change detected with respect to database values." | U with same text | 261-262, 1072 |
| "TYPE CODE FILTER,IF SUPPLIED MUST BE A 2 DIGIT NUMBER" | non-numeric filter | 1116 |
| "No Records found for these filter conditions" | filter count 0 | 1264 |
| "No previous pages to display" | PF7 on page 1 | 1534 |
| "No more pages to display" | PF8 after last page shown | 1539 |
| "Transaction Desc must be supplied." / "Transaction Desc can have numbers or alphabets only." | row description edit | 1083, 1195-1201, 1222-1228 |
| "<action> SQLCODE:<code> <DSNTIAC text>" with action = "Db2 access failure. ", "C-TR-TYPE-FORWARD fetch", "C-TR-TYPE-FORWARD close", "C-TR-TYPE-FORWARD Open", "C-TR-TYPE-BACKWARD Open", "C-TR-TYPE-BACKWARD close", "Error on fetch Cursor C-TR-TYPE-BACKWARD", "Error reading TRANSACTION_TYPE table ", "Record not found. Deleted by others ? ", "Deadlock. Someone else updating ?", "Update failed with", "Please delete associated child records first:", "Delete failed with message:" | Db2 errors (formatted by 9999-FORMAT-DB2-MESSAGE; "DSNTIAC CD: " replaces text if DSNTIAC fails) | CSDB2RPY:40, 53-84; COTRTLIC:1686, 1712, 1784, 1826, 1864, 1874, 1883, 1919, 1929, 1958, 1986, 2013, 2042 |

### 6.2 CTTU (COTRTUPC)
| Text | Condition | Cite |
|---|---|---|
| "Enter transaction type to be maintained" | initial/search state | COTRTUPC:147-148, 1214-1218 |
| "Selected transaction type shown above" | defined, not set | 145-146 |
| "Press F05 to add. F12 to cancel" | not found | 149-150, 1219-1220 |
| "Delete this record ? Press F4 to confirm" | PF4 on shown record | 151-152, 1229-1230 |
| "Delete successful." | delete done | 153-154, 1233-1234 |
| "Update transaction type details shown." | record shown / changes not OK | 155-156, 1226-1228 |
| "Enter new transaction type details." | PF5 on not-found | 157-158, 1235-1236 |
| "Changes validated.Press F5 to save" | validated | 160-161, 1237-1238 |
| "Changes committed to database" | save done | 162-163, 1239-1240 |
| "Changes unsuccessful" | delete failed / lock / update failed | 164-165, 1231-1232, 1241-1244 |
| "No record found for this key in database" | SELECT +100 | 175-176, 1493 |
| "No input received" | blank key on search | 177-178, 722 |
| "No change detected with respect to values fetched." | same description | 179-180, 800 |
| "Could not lock record for update" | -911 | 181-182, 1564 |
| "Update was cancelled" / "Delete was cancelled" | PF12 | 185-186, 191-192, 1001-1004 |
| "Invalid key pressed" | invalid PF key | 193-194, 606 |
| "Tran Type code must be supplied." / "Tran Type code must be numeric." / "Tran Type code must not be zero." | type edit | 826, 922-964 |
| "Transaction Desc must be supplied." / "Transaction Desc can have numbers or alphabets only." | desc edit | 758, 863-896 |
| "Error accessing: TRANSACTION_TYPE table. SQLCODE:<n>:<SQLERRM>" | SELECT < 0 | 1499-1507 |
| "Error updating: TRANSACTION_TYPE Table. SQLCODE:<n>:<SQLERRM>" | UPDATE < 0 | 1569-1577 |
| "Error inserting record into: TRANSACTION_TYPE Table. SQLCODE:<n>:<SQLERRM>" | INSERT fails | 1609-1617 |
| "Please delete associated child records first:SQLCODE :<n>:<SQLERRM><SQLERRM>" | DELETE -532 | 1640-1649 |
| "Delete failed with message:SQLCODE :<n>:<SQLERRM>" | DELETE other | 1653-1661 |
| ABEND-DATA with "UNEXPECTED DATA SCENARIO" / "UNEXPECTED ABEND OCCURRED." then ABEND 9999 | unmatched state / CICS abend | 1073-1080, 1675-1697 |
| Unused 88s: "PF03 pressed.Exiting              ", "Invalid Key pressed. ", "Name can only contain alphabets and spaces", "Record changed by some one else. Please review", "Update of record failed", "Delete of record failed", "Looks Good.... so far" | never displayed (or only as internal flags) | 169-196 |

### 6.3 COBTUPDT (SYSOUT)
"OPEN FILE OK", "OPEN FILE NOT OK", "PROCESSING   <rec>", "ADDING RECORD", "UPDATING RECORD", "DELETING RECORD", "IGNORING COMMENTED LINE", "ERROR: TYPE NOT VALID", "RECORD INSERTED SUCCESSFULLY", "RECORD UPDATED SUCCESSFULLY", "RECORD DELETED SUCCESSFULLY", "No records found.", "Error accessing: TRANSACTION_TYPE table. SQLCODE:<n>" — COBTUPDT:85-87, 105, 112-124, 153-158, 179-181, 209-211.

### 6.4 CP00 (COPAUA0C → TDQ CSSL; location / level / text)
| Loc | Level | Text | Cite |
|---|---|---|---|
| M001 | C/MQ | "REQ MQ OPEN ERROR" | 273-282 |
| I001 | C/IMS | "IMS SCHD FAILED" | 311-316 |
| M003 | C/CICS | "FAILED TO READ REQUEST MQ" | 419-429 |
| A001 | W/APP | "CARD NOT FOUND IN XREF" | 494-500 |
| C001 | C/CICS | "FAILED TO READ XREF FILE" | 502-512 |
| A002 | W/APP | "ACCT NOT FOUND IN XREF" | 541-547 |
| C002 | C/CICS | "FAILED TO READ ACCT FILE" | 550-560 |
| A003 | W/APP | "CUST NOT FOUND IN XREF" | 589-595 |
| C003 | C/CICS | "FAILED TO READ CUST FILE" | 598-608 |
| I002 | C/IMS | "IMS GET SUMMARY FAILED" | 633-639 |
| M004 | C/MQ | "FAILED TO PUT ON REPLY MQ" | 768-778 |
| I003 | C/IMS | "IMS UPDATE SUMRY FAILED" | 840-846 |
| I004 | C/IMS | "IMS INSERT DETL FAILED" | 925-931 |
| M005 | W/MQ | "FAILED TO CLOSE REQUEST MQ" | 966-975 |

### 6.5 CPVS (COPAUS0C)
"Please enter Acct Id..." (264-269); "Acct Id must be Numeric ..." (273-278); "Invalid selection. Valid value is S" (326-328); "You are already at the top of the page..." (381); "You are already at the bottom of the page..." (409); " System error while reading AUTH Details: Code:<st>" (476-480); " System error while repos. AUTH Details: Code:<st>" (509-513); "Account:<id> not found in XREF file. Resp:<n> Reas:<n>" (836-842); "Account:<id> System error while reading XREF file. Resp:<n> Reas:<n>" (851-857); "Account:<id> not found in ACCT file. Resp:… Reas:…" (886-892); "Account:<id> System error while reading ACCT file. Resp:… Reas:…" (901-907); "Customer:<id> not found in CUST file. Resp:… Reas:…" (937-943); "Customer:<id> System error while reading CUST file. Resp:… Reas:…" (952-958); " System error while reading AUTH Summary: Code:<st>" (988-992); " System error while scheduling PSB: Code:<st>" (1022-1026); CCDA-MSG-INVALID-KEY (base CSMSG01Y) (248).

### 6.6 CPVD (COPAUS1C / COPAUS2C)
"Already at the last Authorization..." (COPAUS1C:283); "AUTH MARKED FRAUD..." (537); "AUTH FRAUD REMOVED..." (535); " System error while FRAUD Tagging, ROLLBACK||<st>" (544-548); " System error while reading Auth Summary: Code:<st>" (455-459); " System error while reading Auth Details: Code:<st>" (481-485); " System error while reading next Auth: Code:<st>" (510-514); " System error while scheduling PSB: Code:<st>" (595-600); SYSOUT "RPT DT: <date>" (523); COPAUS2C commarea messages "ADD SUCCESS" (201), "UPDT SUCCESS" (232), " SYSTEM ERROR DB2: CODE:<sqlcode>, STATE: <sqlstate>" (211-213), " UPDT ERROR DB2: CODE:<sqlcode>, STATE: <sqlstate>" (239-241).

### 6.7 Batch IMS programs (SYSOUT)
CBPAUP0C: "STARTING PROGRAM CBPAUP0C::", "CBPAUP0C PARM RECEIVED :", "TODAYS DATE            :", "AUTH SUMMARY READ FAILED  :", "SUMMARY READ BEFORE ABEND :", "AUTH DETAIL READ FAILED  :", "AUTH DETAIL DELETE FAILED :", "AUTH SUMMARY DELETE FAILED :", "CHKP SUCCESS: AUTH COUNT - ", "CHKP FAILED: DIBSTAT - ", "# TOTAL SUMMARY READ  :", "# SUMMARY REC DELETED :", "# TOTAL DETAILS READ  :", "# DETAILS REC DELETED :", "CBPAUP0C ABENDING ..." — CBPAUP0C:171-193, 237-238, 267, 318, 343, 362, 366, 380.
PAUDBUNL / DBUNLDGS: "STARTING PROGRAM PAUDBUNL::" / "STARTING PROGRAM DBUNLDGS::", "AUTH SUM  GN FAILED  :", "KEY FEEDBACK AREA    :", "CHILD SEG FLAG GE : ", "GNP CALL FAILED  :", "KFB AREA IN CHILD:", "GSAM PARENT FAIL :" (DBUNLDGS), "ERROR IN OPENING OPFILE1:", "IMSUNLOD ABENDING ..." — PAUDBUNL:180, 190, 244-245, 276, 280-281, 311; DBUNLDGS:189, 254-255, 287, 291-292, 312.
PAUDBLOD: "STARTING PAUDBLOD", "ERROR READING ROOT SEG INFILE", "ROOT INSERT SUCCESS    ", "ROOT SEGMENT ALREADY IN DB", "ROOT INSERT FAILED  :", "GU CALL TO ROOT SEG SUCCESS", "ROOT GU CALL FAIL:", "CHILD SEGMENT INSERTED SUCCESS", "CHILD SEGMENT ALREADY IN DB", "INSERT CALL FAIL FOR CHILD:", "IMS LOAD ABENDING ..." — PAUDBLOD:173, 235, 254-260, 306-311, 327-333, 363.

### 6.8 CDRD / CDRA
Reply texts: "SYSTEM DATE : " / "SYSTEM TIME : " (CODATE01:355-356); "INVALID REQUEST PARAMETERS " + "ACCT ID : " [+ "FUNCTION : "] (COACCT01:429-430, 449-451). Error-queue return messages: "INP MQOPEN ERR", "OUT MQOPEN ERR", "ERR MQOPEN ERR", "INP MQGET ERR:", "MQPUT ERR", "MQCLOSE ERR", "ERROR WHILE READING ACCTFILE" (COACCT01 only), "RESP: <r1><r2>END"; para "CICS RETRIEVE" (CODATE01:149) / "CICS RETREIVE" (COACCT01:200) — CODATE01:149-154, 199, 233, 268, 333, 400, 437, 475; COACCT01:250, 284, 319, 384, 442, 496.

---

## 7. Navigation / integration
| Program | Inbound | Outbound | COMMAREA fields used |
|---|---|---|---|
| COTRTLIC | Admin menu COADM01C option (COADM02Y:49), COTRTUPC PF3, direct CTLI | XCTL COADM01C / CDEMO-FROM-PROGRAM (PF3); XCTL COTRTUPC (PF2); RETURN TRANSID CTLI | CARDDEMO-COMMAREA (COCOM01Y: FROM/TO-TRANID/PROGRAM, USRTYP, PGM-CONTEXT, LAST-MAP/MAPSET) + WS-THIS-PROGCOMMAREA (filters, 7 rows, selected row, first/last key, page no, next-page, delete/update flags) in a 2000-byte area — COTRTLIC:377-420, 899-914 |
| COTRTUPC | Admin menu (COADM02Y:53), COTRTLIC PF2, direct CTTU | XCTL caller or COADM01C (PF3); RETURN TRANSID CTTU | COCOM01Y + TTUP-CHANGE-ACTION, old/new type+desc; zeroes CDEMO-ACCT-ID/CARD-NUM — COTRTUPC:294-336, 559-571, 1065-1072 |
| COPAUA0C | MQ trigger monitor (CKTI) → CP00 via MQTM | MQPUT1 to ReplyToQ; WRITEQ TD CSSL; EXEC CICS RETURN | none (LK-COMMAREA 4096 unused) — COPAUA0C:214-215 |
| COPAUS0C | Main menu COMEN01C option (COMEN02Y:89) | XCTL COPAUS1C (S); XCTL COMEN01C (PF3) / COSGN00C default; RETURN TRANSID CPVS | CDEMO-ACCT-ID, CDEMO-CUST-ID, CDEMO-CARD-NUM, PGM-REENTER + CDEMO-CPVS-INFO (sel flag, selected key, 20 prev-page keys, last key, page num, next flag, 5 row keys) — COPAUS0C:116-126, 830-831 |
| COPAUS1C | COPAUS0C (XCTL) | XCTL COPAUS0C (PF3 / no commarea); LINK COPAUS2C (PF5); RETURN TRANSID CPVD | CDEMO-ACCT-ID, CDEMO-CUST-ID, CDEMO-CPVD-PAU-SELECTED (same offset as CPVS-PAU-SELECTED) — COPAUS1C:109-120 |
| COPAUS2C | LINK from COPAUS1C | RETURN | WS-FRAUD-DATA: acct 9(11), cust 9(9), CIPAUDTY 200, action F/R, status S/F, message X50 — COPAUS2C:74-86 |
| CODATE01 / COACCT01 | MQ trigger (MQTM) → CDRD / CDRA | MQPUT reply/error; RETURN | none |
| CBPAUP0C etc. | JCL (no scheduler entry found in scope) | — | — |
| TRANEXTR | JCL | produces TRANTYPE.PS / TRANCATG.PS for base VSAM load (consumer not in scope) | — |

---

## 8. Edge cases, error handling, abend / return codes
| Area | Behaviour | Cite |
|---|---|---|
| CTLI Db2 down | Priming query fails → SEND TEXT of formatted SQL message, RETURN without TRANSID (conversation ends) | COTRTLIC:684-691, 2085-2095 |
| CTLI/CTTU commit | Every successful DML followed by SYNCPOINT; failures leave no explicit ROLLBACK (task end backs out) | COTRTLIC:1856, 1909; COTRTUPC:1557, 1606, 1637 |
| CTTU unexpected state | ABEND-ROUTINE sends ABEND-DATA, cancels handle, `EXEC CICS ABEND ABCODE('9999')` | COTRTUPC:1073-1080, 1675-1697 |
| CTTU HANDLE ABEND | Any CICS abend routed to ABEND-ROUTINE | COTRTUPC:348-350 |
| COBTUPDT | Errors → DISPLAY, RETURN-CODE 4, continue; open failure only displays and continues | COBTUPDT:82-89, 230-233 |
| TRANEXTR | Steps 20-40 COND=(0,NE); step 50 COND=(4,LT) | TRANEXTR.jcl:42, 53, 65, 95 |
| CP00 critical error | log + DLI TERM + MQCLOSE + RETURN; remaining queue messages stay for the next trigger | COPAUA0C:1008-1023 |
| CP00 PSB | Scheduled per message; `TC` → TERM and reschedule; SYNCPOINT after each message releases PSB | COPAUA0C:292-318, 334-337 |
| CPVS/CPVD PSB | SYNCPOINT before every SEND releases the PSB (pseudo-conversational) | COPAUS0C:684-688; COPAUS1C:218-221, 276-279 |
| CPVD fraud | LINK NOHANDLE: non-NORMAL EIBRESP or Db2 failure → SYNCPOINT ROLLBACK | COPAUS1C:248-261, 565-569 |
| CBPAUP0C | Any IMS failure → message, RETURN-CODE 16, GOBACK (no abend) | CBPAUP0C:377-383 |
| PAUDBLOD / PAUDBUNL / DBUNLDGS | RETURN-CODE 16 on failure; duplicates (`II`) tolerated on load | PAUDBLOD:256-261, 330-335, 360-365; PAUDBUNL:308-314 |
| CDRD/CDRA | 5 s wait; NO-MSG-AVAILABLE ends loop; any MQ error → error queue + termination; error-queue open failure only DISPLAYs | CODATE01:286, 326-327, 264-270 |

---

## 9. Open questions, ambiguities and apparent legacy bugs (not fixed)
| # | Finding | Cite |
|---|---|---|
| Q-01 | CREADB21 has `TYPRUN=SCAN` on the JOB card, so as delivered it only syntax-checks and never creates/loads Db2 objects. | CREADB21.jcl:2 |
| Q-02 | Seed type 06 description is misspelt `REVERAL`. | DB2LTTYP.ctl:22 |
| Q-03 | Both online programs set the VARCHAR length to the full 50 bytes (`FUNCTION LENGTH` of the untrimmed field), so descriptions are stored right-padded with spaces. | COTRTUPC:1541-1542; COTRTLIC:1843-1844 |
| Q-04 | COTRTLIC 1210-EDIT-ARRAY tests the filter-changed flags before 1220/1230 set them for the current input, so "filter changed ⇒ ignore selections" never triggers on the same ENTER. | COTRTLIC:960-976, 991-994 |
| Q-05 | COTRTLIC backward read treats SQLCODE +100 (fewer than 7 earlier rows, e.g. after a delete or filter change) as a Db2 error ("Error on fetch Cursor C-TR-TYPE-BACKWARD"). | COTRTLIC:1761-1790 |
| Q-06 | COTRTLIC labels a first-fetch failure as "C-TR-TYPE-FORWARD close". | COTRTLIC:1706-1717 |
| Q-07 | COTRTLIC 88-levels CA-DELETE-SUCCEEDED/CA-UPDATE-SUCCEEDED share the LOW-VALUES value of …-NOT-REQUESTED, so "succeeded" is indistinguishable from "never requested". | COTRTLIC:411-418 |
| Q-08 | COTRTLIC PF2 transfer sets CDEMO-USRTYP-USER (not ADMIN), and COTRTUPC re-initialises its area on entry from COTRTLIC, so no selected type is carried to CTTU. | COTRTLIC:634; COTRTUPC:366-374 |
| Q-09 | BMS COTRTLI defines an 8th list row (TRTSELA/TRTTYPA/TRTDSCA, row 19) that the program never uses; its names collide with the symbolic-map attribute-suffix convention. Both maps show the same title "Maintain Transaction Type". | COTRTLI.bms:76-78, 280-300; COTRTLIC:60 |
| Q-10 | COTRTUPC 3201-SHOW-INITIAL-VALUES moves LOW-VALUES to TRTYPCDO twice and never clears TRTYDSCO. | COTRTUPC:1176-1179 |
| Q-11 | COTRTUPC: DATA-WAS-CHANGED-BEFORE-UPDATE is tested but never set (no optimistic-lock re-read); "F6=Add" is on the map but PF6 is not handled; delete -532 path does not set TTUP-DELETE-FAILED and appends SQLERRM twice. | COTRTUPC:1585-1586, 126-130 (bms), 1638-1649 |
| Q-12 | COTRTUPC type edit uses TEST-NUMVAL/NUMVAL into unsigned 9(02), so inputs such as `-1` or `1.` are accepted and normalised. | COTRTUPC:934-942, 834-841 |
| Q-13 | COBTUPDT: no explicit COMMIT; positive SQLCODEs other than +100 ignored; "error" path sets RC=4 but continues (named 9999-ABEND). Paragraph 0001-OPEN-FILES falls through into 1001. | COBTUPDT:80-99, 230-233 |
| Q-14 | TRANEXTR output padding (`'00000000'` / `'0000'`) and LRECL 60 must match the base-application VSAM layouts (TRANTYPE/TRANCATG); not verified in this scope. | TRANEXTR.jcl:76-115 |
| Q-15 | COPAUA0C: WS-RESP-LENGTH (STRING POINTER) starts at 1 and is never reset, so from the 2nd message onward the reply is built past the previous one / overflows the 200-byte buffer; length sent is pointer value (1 byte too long). | COPAUA0C:46, 720-731, 756 |
| Q-16 | COPAUA0C declined-amount accumulation uses PA-TRANSACTION-AMT from the detail area, which is only populated later in 8500 (i.e. previous message's amount). | COPAUA0C:820-821, 885 |
| Q-17 | COPAUA0C summary limits come from ACCOUNT-RECORD even when the account read failed (stale record); customer not found does not decline; decline reasons 4200/4300/5100/5200 are unreachable; 5600-READ-PROFILE-DATA is a stub. | COPAUA0C:810-811, 699-717, 647-651 |
| Q-18 | COPAUA0C MQGET uses NO-SYNCPOINT, so a request consumed before a critical failure is lost (no backout); reply is also NO-SYNCPOINT while IMS updates commit later. | COPAUA0C:389, 753 |
| Q-19 | COPAUA0C error M003 classifies an MQ failure as ERR-CICS; README names PSBPAUTL as the online PSB but code uses PSBPAUTB; README DB2ENTRY DB201PLN vs CSD AWS01PLN; README queue names (`AWS.M2.CARDDEMO.PAUTH.*`) are not in code. | COPAUA0C:82, 421; CRDDEMO2.csd:69-77 |
| Q-20 | COPAUS0C: NOTFND on XREF/ACCT/CUST does not set the error flag, so processing continues with stale data and SEND may be issued several times in one task; ACCSTAT is never populated; amount fields use `-zzzz9.99` (overflow above 99,999.99); prev-page key table has 20 slots with no bound check. | COPAUS0C:832-845, 57, 120, 436-441 |
| Q-21 | CPVS/CPVD list and detail show PA-APPROVED-AMT, so declined authorizations display 0.00 instead of the requested amount. | COPAUS0C:525; COPAUS1C:308 |
| Q-22 | COPAUS1C relies on CDEMO-CPVD-* overlaying CDEMO-CPVS-* at identical offsets after COCOM01Y (implicit contract). | COPAUS0C:117-126; COPAUS1C:110-120 |
| Q-23 | COPAUS2C builds AUTH_TS from the request's original date plus the processing-time key, a hybrid that may not equal the true auth timestamp; MERCHANT_NAME length forced to 22; it overwrites PA-FRAUD-RPT-DATE (MM/DD/YY) in the caller's record. README asks to change the schema name in COPAUS2C but it is hard-coded CARDDEMO. | COPAUS2C:101-111, 130, 142 |
| Q-24 | CBPAUP0C summary-delete test repeats `PA-APPROVED-AUTH-CNT <= 0` twice (declined count never checked). | CBPAUP0C:156 |
| Q-25 | CBPAUP0C adjusts summary counters in memory but never REPLs the summary, so adjustments are lost when the summary is kept; credit balance is never reduced although README claims "Adjustment of available credit". | CBPAUP0C:287-293 |
| Q-26 | CBPAUP0J passes expiry `00`, so every detail qualifies for deletion; P-CHKP-FREQ is X(5) compared with a numeric counter; checkpoint id counter never increments; YYDDD subtraction is wrong across year boundaries. | CBPAUP0J.jcl:37; CBPAUP0C:101, 160, 77, 280-282 |
| Q-27 | CBPAUP0J STEPLIB `XXXXXXXX.PROD.LOADLIB` is a placeholder; scheduling of CBPAUP0J/TRANEXTR is not defined in this scope. | CBPAUP0J.jcl:27 |
| Q-28 | CDRD/CDRA: README message formats (`DATE`/`ACCT` request types, REQUEST-ID, queues `CARDDEMO.REQUEST/RESPONSE.QUEUE`) do not match code (`INQA` + 9(11) key; queues `CARD.DEMO.REPLY.DATE/ACCT`, `CARD.DEMO.ERROR`); request ReplyToQ is saved but ignored. | CODATE01:147, 315-320; COACCT01:109-112, 198 |
| Q-29 | CDRD/CDRA: `MOVE WS-CICS-RESP2-CD TO WS-CICS-RESP2-CD` self-move (display value never set); input-queue open sets REPLY-QUEUE-OPEN; close-output error reports INPUT-QUEUE-NAME; close errors PERFORM 8000-TERMINATION recursively; MQ-MSG-COUNT has no initial value; "ERROR WHILE READING ACCTFILE" (28 chars) truncated to 25 in MQ-APPL-RETURN-MESSAGE; typo "CICS RETREIVE". | CODATE01:151, 194, 496, 476; COACCT01:202, 442, 200 |
| Q-30 | CDRA reply numeric fields are unedited zoned decimals with overpunched sign (no decimal point) — consumer format undefined. Both CSD program descriptions say "LIST CARDS"; no MQ trigger/MQCONN/MQQUEUE definitions are supplied in CSD. | COACCT01:138-166; CRDDEMOM.csd:2, 10 |
| Q-31 | Cash-advance handling: PA-CASH-BALANCE is forced to 0 on every approval; no cash/credit split exists. | COPAUA0C:818 |
