# COCRDSEC — CDV1 Transaction Flow Analysis

## STUB PROGRAM NOTICE

> **COCRDSEC does not have a source file in the repository.**
> This analysis is based entirely on the CSD (CICS System Definition) resource
> definitions and cross-reference searches against all other programs in the
> CardDemo application. No COBOL source code, copybooks, BMS maps, or VSAM file
> operations can be attributed directly to this program. All information below is
> inferred from the CSD and the broader application context.

---

## Section 1: Flow Overview

| Attribute            | Value                                               |
|----------------------|-----------------------------------------------------|
| **Transaction ID**   | CDV1                                                |
| **Program**          | COCRDSEC                                            |
| **CSD Description**  | CREDIT CARD SEARCH                                  |
| **Transaction Desc** | DEVELOPER TRANSACTION - 1                           |
| **CSD Group**        | CARDDEMO                                            |
| **Language**          | Not specified in CSD (implied COBOL by convention)  |
| **Source File**       | **NOT AVAILABLE** — no `COCRDSEC.cbl` in `app/cbl/` |
| **Status**           | Stub / Placeholder                                  |

### 1.1 CSD Program Definition

```
DEFINE PROGRAM(COCRDSEC) GROUP(CARDDEMO)
DESCRIPTION(CREDIT CARD SEARCH)
       RELOAD(NO) RESIDENT(NO) USAGE(NORMAL) USELPACOPY(NO)
       STATUS(ENABLED) CEDF(YES) DATALOCATION(ANY) EXECKEY(USER)
       CONCURRENCY(QUASIRENT) API(CICSAPI) DYNAMIC(NO)
       EXECUTIONSET(FULLAPI) JVM(NO) DEFINETIME(22/03/15 10:11:47)
       CHANGETIME(22/03/15 10:11:56) CHANGEUSRID(AWSUSER)
       CHANGEAGENT(CSDAPI) CHANGEAGREL(0730)
```

### 1.2 CSD Transaction Definition

```
DEFINE TRANSACTION(CDV1) GROUP(CARDDEMO)
DESCRIPTION(DEVELOPER TRANSACTION - 1)
       PROGRAM(COCRDSEC) TWASIZE(0) PROFILE(DFHCICST) STATUS(ENABLED)
       TASKDATALOC(ANY) TASKDATAKEY(USER) STORAGECLEAR(NO)
       RUNAWAY(SYSTEM) SHUTDOWN(DISABLED) ISOLATE(YES) DYNAMIC(NO)
       ROUTABLE(NO) PRIORITY(1) TRANCLASS(DFHTCL00) DTIMOUT(NO)
       RESTART(NO) SPURGE(YES) TPURGE(YES) DUMP(YES) TRACE(YES)
       CONFDATA(NO) OTSTIMEOUT(NO) ACTION(BACKOUT) WAIT(YES)
       WAITTIME(0,0,0) RESSEC(NO) CMDSEC(NO)
       DEFINETIME(22/03/15 10:13:20) CHANGETIME(22/04/01 23:35:42)
       CHANGEUSRID(AWSUSER) CHANGEAGENT(CSDAPI) CHANGEAGREL(0730)
```

### 1.3 Navigation Context (ASCII Diagram)

Since no source exists, the exact navigation path is unknown. Based on the CSD
description ("CREDIT CARD SEARCH") and the transaction description ("DEVELOPER
TRANSACTION - 1"), the *probable* placement within the CardDemo navigation
hierarchy would be:

```
 +-----------+       +-------------+       +-------------+
 | CC00      |       | CM00 / CA00 |       | CDV1        |
 | COSGN00C  | ----> | COMEN01C or | ----> | COCRDSEC    |
 | Sign-On   |       | COADM01C    |       | (STUB)      |
 +-----------+       | Main Menu   |       | Credit Card |
                     +-------------+       | Search      |
                                           +-------------+
```

**Note:** No program in the repository contains an `XCTL` or `LINK` reference
to `COCRDSEC`. This transaction may have been intended to be invoked directly
from the terminal (by typing `CDV1`) rather than via menu navigation, or the
navigation logic was never implemented.

### 1.4 Pseudo-Conversational Lifecycle

Unknown — no source code available to determine the send/receive/return pattern.
Typical CardDemo programs use:
1. Check `EIBCALEN` — if 0, first entry; display initial map
2. On re-entry, evaluate `EIBAID` for function keys
3. `EXEC CICS RETURN TRANSID('CDV1') COMMAREA(...)` for pseudo-conversational return

---

## Section 2: Programs Involved

| Program    | Type              | Function             | Caller | Call Method | Source Available |
|------------|-------------------|----------------------|--------|-------------|-----------------|
| COCRDSEC   | CICS Online (Stub)| Credit Card Search   | N/A    | Transaction CDV1 | **NO** — source not in repo |

### 2.1 Cross-Reference Search Results

A comprehensive search of all COBOL source files in `app/cbl/` and all
copybooks in `app/cpy/` found **zero references** to `COCRDSEC` or `CDV1`:

- No `XCTL PROGRAM('COCRDSEC')` found in any program
- No `LINK PROGRAM('COCRDSEC')` found in any program
- No `MOVE 'COCRDSEC'` found in any program
- No `'CDV1'` literal found in any program or copybook

This confirms the program is a standalone stub with no integration into the
existing application flow.

### 2.2 Relationship to Other Credit Card Programs

The CSD description "CREDIT CARD SEARCH" suggests COCRDSEC was intended to
provide card search functionality. The repository contains these related
programs with source:

| Program    | Transaction | CSD Description          | Source |
|------------|-------------|--------------------------|--------|
| COCRDLIC   | CCLI        | LIST CARDS               | `app/cbl/COCRDLIC.cbl` |
| COCRDSLC   | CCDL        | VIEW CARD DETAIL         | `app/cbl/COCRDSLC.cbl` |
| COCRDUPC   | —           | CREDIT CARD UPDATE SCREEN| `app/cbl/COCRDUPC.cbl` |
| **COCRDSEC** | **CDV1**  | **CREDIT CARD SEARCH**   | **NOT AVAILABLE** |

---

## Section 3: Copybooks

No copybooks can be attributed to COCRDSEC since no source exists.

If implemented, COCRDSEC would likely use the following copybooks (by analogy
with other CardDemo CICS programs):

| Copybook   | Probable Purpose                        | Used by Other Card Programs |
|------------|-----------------------------------------|-----------------------------|
| COCOM01Y   | COMMAREA structure (navigation, user)   | All online programs         |
| DFHAID     | AID key constants (system copybook)     | All online programs         |
| DFHBMSCA   | BMS attribute constants (system copybook)| All online programs        |

### 3.1 COMMAREA Structure (COCOM01Y.cpy)

The standard CardDemo COMMAREA that would be passed to/from COCRDSEC:

```
01 CARDDEMO-COMMAREA.
   05 CDEMO-GENERAL-INFO.
      10 CDEMO-FROM-TRANID             PIC X(04).
      10 CDEMO-FROM-PROGRAM            PIC X(08).
      10 CDEMO-TO-TRANID               PIC X(04).
      10 CDEMO-TO-PROGRAM              PIC X(08).
      10 CDEMO-USER-ID                 PIC X(08).
      10 CDEMO-USER-TYPE               PIC X(01).
         88 CDEMO-USRTYP-ADMIN         VALUE 'A'.
         88 CDEMO-USRTYP-USER          VALUE 'U'.
      10 CDEMO-PGM-CONTEXT             PIC 9(01).
         88 CDEMO-PGM-ENTER            VALUE 0.
         88 CDEMO-PGM-REENTER          VALUE 1.
   05 CDEMO-CUSTOMER-INFO.
      10 CDEMO-CUST-ID                 PIC 9(09).
      10 CDEMO-CUST-FNAME              PIC X(25).
      10 CDEMO-CUST-MNAME              PIC X(25).
      10 CDEMO-CUST-LNAME              PIC X(25).
   05 CDEMO-ACCOUNT-INFO.
      10 CDEMO-ACCT-ID                 PIC 9(11).
      10 CDEMO-ACCT-STATUS             PIC X(01).
   05 CDEMO-CARD-INFO.
      10 CDEMO-CARD-NUM                PIC 9(16).
   05 CDEMO-MORE-INFO.
      10 CDEMO-LAST-MAP                PIC X(7).
      10 CDEMO-LAST-MAPSET             PIC X(7).
```

---

## Section 4: VSAM File Operations

No VSAM file operations can be documented — source code is not available.

Based on the CSD description "CREDIT CARD SEARCH", COCRDSEC would likely
access:

| File (DD)  | Description                       | Probable Access Pattern |
|------------|-----------------------------------|------------------------|
| CARDDAT    | Credit card data (VSAM KSDS)      | READ / BROWSE          |
| CCXREF     | Card-to-account cross-reference   | READ                   |
| CUSTDAT    | Customer data                     | READ                   |
| ACCTDAT    | Account data                      | READ                   |

**Note:** These are speculative based on the program description and how other
card-related programs (COCRDLIC, COCRDSLC) access data. No actual EXEC CICS
READ/WRITE/BROWSE commands can be verified.

---

## Section 5: BMS Screen Map

No BMS map is associated with COCRDSEC in the CSD. There is no `DEFINE MAPSET`
for a screen that would be named `COCRDSEC` or similar, and no BMS source file
was found.

If implemented, a search screen would typically contain:
- Search criteria input fields (card number, account, customer name)
- A results display area
- Standard function key assignments (PF3=Back, PF7/PF8=Scroll, Enter=Search)

---

## Section 6: COMMAREA Structure

### 6.1 COMMAREA Flow (Inferred)

Since COCRDSEC is a stub, the COMMAREA flow cannot be traced from source.
In a typical CardDemo navigation:

```
+------------------+                    +------------------+
| Calling Program  |   COMMAREA         | COCRDSEC (Stub)  |
| (e.g., COMEN01C) | ----------------> | CDV1             |
|                  |  CDEMO-TO-PROGRAM  |                  |
|                  |  = 'COCRDSEC'      | Would read:      |
|                  |  CDEMO-TO-TRANID   |  - USER-ID       |
|                  |  = 'CDV1'          |  - USER-TYPE     |
|                  |  CDEMO-USER-ID     |  - PGM-CONTEXT   |
|                  |  CDEMO-USER-TYPE   |                  |
+------------------+                    +------------------+
```

### 6.2 Field Read/Write Ownership

Unknown — cannot be determined without source code.

---

## Section 7: Business Logic Flow

**Cannot be documented** — no source code exists.

Based on the CSD description "CREDIT CARD SEARCH" and the pattern of other
CardDemo programs, the intended business logic would likely be:

1. **Entry:** Receive COMMAREA from calling program or direct terminal input
2. **Display:** Show a search criteria screen (card number, customer name, etc.)
3. **Input:** Accept search parameters from the user
4. **Validation:** Validate input fields
5. **Search:** Browse VSAM files matching criteria
6. **Results:** Display matching credit card records
7. **Navigation:** Allow selection of a result to view details (XCTL to COCRDSLC)
8. **Return:** PF3 to return to calling menu

**This is entirely speculative.** The actual implementation may differ or may
never have been completed.

---

## Section 8: Complete File Inventory

### 8.1 Files That Exist

| File Type       | File Name       | Path                          | Status    |
|-----------------|-----------------|-------------------------------|-----------|
| CSD Definition  | CARDDEMO.CSD    | `app/csd/CARDDEMO.CSD`       | EXISTS    |

### 8.2 Files That Do NOT Exist

| File Type       | Expected Name   | Expected Path                 | Status       |
|-----------------|-----------------|-------------------------------|--------------|
| COBOL Source    | COCRDSEC.cbl    | `app/cbl/COCRDSEC.cbl`       | NOT FOUND    |
| BMS Map Source  | (none defined)  | `app/bms/`                    | NOT DEFINED  |
| BMS Copybook    | (none defined)  | `app/cpy/`                    | NOT DEFINED  |

### 8.3 Related Files (Context)

| File Type       | File Name       | Path                          | Purpose          |
|-----------------|-----------------|-------------------------------|------------------|
| Copybook        | COCOM01Y.cpy    | `app/cpy/COCOM01Y.cpy`       | COMMAREA layout  |

---

## Section 9: Transaction Record Layout

No record layouts can be documented for COCRDSEC — source code is not available,
and no VSAM I/O operations can be verified.

---

## Section 10: Observations

### 10.1 What the CSD Definition Tells Us

1. **Program is registered and enabled** — COCRDSEC is fully defined in the CSD
   with STATUS(ENABLED), meaning the CICS region would attempt to load it
2. **Transaction is active** — CDV1 is defined with STATUS(ENABLED) and linked
   to COCRDSEC
3. **Developer-oriented** — The transaction description "DEVELOPER TRANSACTION - 1"
   suggests this was created for development/testing purposes rather than
   end-user functionality
4. **No TRANSID on program** — Unlike some programs (e.g., COACTVWC has
   TRANSID(CAVW)), COCRDSEC has no TRANSID in its program definition. The
   transaction association is only via the CDV1 transaction definition
5. **Standard CICS settings** — QUASIRENT concurrency, CICSAPI, FULLAPI
   execution set — consistent with all other CardDemo online programs

### 10.2 Gaps and Migration Considerations

1. **Missing source is the primary gap** — Without source code, the program
   cannot be migrated. It must either be:
   - Implemented from scratch based on the "CREDIT CARD SEARCH" description
   - Removed from the CSD if the functionality is no longer needed
   - Mapped to an existing program if the CSD entry is a duplicate/alias
2. **No integration with menu system** — No existing program references
   COCRDSEC, so adding it to the menu would require changes to COMEN01C
   (regular menu) or COADM01C (admin menu)
3. **Potential overlap with COCRDSLC** — COCRDSLC (VIEW CARD DETAIL) has a
   CSD description of "VIEW CARD DETAIL" which is functionally related.
   COCRDSEC ("CREDIT CARD SEARCH") may have been intended as the search/list
   screen that feeds into COCRDSLC for detailed viewing
4. **CSD cleanup recommended** — If COCRDSEC is truly a stub that was never
   implemented, the CSD entries for both the program and CDV1 transaction
   should be reviewed for removal or implementation during migration

### 10.3 Timeline Analysis

- **Program defined:** 2022-03-15 10:11:47
- **Program last changed:** 2022-03-15 10:11:56 (9 seconds later — initial creation only)
- **Transaction defined:** 2022-03-15 10:13:20
- **Transaction last changed:** 2022-04-01 23:35:42 (minor update ~2 weeks later)

The program definition was never updated after initial creation, supporting the
conclusion that it is a stub/placeholder.

---

## Section 11: Comparison — Stub vs. Implemented CardDemo Programs

| Attribute                | COCRDSEC (Stub)     | Typical CardDemo Program (e.g., COCRDLIC) |
|--------------------------|---------------------|-------------------------------------------|
| COBOL Source             | Not available       | Full source in `app/cbl/`                 |
| BMS Map                  | Not defined         | Defined in CSD and `app/bms/`             |
| Copybook references      | None (inferred)     | 5-15 per program                          |
| VSAM file access         | None (speculated)   | 2-5 files per program                     |
| Menu integration         | Not referenced      | Referenced via XCTL from menu programs    |
| COMMAREA usage           | Unknown             | Full read/write of navigation fields      |
| Pseudo-conversational    | Unknown             | RETURN TRANSID pattern                    |
| Function key handling    | Unknown             | EVALUATE EIBAID with PF3, PF7, PF8, etc. |
| Error handling           | Unknown             | RESP/RESP2 checking on all CICS commands  |
| Migration complexity     | **Cannot assess**   | Medium (typical CICS-to-web conversion)   |

---

*Analysis generated: 2026-05-07*
*Repository: choikh0423/aws-mainframe-modernization-carddemo*
*Branch: demos/cobol-full-docs*
*Analyst note: This is a STUB analysis — source code for COCRDSEC is not
available in the repository. All technical details beyond the CSD definition
are inferred or speculative.*
