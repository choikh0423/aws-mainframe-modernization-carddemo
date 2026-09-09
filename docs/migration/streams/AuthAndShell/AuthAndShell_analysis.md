# S-01 AuthAndShell — source analysis

Scope: transactions **CC00** (sign-on), **CM00** (main menu), **CA00** (admin menu).
Programs `app/cbl/COSGN00C.cbl`, `app/cbl/COMEN01C.cbl`, `app/cbl/COADM01C.cbl`;
maps `app/bms/COSGN00.bms`, `app/bms/COMEN01.bms`, `app/bms/COADM01.bms`;
tables `app/cpy/COMEN02Y.cpy`, `app/cpy/COADM02Y.cpy`;
data `app/cpy/CSUSR01Y.cpy` (USRSEC), `app/cpy/COCOM01Y.cpy` (COMMAREA).

Every line reference in this document is `<file>:<line>` against the sources in `app/`.

---

## 1. Shared building blocks

### 1.1 COMMAREA — `app/cpy/COCOM01Y.cpy`

`CARDDEMO-COMMAREA` is the record every CICS program in the estate passes on
`XCTL` / `RETURN TRANSID`. The general-info group is what this stream owns
(`COCOM01Y.cpy:20-31`):

| Field | PIC | Set by this stream |
| --- | --- | --- |
| `CDEMO-FROM-TRANID` | X(04) | `CC00` / `CM00` / `CA00` before every transfer |
| `CDEMO-FROM-PROGRAM` | X(08) | `COSGN00C` / `COMEN01C` / `COADM01C` |
| `CDEMO-TO-TRANID` | X(04) | never set by S-01 |
| `CDEMO-TO-PROGRAM` | X(08) | only on the PF3 path back to `COSGN00C` (`COMEN01C.cbl:97`, `COADM01C.cbl:101`) |
| `CDEMO-USER-ID` | X(08) | sign-on, from the typed user id (`COSGN00C.cbl:134`, `:226`) |
| `CDEMO-USER-TYPE` | X(01), 88 `CDEMO-USRTYP-ADMIN 'A'` / `CDEMO-USRTYP-USER 'U'` | sign-on, from `SEC-USR-TYPE` (`COSGN00C.cbl:227`) |
| `CDEMO-PGM-CONTEXT` | 9(01), 88 `CDEMO-PGM-ENTER 0` / `CDEMO-PGM-REENTER 1` | zeroed before every transfer; set to 1 when a menu paints its screen |

The customer / account / card / last-map groups (`COCOM01Y.cpy:32-44`) are never
touched by S-01; downstream streams fill them.

### 1.2 USRSEC record — `app/cpy/CSUSR01Y.cpy`

`SEC-USER-DATA`: `SEC-USR-ID` X(08) (the VSAM key), `SEC-USR-FNAME` X(20),
`SEC-USR-LNAME` X(20), `SEC-USR-PWD` X(08) (clear text), `SEC-USR-TYPE` X(01),
`SEC-USR-FILLER` X(23) — 80 bytes. Seeded from `app/jcl/DUSRSECJ.jcl`
(5 × `ADMIN00n` type `A`, 5 × `USER000n` type `U`, all with password `PASSWORD`).
The estate has no `app/data/ASCII/usrsec.txt`; the in-stream JCL data is the
fixture, and the consolidated app carries it in `db/seed/R__seed_carddemo_data.sql`.

### 1.3 Screen header — `COTTL01Y` / `CSDAT01Y`

Every screen paints `CCDA-TITLE01` = `AWS Mainframe Modernization` and
`CCDA-TITLE02` = `CardDemo` (`COTTL01Y.cpy:18-21`), the transaction id, the
program name, `mm/dd/yy` and `hh:mm:ss` from `FUNCTION CURRENT-DATE`
(`COSGN00C.cbl:177-196`, `COMEN01C.cbl:238-257`, `COADM01C.cbl:205-224`).
`COSGN00C` additionally paints `APPLID` and `SYSID` from `EXEC CICS ASSIGN`
(`COSGN00C.cbl:198-204`); the two menus do not.

### 1.4 Shared messages — `app/cpy/CSMSG01Y.cpy`

* `CCDA-MSG-THANK-YOU` = `Thank you for using CardDemo application...` (`CSMSG01Y.cpy:18-19`)
* `CCDA-MSG-INVALID-KEY` = `Invalid key pressed. Please see below...` (`CSMSG01Y.cpy:20-21`)

Both are PIC X(50), space padded on the mainframe.

---

## 2. COSGN00C — CC00 sign-on

### 2.1 Entry conditions

`MAIN-PARA` (`COSGN00C.cbl:73-102`):

* `ERR-FLG-OFF`, `WS-MESSAGE` and `ERRMSGO` cleared (`:75-78`).
* `EIBCALEN = 0` (transaction typed at a clear screen, or an `XCTL` from a menu
  with no commarea): `MOVE LOW-VALUES TO COSGN0AO`, cursor to `USERID`
  (`MOVE -1 TO USERIDL`), send the map (`:80-83`).
* Otherwise the AID is evaluated (`:85-95`):
  * `DFHENTER` → `PROCESS-ENTER-KEY`
  * `DFHPF3` → `WS-MESSAGE = CCDA-MSG-THANK-YOU`, `SEND-PLAIN-TEXT`
    (`SEND TEXT ... ERASE FREEKB` then a bare `EXEC CICS RETURN`, `:162-172`) —
    the pseudo-conversation ends, no commarea survives.
  * any other key → error flag on, `CCDA-MSG-INVALID-KEY`, redisplay.
* Every path except PF3 ends at `EXEC CICS RETURN TRANSID('CC00') COMMAREA(CARDDEMO-COMMAREA)` (`:98-102`).

### 2.2 Screen layout — `app/bms/COSGN00.bms` (`COSGN0A`, 24×80)

| Row/col | Field | Content |
| --- | --- | --- |
| 1,1 / 1,8 | literal / `TRNNAME` | `Tran :` + `CC00` |
| 1,21 | `TITLE01` | `AWS Mainframe Modernization` |
| 1,64 / 1,71 | literal / `CURDATE` | `Date :` + `mm/dd/yy` |
| 2,1 / 2,8 | literal / `PGMNAME` | `Prog :` + `COSGN00C` |
| 2,21 | `TITLE02` | `CardDemo` |
| 2,64 / 2,71 | literal / `CURTIME` | `Time :` + `hh:mm:ss` |
| 3,1 / 3,8 | literal / `APPLID` | `AppID:` + CICS applid |
| 3,64 / 3,71 | literal / `SYSID` | `SysID:` + CICS sysid |
| 5,6 | literal | `This is a Credit Card Demo Application for Mainframe Modernization` |
| 7,21 … 15,21 | 9 literals, 42 chars each | the “NATIONAL RESERVE NOTE” banknote art (`COSGN00.bms:100-144`) |
| 17,16 | literal | `Type your User ID and Password, then press ENTER:` |
| 19,29 / 19,43 / 19,52 | literal / `USERID` X(08) unprot / literal | `User ID     :`, input, `(8 Char)` |
| 20,29 / 20,43 / 20,52 | literal / `PASSWD` X(08) unprot `DRK` / literal | `Password    :`, non-display input, `(8 Char)` |
| 23,1 | `ERRMSG` | 78 chars, `COLOR=RED`, `BRT` |
| 24,1 | literal | `ENTER=Sign-on  F3=Exit` |

`USERID` carries `IC` (initial cursor). `PASSWD` is `DRK` — typed characters are
not echoed.

### 2.3 Field validation — `PROCESS-ENTER-KEY` (`COSGN00C.cbl:108-140`)

1. `RECEIVE MAP('COSGN0A')` (`:110-115`).
2. `EVALUATE TRUE` (`:117-130`):
   * `USERIDI = SPACES OR LOW-VALUES` → err flag, `Please enter User ID ...`,
     cursor to `USERID`, redisplay.
   * `PASSWDI = SPACES OR LOW-VALUES` → err flag, `Please enter Password ...`,
     cursor to `PASSWD`, redisplay.
   * otherwise `CONTINUE`.
3. **Unconditionally** (the `EVALUATE` has no `GO TO`): `MOVE FUNCTION UPPER-CASE(USERIDI)`
   to `WS-USER-ID` **and** `CDEMO-USER-ID`, `MOVE FUNCTION UPPER-CASE(PASSWDI)`
   to `WS-USER-PWD` (`:132-136`). Both receiving fields are X(08), so anything
   longer is truncated on the right; anything shorter is space padded.
4. `IF NOT ERR-FLG-ON PERFORM READ-USER-SEC-FILE` (`:138-140`).

Note the blank-password branch leaves `CDEMO-USER-ID` populated even though the
sign-on failed.

### 2.4 File access and routing — `READ-USER-SEC-FILE` (`COSGN00C.cbl:209-257`)

`EXEC CICS READ DATASET('USRSEC  ') INTO(SEC-USER-DATA) RIDFLD(WS-USER-ID)
KEYLENGTH(LENGTH OF WS-USER-ID)` — a keyed read of the 8-byte, space-padded
user id, `RESP` captured.

| `WS-RESP-CD` | Condition | Behaviour |
| --- | --- | --- |
| 0 and `SEC-USR-PWD = WS-USER-PWD` | match | `CDEMO-FROM-TRANID='CC00'`, `CDEMO-FROM-PROGRAM='COSGN00C'`, `CDEMO-USER-ID`, `CDEMO-USER-TYPE=SEC-USR-TYPE`, `CDEMO-PGM-CONTEXT=0`; `XCTL COADM01C` when `CDEMO-USRTYP-ADMIN`, else `XCTL COMEN01C` (`:222-240`) |
| 0 and password differs | mismatch | `Wrong Password. Try again ...`, cursor to `PASSWD`, redisplay (`:241-246`). **The error flag is not set** — harmless because the paragraph ends here. |
| 13 (`NOTFND`) | no such user | err flag, `User not found. Try again ...`, cursor to `USERID` (`:247-251`) |
| any other | read failure | err flag, `Unable to verify the User ...`, cursor to `USERID` (`:252-256`) |

The password comparison is a fixed-length X(08) compare: trailing spaces are
insignificant, embedded and leading spaces are not. Routing is `A` → `COADM01C`
and **everything else** → `COMEN01C`: the 88-level `CDEMO-USRTYP-ADMIN` only
tests for `'A'`, so an unexpected type letter lands on the main menu.

### 2.5 Control flow summary

```
CC00 ─ EIBCALEN=0 ────────────────────────────► send COSGN0A (cursor USERID)
     ├ ENTER ─ blank user id ─────────────────► 'Please enter User ID ...'
     │       ├ blank password ────────────────► 'Please enter Password ...'
     │       └ USRSEC read ─ resp 0 + pwd ok ─► XCTL COADM01C ('A') | COMEN01C
     │                     ├ resp 0 + pwd bad ► 'Wrong Password. Try again ...'
     │                     ├ resp 13 ─────────► 'User not found. Try again ...'
     │                     └ other ───────────► 'Unable to verify the User ...'
     ├ PF3 ───────────────────────────────────► SEND TEXT 'Thank you for using CardDemo application...' + RETURN
     └ other AID ─────────────────────────────► 'Invalid key pressed. Please see below...'
```

---

## 3. COMEN01C — CM00 main menu

### 3.1 Option table — `app/cpy/COMEN02Y.cpy`

`CDEMO-MENU-OPT-COUNT = 11` (`COMEN02Y.cpy:21`), `CDEMO-MENU-OPT OCCURS 12`
(`:94-98`) over `CDEMO-MENU-OPT-NUM` 9(02), `CDEMO-MENU-OPT-NAME` X(35),
`CDEMO-MENU-OPT-PGMNAME` X(08), `CDEMO-MENU-OPT-USRTYPE` X(01):

| # | Name (X(35), shown trimmed) | Program | Usr type |
| --- | --- | --- | --- |
| 1 | Account View | COACTVWC | U |
| 2 | Account Update | COACTUPC | U |
| 3 | Credit Card List | COCRDLIC | U |
| 4 | Credit Card View | COCRDSLC | U |
| 5 | Credit Card Update | COCRDUPC | U |
| 6 | Transaction List | COTRN00C | U |
| 7 | Transaction View | COTRN01C | U |
| 8 | Transaction Add | COTRN02C | U |
| 9 | Transaction Reports | CORPT00C | U |
| 10 | Bill Payment | COBIL00C | U |
| 11 | Pending Authorization View | COPAUS0C | U |

Option 8's original name `Transaction Add (Admin Only)` is commented out
(`COMEN02Y.cpy:69-71`) and its user type is `U`: **no live main-menu option is
admin-only**, so the `'A'` branch at `COMEN01C.cbl:136-143` is unreachable with
the shipped table. The 12th occurrence exists but is never populated.

### 3.2 Screen layout — `app/bms/COMEN01.bms` (`COMEN1A`, 24×80)

Header as in §1.3 with `Tran:`/`Prog:` (5-char labels, values at col 7), title
`Main Menu` at (4,35) (`COMEN01.bms:75-79`), twelve 40-char option lines
`OPTN001`…`OPTN012` at rows 6–17 col 20, `Please select an option :` at (20,15),
the `OPTION` input at (20,41) — `LENGTH=2`, `ATTRB=(FSET,IC,NORM,NUM,UNPROT)`,
`JUSTIFY=(RIGHT,ZERO)` — `ERRMSG` 78 chars `COLOR=RED` at (23,1) and
`ENTER=Continue  F3=Exit` at (24,1).

`BUILD-MENU-OPTIONS` (`COMEN01C.cbl:262-303`) fills the option lines with
`STRING CDEMO-MENU-OPT-NUM '. ' CDEMO-MENU-OPT-NAME` into X(40), i.e.
`01. Account View` followed by the name's X(35) padding.

### 3.3 Entry conditions (`COMEN01C.cbl:75-110`)

* `EIBCALEN = 0` → `CDEMO-FROM-PROGRAM = 'COSGN00C'`, `RETURN-TO-SIGNON-SCREEN`
  (`XCTL` to `CDEMO-TO-PROGRAM`, defaulted to `COSGN00C`, `:196-203`).
* Commarea present and `NOT CDEMO-PGM-REENTER` → set `CDEMO-PGM-REENTER`,
  clear the map, `SEND-MENU-SCREEN` (`:87-90`). This is the first paint after the
  sign-on `XCTL`.
* Commarea present and re-entering → `RECEIVE-MENU-SCREEN` then the AID
  `EVALUATE` (`:92-103`): ENTER → `PROCESS-ENTER-KEY`; PF3 → `CDEMO-TO-PROGRAM =
  'COSGN00C'` + `RETURN-TO-SIGNON-SCREEN`; other → `CCDA-MSG-INVALID-KEY`.
* `EXEC CICS RETURN TRANSID('CM00') COMMAREA(CARDDEMO-COMMAREA)` (`:107-110`).

PF3 `XCTL`s to `COSGN00C` **without a commarea**, so the sign-on screen restarts
at `EIBCALEN = 0` and shows no message.

### 3.4 Option validation (`COMEN01C.cbl:115-134`)

```
PERFORM VARYING WS-IDX FROM LENGTH OF OPTIONI BY -1
        UNTIL OPTIONI(WS-IDX:1) NOT = SPACES OR WS-IDX = 1
MOVE OPTIONI(1:WS-IDX) TO WS-OPTION-X      *> PIC X(02) JUST RIGHT
INSPECT WS-OPTION-X REPLACING ALL ' ' BY '0'
MOVE WS-OPTION-X TO WS-OPTION              *> PIC 9(02)
MOVE WS-OPTION   TO OPTIONO                *> the screen echoes the 2-digit value
```

The loop finds the last non-blank column of the 2-byte input; the right-justified
move then pads on the left and the `INSPECT` turns every remaining blank into
`0`. Therefore `1`, `1␠`, `␠1` and `01` all become `01`; `␠␠` becomes `00`.

Rejection (`:127-134`): non-numeric, `> CDEMO-MENU-OPT-COUNT` (11) or zero →
`Please enter a valid option number...` in the map's default red.

### 3.5 Authorization and dispatch (`COMEN01C.cbl:136-191`)

* `IF CDEMO-USRTYP-USER AND CDEMO-MENU-OPT-USRTYPE(WS-OPTION) = 'A'` →
  `No access - Admin Only option... ` (note the trailing space in the literal,
  `:140`). The test is on `'U'` exactly, so a commarea user type that is neither
  `A` nor `U` skips the check. Unreachable with the shipped table (§3.1). This
  block runs even when the option was already rejected, indexing the table with
  `0` or an out-of-range subscript — with `NOT ERR-FLG-ON` guarding the dispatch
  the outcome is unchanged.
* `IF NOT ERR-FLG-ON` → `EVALUATE TRUE`:
  * `PGMNAME = 'COPAUS0C'` → `EXEC CICS INQUIRE PROGRAM(...) NOHANDLE`.
    `EIBRESP = NORMAL` → set `CDEMO-FROM-TRANID='CM00'`, `CDEMO-FROM-PROGRAM='COMEN01C'`,
    `CDEMO-PGM-CONTEXT=0`, `XCTL`. Otherwise `DFHRED` on `ERRMSGC` and
    `STRING 'This option ' | name DELIMITED BY '  ' | ' is not installed...'`
    → `This option Pending Authorization View is not installed...` (`:147-168`).
  * `PGMNAME(1:5) = 'DUMMY'` → `DFHGREEN` and
    `STRING 'This option ' | name DELIMITED BY SPACE | 'is coming soon ...'`.
    Because the name is delimited by a **single** space only its first word is
    copied and no separator is added: `This option Accountis coming soon ...`.
    No shipped option maps to a `DUMMY` program (`:169-176`).
  * otherwise → `CDEMO-FROM-TRANID='CM00'`, `CDEMO-FROM-PROGRAM='COMEN01C'`
    (moved twice, `:179-180`), `CDEMO-PGM-CONTEXT=0`, `XCTL PGMNAME` (`:177-187`).
    The two commented-out `MOVE`s at `:181-182` show `CDEMO-USER-ID` /
    `CDEMO-USER-TYPE` are deliberately left as the sign-on set them.
* Then `SEND-MENU-SCREEN` (`:190`) — only reached when the `XCTL` did not happen.

The `INQUIRE` exists because `COPAUS0C` is **not** defined in the base
`app/csd/CARDDEMO.CSD` group; it ships with the authorization add-on
(`app/app-authorization-ims-db2-mq/csd/CRDDEMO2.csd`). On a base install the
option therefore always answers “is not installed…”.

---

## 4. COADM01C — CA00 admin menu

### 4.1 Option table — `app/cpy/COADM02Y.cpy`

`CDEMO-ADMIN-OPT-COUNT = 6` (`COADM02Y.cpy:22`, the earlier `VALUE 4` is
commented out at `:21`), `CDEMO-ADMIN-OPT OCCURS 9` (`:56-59`) over
`CDEMO-ADMIN-OPT-NUM` 9(02), `CDEMO-ADMIN-OPT-NAME` X(35),
`CDEMO-ADMIN-OPT-PGMNAME` X(08) — **there is no user-type column**:

| # | Name | Program |
| --- | --- | --- |
| 1 | User List (Security) | COUSR00C |
| 2 | User Add (Security) | COUSR01C |
| 3 | User Update (Security) | COUSR02C |
| 4 | User Delete (Security) | COUSR03C |
| 5 | Transaction Type List/Update (Db2) | COTRTLIC |
| 6 | Transaction Type Maintenance (Db2) | COTRTUPC |

Options 5 and 6 came with the DB2 add-on and, like `COPAUS0C`, are absent from
`app/csd/CARDDEMO.CSD` — which is what the `PGMIDERR` handler is for.

### 4.2 Screen layout — `app/bms/COADM01.bms` (`COADM1A`)

Identical geometry to `COMEN1A` (§3.2) with the title `Admin Menu` at (4,35)
(`COADM01.bms:75-79`) and the same `Please select an option :`, `OPTION`,
`ERRMSG` and `ENTER=Continue  F3=Exit` fields.

### 4.3 Entry conditions and AID handling (`COADM01C.cbl:75-114`)

Same shape as COMEN01C — `EIBCALEN = 0` returns to `COSGN00C`, first entry paints
the map and sets `CDEMO-PGM-REENTER`, re-entry evaluates the AID (ENTER / PF3 /
`CCDA-MSG-INVALID-KEY`), `RETURN TRANSID('CA00')` — plus one addition:
`EXEC CICS HANDLE CONDITION PGMIDERR(PGMIDERR-ERR-PARA)` at `:77-79`.

**There is no user-type check anywhere in COADM01C**: the program relies on
`COSGN00C` only ever routing `'A'` users to `CA00`.

### 4.4 Option validation and dispatch (`COADM01C.cbl:119-158`)

Parsing is character-for-character the same as §3.4 against
`CDEMO-ADMIN-OPT-COUNT` (6); the rejection message is again
`Please enter a valid option number...`.

`IF NOT ERR-FLG-ON`:
* `IF CDEMO-ADMIN-OPT-PGMNAME(WS-OPTION)(1:5) NOT = 'DUMMY'` → `CDEMO-FROM-TRANID='CA00'`,
  `CDEMO-FROM-PROGRAM='COADM01C'`, `CDEMO-PGM-CONTEXT=0`, `XCTL PGMNAME` (`:141-149`).
* Fall-through (i.e. the program name *does* start with `DUMMY`) → `DFHGREEN` and
  `STRING 'This option ' | 'is not installed ...'` → **`This option is not installed ...`**.
  The option name is commented out of the `STRING` (`:153-154`), so unlike
  COMEN01C the admin message never names the option.
* `PGMIDERR-ERR-PARA` (`:270-281`) builds the *same* message when the `XCTL`
  target is not installed, sends the map and `RETURN TRANSID('CA00')`.

---

## 5. `XCTL` / `LINK` targets reachable from this stream

| From | Trigger | Target program | Symbolic? |
| --- | --- | --- | --- |
| COSGN00C | user type `A` | `COADM01C` | literal (`COSGN00C.cbl:232`) |
| COSGN00C | any other user type | `COMEN01C` | literal (`:237`) |
| COMEN01C / COADM01C | `EIBCALEN = 0` or PF3 | `CDEMO-TO-PROGRAM`, defaulted to `COSGN00C` | symbolic, only ever `COSGN00C` (`COMEN01C.cbl:97,199`, `COADM01C.cbl:101,166`) |
| COMEN01C | options 1–11 | `CDEMO-MENU-OPT-PGMNAME(n)` = the 11 programs of §3.1 | symbolic, table driven |
| COADM01C | options 1–6 | `CDEMO-ADMIN-OPT-PGMNAME(n)` = the 6 programs of §4.1 | symbolic, table driven |

No `LINK` is issued by any of the three programs. `COSGN00C` is the only program
that reads a file (USRSEC); the menus are pure screen logic over their copybook
tables.

## 6. Source vs. inventory

`docs/migration/CardDemo_inventory.md` lists S-01 as CC00/CM00/CA00 with the
three programs, three maps and the two menu tables, which matches the source.
Two details the inventory does not carry, both confirmed above: the main menu's
`COPAUS0C` option and the admin menu's two Db2 options point at programs that
are **not** in the base CSD, and `COMEN02Y`'s only admin-only option was
commented out, leaving the `No access - Admin Only option... ` path unreachable.
