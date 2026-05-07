# COBSWAIT (COBW) — Batch Wait Utility: Business Use Case Flow Analysis

## Section 1: Use Case Overview

| Attribute          | Value                                                                                      |
|--------------------|--------------------------------------------------------------------------------------------|
| **Use Case Name**  | Batch Execution Delay                                                                      |
| **Use Case ID**    | UC-COBW-001                                                                                |
| **Actor(s)**       | Batch Job Scheduler (automated); Operations Staff (indirect)                               |
| **Business Goal**  | Introduce a controlled time delay between batch processing steps to allow prior operations (such as file releases or propagation) to complete before subsequent steps begin |
| **Preconditions**  | A batch job stream is running and requires a timed pause between steps; the wait duration (in hundredths of a second) is specified in the job input |
| **Postconditions** | The specified time has elapsed and the batch job stream continues with the next step         |
| **Trigger**        | The batch job scheduler executes the wait step as part of a scheduled job stream             |
| **Data Access**    | None — no files or databases are read or written                                            |
| **Frequency**      | Multiple times daily, once per batch job stream that includes a wait step                   |

### Business Context

The Batch Execution Delay utility is a scheduling component within the CardDemo application's batch processing infrastructure. It serves as a timing mechanism that ensures orderly sequencing of batch operations. When the system transitions between processing phases — for example, after closing online data files and before opening them for batch processing — a controlled delay prevents timing-related failures. This utility accepts a wait duration from the job input and suspends execution for precisely that interval before allowing the job stream to proceed.

This is a **batch utility program** and does **not** operate as an interactive online transaction. It has no user interface, no terminal interaction, and no online navigation context. The transaction identifier COBW is an inventory label only.

```
 ┌─────────────────────────────────────────────────────────┐
 │              CardDemo Functional Domains                │
 ├──────────────┬──────────────┬───────────────────────────┤
 │  Online      │  Admin       │  Batch Processing         │
 │  (CICS)      │  (CICS)      │  (JCL / Scheduler)        │
 │              │              │                           │
 │  Sign-on     │  User Mgmt   │  Open/Close Files         │
 │  Accounts    │  Txn Types   │  Post Transactions        │
 │  Cards       │              │  Interest Calculation     │
 │  Transactions│              │  ┌─────────────────────┐  │
 │  Bill Pay    │              │  │ >>> WAIT STEP <<<   │  │
 │  Reports     │              │  │  (COBSWAIT/COBW)    │  │
 │              │              │  └─────────────────────┘  │
 │              │              │  Data Export/Import       │
 │              │              │  Report Generation        │
 └──────────────┴──────────────┴───────────────────────────┘
```

---

## Section 2: User Journey (Happy Path)

> **Note:** COBSWAIT is a non-interactive batch utility. There is no human user interacting with a terminal screen. The "user" in this context is the batch job scheduler (an automated system actor). The sequence below describes the automated execution flow.

### Sequence Diagram

```
 ┌───────────────┐                    ┌──────────────────┐
 │  Job          │                    │  Batch Wait      │
 │  Scheduler    │                    │  Utility         │
 └───────┬───────┘                    └────────┬─────────┘
         │                                     │
         │  1. Execute wait step               │
         │────────────────────────────────────>│
         │     (provides wait duration         │
         │      in job input)                  │
         │                                     │
         │                           2. Read duration
         │                              from job input
         │                                     │
         │                           3. Convert to
         │                              internal format
         │                                     │
         │                           4. Invoke system
         │                              timer for the
         │                              specified interval
         │                                     │
         │                              ... waiting ...
         │                                     │
         │                           5. Timer expires;
         │                              program ends
         │                                     │
         │  6. Return control                  │
         │<────────────────────────────────────│
         │     (job stream continues)          │
         │                                     │
```

### Step-by-Step Narrative

| Step | Actor          | Action                                         | System Response                                                        |
|------|----------------|-------------------------------------------------|------------------------------------------------------------------------|
| 1    | Job Scheduler  | Executes the wait step within a batch job stream | The wait utility program is loaded and started                         |
| 2    | System         | —                                               | Reads the wait duration value from the job input stream                |
| 3    | System         | —                                               | Converts the input value to internal binary format for timer use       |
| 4    | System         | —                                               | Invokes the system interval timer and suspends execution for the specified duration |
| 5    | System         | —                                               | Timer expires; program execution resumes                               |
| 6    | System         | —                                               | Program terminates normally; control returns to the job scheduler for the next step |

---

## Section 3: Business Rules & Validations

### Input Validation Rules

> **Note:** COBSWAIT performs **no input validation**. The following table documents the implicit data requirements and the consequences of violating them. These are not enforced by the program but represent the expected input contract.

| #  | Field          | Rule                                                      | Error Message Shown to User                                              |
|----|----------------|-----------------------------------------------------------|--------------------------------------------------------------------------|
| V1 | Wait Duration  | Must be an 8-character numeric string (digits 0-9 only)   | Not applicable — no error message is produced; invalid input causes an abnormal program termination (system abend) |
| V2 | Wait Duration  | Must be present in the job input stream                    | Not applicable — missing input causes a job-level error before the program can execute |

### Business Rules

| #   | Rule                                                                                                     |
|-----|----------------------------------------------------------------------------------------------------------|
| BR1 | **Duration Unit:** The wait duration is specified in hundredths of a second (centiseconds). A value of `00003600` means the system waits 36 seconds. |
| BR2 | **Single Execution:** The utility performs exactly one wait operation per invocation and then terminates. It does not loop or accept multiple inputs. |
| BR3 | **Zero Duration:** A wait duration of `00000000` results in an immediate return with no delay.            |
| BR4 | **No Data Modification:** The utility does not read, write, or modify any application data files or databases. Its sole function is to introduce a time delay. |
| BR5 | **Job Stream Continuation:** Upon completion, the utility returns a successful status code to the job scheduler, allowing the next step in the job stream to execute. |

### Decision Table

```
 ┌───────────────────────────────┬──────────────────────────────────────┐
 │  Input Condition              │  System Response                     │
 ├───────────────────────────────┼──────────────────────────────────────┤
 │  Valid numeric 8-digit value  │  Wait for the specified duration,    │
 │  (e.g., "00003600")          │  then terminate normally             │
 ├───────────────────────────────┼──────────────────────────────────────┤
 │  Value is "00000000"          │  Return immediately with no delay    │
 ├───────────────────────────────┼──────────────────────────────────────┤
 │  Non-numeric characters in    │  Abnormal program termination        │
 │  input                        │  (data conversion failure)           │
 ├───────────────────────────────┼──────────────────────────────────────┤
 │  Input stream missing         │  Job-level failure before program    │
 │                               │  execution begins                    │
 ├───────────────────────────────┼──────────────────────────────────────┤
 │  Input stream empty (blank)   │  Abnormal program termination        │
 │                               │  (data conversion failure)           │
 └───────────────────────────────┴──────────────────────────────────────┘
```

---

## Section 4: Data Entities Involved

### Entity Descriptions

> Not applicable — stub program. COBSWAIT does not access any application data entities (files or databases).

| Entity   | Description | Role in This Use Case |
|----------|-------------|----------------------|
| *(none)* | —           | —                    |

### Entity Relationships

> Not applicable — stub program. No data entities are involved.

### Data Fields Displayed

> Not applicable — stub program. There is no screen display and no application data fields are processed.

The only data items used are internal to the program:

| # | Field          | Format                   | Source     | Description                                          |
|---|----------------|--------------------------|------------|------------------------------------------------------|
| 1 | Wait Duration  | 8-character numeric text  | Job input  | The number of hundredths of a second to wait         |

---

## Section 5: Screen / Interface Description

> Not applicable — stub program. COBSWAIT is a batch utility with no terminal screen, no user interface, and no interactive elements.

### Screen Layout

> Not applicable — this program does not use a terminal screen.

### Interface Elements

| Element | Type | Description |
|---------|------|-------------|
| *(none)* | —   | —           |

### Available Actions

> Not applicable — there are no interactive actions. The program is controlled entirely through the batch job input stream.

| Action  | How to Invoke | Description |
|---------|---------------|-------------|
| *(none)* | —            | —           |

---

## Section 6: Alternative & Error Flows

### AF-1: Non-Numeric Input Value

| Step | Description                                                                                        |
|------|----------------------------------------------------------------------------------------------------|
| 1    | The job scheduler executes the wait step with a non-numeric value in the job input (e.g., "ABCDEFGH") |
| 2    | The system attempts to convert the text value to an internal numeric format                         |
| 3    | The conversion fails, causing an abnormal program termination (system abend)                       |
| 4    | The job scheduler records a non-zero return code for the failed step                               |
| 5    | Depending on job stream configuration, subsequent steps may be skipped or conditional paths taken   |

### AF-2: Missing Job Input Stream

| Step | Description                                                                                        |
|------|----------------------------------------------------------------------------------------------------|
| 1    | The job scheduler attempts to execute the wait step, but the job input stream is not defined        |
| 2    | The job control system raises an error before the wait utility program is started                   |
| 3    | The job step fails with a job control error                                                        |
| 4    | Subsequent steps are governed by the job stream's error handling configuration                      |

### AF-3: Empty (Blank) Input Value

| Step | Description                                                                                        |
|------|----------------------------------------------------------------------------------------------------|
| 1    | The job scheduler executes the wait step with a blank/empty value in the job input                  |
| 2    | The system reads blank characters and attempts to convert them to an internal numeric format        |
| 3    | The conversion fails, causing an abnormal program termination (system abend)                       |
| 4    | The job scheduler records a non-zero return code for the failed step                               |

### AF-4: System Timer Failure

| Step | Description                                                                                        |
|------|----------------------------------------------------------------------------------------------------|
| 1    | The job scheduler executes the wait step with a valid numeric duration                              |
| 2    | The system timer subroutine encounters a system-level error                                        |
| 3    | The error propagates upward, causing the wait utility to terminate abnormally                       |
| 4    | The job scheduler records a non-zero return code for the failed step                               |

---

## Section 7: Business Process Context

### Navigation Context

> COBSWAIT does not participate in the online navigation hierarchy. It exists solely within the batch processing domain. The diagram below shows where it fits in the overall application context.

```
 CardDemo Application
 ├── Online Domain (CICS Transactions)
 │   ├── CC00 — Sign-on
 │   ├── CM00 — Main Menu
 │   │   ├── Account View / Update
 │   │   ├── Card List / Detail / Update
 │   │   ├── Transaction List / View / Add
 │   │   ├── Bill Payment
 │   │   └── Reports
 │   └── CA00 — Admin Menu
 │       ├── User List / Add / Update / Delete
 │       └── Transaction Type Management
 │
 └── Batch Domain (JCL Job Streams)
     ├── File Open / Close Jobs
     ├── >>> COBSWAIT — Batch Wait Utility <<<    <── THIS USE CASE
     ├── Transaction Posting (Post-Tran)
     ├── Interest Calculation
     ├── Data Export / Import
     └── Report Generation
```

### Downstream Use Cases

| Destination                     | Trigger                                             | What Happens Next                                              |
|---------------------------------|-----------------------------------------------------|----------------------------------------------------------------|
| Next batch job step in stream   | Wait duration elapses and program terminates normally | The job scheduler proceeds to the next step (e.g., file open, transaction posting, or another processing job) |

### Upstream Use Cases

| Source                          | Navigation Path                                      |
|---------------------------------|------------------------------------------------------|
| Batch job scheduler             | The scheduler invokes the wait step as part of a pre-defined job stream sequence (e.g., after a file close job and before a batch processing job) |

### End-to-End Journey Examples

**Journey 1: Daily Batch Cycle — Online-to-Batch Transition**

```
 Close Online Files ──> COBSWAIT (wait for propagation) ──> Open Files for Batch ──> Post Transactions
```
> *Business scenario:* At end of business day, the system closes online data files, waits for the close to propagate across the storage subsystem, then opens files for batch processing.

**Journey 2: Batch-to-Online Transition**

```
 Complete Batch Processing ──> Close Batch Files ──> COBSWAIT (wait) ──> Open Files for Online
```
> *Business scenario:* After overnight batch processing completes, the system closes batch file locks, waits for propagation, then reopens files for the next business day's online operations.

**Journey 3: Inter-Job Dependency Timing**

```
 Export Data to External System ──> COBSWAIT (wait for transfer) ──> Import Response Data
```
> *Business scenario:* After exporting data to an external system, the batch stream waits a configured interval to allow the external transfer to complete before importing response data.

---

## Section 8: Acceptance Criteria

### Core Functionality

| #     | Given                                                  | When                                        | Then                                                         |
|-------|--------------------------------------------------------|---------------------------------------------|--------------------------------------------------------------|
| AC-01 | A batch job stream includes a wait step with a valid numeric duration of "00003600" (36 seconds) | The job scheduler executes the wait step     | The system pauses execution for exactly 36 seconds and then terminates normally with a successful return code |
| AC-02 | A batch job stream includes a wait step with a duration of "00000000" | The job scheduler executes the wait step     | The system returns immediately with no delay and a successful return code |
| AC-03 | A batch job stream includes a wait step with a duration of "00000100" (1 second) | The job scheduler executes the wait step     | The system pauses for exactly 1 second and then terminates normally |

### Input Validation

| #     | Given                                                  | When                                        | Then                                                         |
|-------|--------------------------------------------------------|---------------------------------------------|--------------------------------------------------------------|
| AC-04 | A batch job stream provides a non-numeric value (e.g., "ABCDEFGH") as the wait duration | The job scheduler executes the wait step     | The program terminates abnormally and the job step records a non-zero return code |
| AC-05 | A batch job stream does not define the required input stream for the wait step | The job scheduler attempts to execute the wait step | The job step fails at the job control level before the program starts |
| AC-06 | A batch job stream provides a blank/empty value as the wait duration | The job scheduler executes the wait step     | The program terminates abnormally and the job step records a non-zero return code |

### Job Stream Integration

| #     | Given                                                  | When                                        | Then                                                         |
|-------|--------------------------------------------------------|---------------------------------------------|--------------------------------------------------------------|
| AC-07 | The wait step completes successfully within a multi-step job stream | The wait duration elapses                    | Control passes to the next job step in the stream without manual intervention |
| AC-08 | The wait step terminates abnormally within a multi-step job stream | The program abends                           | The job scheduler honors the configured error handling (e.g., skip subsequent steps or branch to error recovery) |

### Data Integrity

| #     | Given                                                  | When                                        | Then                                                         |
|-------|--------------------------------------------------------|---------------------------------------------|--------------------------------------------------------------|
| AC-09 | The wait utility executes in a batch job stream        | At any point during execution                | No application data files or databases are read, written, or modified by the wait utility |

---

## Section 9: Technical Cross-Reference (Traceability)

### Business Step to Technical Implementation

| Business Step                                      | Technical Reference                                                         |
|----------------------------------------------------|-----------------------------------------------------------------------------|
| Read wait duration from job input                  | `ACCEPT PARM-VALUE FROM SYSIN` (COBSWAIT.cbl, line 36)                     |
| Convert input to internal numeric format           | `MOVE PARM-VALUE TO MVSWAIT-TIME` (COBSWAIT.cbl, line 37) — PIC X(8) to PIC 9(8) COMP |
| Invoke system timer for the specified interval     | `CALL 'MVSWAIT' USING MVSWAIT-TIME` (COBSWAIT.cbl, line 38)               |
| System timer suspends execution                    | `ASMWAIT BINLBL` macro in MVSWAIT.asm (line 23) — issues interval timer    |
| Program terminates normally                        | `STOP RUN` (COBSWAIT.cbl, line 40)                                         |
| Return code set to zero on success                 | `SR 15,15` in MVSWAIT.asm (line 26) — zeroes register 15 before return     |

### Source File Mapping

| Business Concept                  | Source File(s)                                                              |
|-----------------------------------|-----------------------------------------------------------------------------|
| Batch Wait Utility program        | `app/cbl/COBSWAIT.cbl` (41 lines)                                          |
| System timer subroutine           | `app/asm/MVSWAIT.asm` (31 lines)                                           |
| Job control / scheduling          | `app/jcl/WAITSTEP.jcl` (28 lines)                                          |

### Migration Considerations

| #  | Consideration                                                                                         |
|----|-------------------------------------------------------------------------------------------------------|
| M1 | **Platform-Specific Timer Replacement:** The ASMWAIT macro is an IBM mainframe system service. On a modernized platform, replace with the target language's native sleep mechanism (e.g., `Thread.sleep()` in Java, `time.sleep()` in Python, a Wait state in AWS Step Functions). |
| M2 | **Assembler Dependency Elimination:** MVSWAIT.asm requires an IBM HLASM assembler toolchain. The entire COBOL-to-assembler call chain can be replaced by a single sleep function call in the target language, eliminating the assembler dependency entirely. |
| M3 | **Input Validation Addition:** The current program has no input validation. A modernized version should validate that the input is numeric and within an acceptable range, returning a meaningful error code rather than abending on invalid input. |
| M4 | **SYSIN Input Mechanism:** The `ACCEPT ... FROM SYSIN` pattern reads from JCL DD cards. In a modernized environment, this maps to command-line arguments, environment variables, configuration files, or orchestration parameters (e.g., Step Functions input JSON). |
| M5 | **JCL Job Stream Replacement:** The WAITSTEP.jcl that invokes this program must be replaced with the target scheduling system (AWS Step Functions, Apache Airflow, cron, etc.). |
| M6 | **Evaluate Necessity:** Many mainframe wait steps exist to accommodate VSAM file close/open propagation or JES spool processing. In a cloud-native architecture with event-driven orchestration, explicit wait steps can often be eliminated entirely and replaced with event triggers or completion callbacks. This is the highest-value migration consideration — the entire program may become unnecessary. |
| M7 | **Centisecond Unit Convention:** The wait duration is in centiseconds (hundredths of a second). Modern sleep functions typically use milliseconds or seconds. Ensure correct unit conversion during migration: centiseconds / 100 = seconds; centiseconds * 10 = milliseconds. |
| M8 | **COBOL-to-Assembler Linkage Convention:** The CALL 'MVSWAIT' uses standard OS linkage (R1 parameter list, R13 save area, R14 return). This linkage convention is implicit and invisible in the COBOL source but must be understood if the assembler routine is being analyzed for rehosting compatibility. |

---

## Section 10: Related Use Cases / End-to-End Composition

| Journey Name                            | Use Cases Involved                                              | Business Scenario                                                                |
|-----------------------------------------|-----------------------------------------------------------------|----------------------------------------------------------------------------------|
| Daily Online-to-Batch Transition        | File Close (CLOSEFIL) → **Batch Wait (COBSWAIT)** → File Open (OPENFIL) → Transaction Posting (POSTTRAN) | End-of-day transition from online to batch processing, with wait for file propagation |
| Daily Batch-to-Online Transition        | Batch Processing Complete → File Close → **Batch Wait (COBSWAIT)** → File Open → Online Resume | Morning transition from batch back to online operations                            |
| Inter-System Data Exchange              | Data Export → **Batch Wait (COBSWAIT)** → Data Import           | Timed delay between export and import to allow external system processing          |
| Full Daily Batch Cycle                  | Close Online Files → **Batch Wait** → Open Batch Files → Post Transactions → Calculate Interest → Generate Reports → Close Batch Files → **Batch Wait** → Open Online Files | Complete overnight batch processing cycle with wait steps at each transition boundary |

> **Note:** Individual use case flows for the related batch programs (CLOSEFIL, OPENFIL, POSTTRAN, etc.) must be composed into journey documents for complete end-to-end validation. Those use case documents may not yet exist.

---

## Document Footer — Summary Statistics

| Attribute                    | Value                           |
|------------------------------|---------------------------------|
| **Source Program**           | COBSWAIT.cbl (41 lines)        |
| **Transaction ID**           | COBW                            |
| **Program Type**             | Batch COBOL Utility             |
| **Data Source**               | Technical Flow Analysis + COBOL source verification |
| **Acceptance Criteria**      | 9 (AC-01 through AC-09)        |
| **Business Rules**           | 5 (BR1 through BR5)            |
| **Validation Rules**         | 2 (V1, V2)                     |
| **Alternative / Error Flows**| 4 (AF-1 through AF-4)          |
| **Migration Considerations** | 8 (M1 through M8)              |
