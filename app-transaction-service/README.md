# app-transaction-service

Spring Boot 3 / Java 17 port of the CardDemo online **Add Transaction** flow, implemented on the
mainframe by the CICS COBOL program [`app/cbl/COTRN02C.cbl`](../app/cbl/COTRN02C.cbl)
(CICS transaction `CT02`, BMS mapset `COTRN02`, map `COTRN2A`).

Only the transaction add flow is ported. `COTRN02C` enforces no role or admin check, so the
endpoint does not either.

## Endpoint

`POST /transactions`

```json
{
  "accountId": "",
  "cardNumber": "4111111111111111",
  "typeCode": "01",
  "categoryCode": "0001",
  "source": "POS TERM",
  "description": "Purchase at Abshire-Lowe",
  "amount": "-00000100.50",
  "origDate": "2024-03-01",
  "procDate": "2024-03-02",
  "merchantId": "123456789",
  "merchantName": "Abshire-Lowe",
  "merchantCity": "Chicago",
  "merchantZip": "60601",
  "confirm": true
}
```

Responses:

| Case | Status | Body |
| --- | --- | --- |
| Added | 201 | `{"message":"Transaction added successfully.  Your Tran ID is 0000000000000043.","tranId":"0000000000000043","added":true}` |
| `confirm` false (COBOL `CONFIRMI` not `Y`) | 200 | `{"message":"Confirm to add this transaction...","tranId":null,"added":false}` |
| Any validation / lookup error | 400 | `{"message":"Card Number NOT found...","field":"cardNumber"}` |
| Write failure other than duplicate key | 500 | `{"message":"Unable to Add Transaction...","field":null}` |

The 400 bodies carry the exact COBOL message text (including the trailing `...`) so parity with
the 3270 `ERRMSGO` field is auditable.

## Mapping from COTRN02C

| COBOL paragraph / construct | Java |
| --- | --- |
| `PROCESS-ENTER-KEY` (validate, then check `CONFIRMI`) | `TransactionAddService.addTransaction` |
| `VALIDATE-INPUT-KEY-FIELDS` | `TransactionAddService.validateInputKeyFields` |
| `READ-CXACAIX-FILE` (account id → card number) | `CardXrefRepository.findByAcctId` |
| `READ-CCXREF-FILE` (card number → account id) | `CardXrefRepository.findByCardNum` |
| `VALIDATE-INPUT-DATA-FIELDS` | `TransactionAddService.validateInputDataFields` |
| `CALL 'CSUTLDTC'` date validity check | strict `LocalDate.parse` with `uuuu-MM-dd` in `requireDate` |
| `ADD-TRANSACTION` (`STARTBR` at `HIGH-VALUES` + `READPREV` + `ENDBR`, then `ADD 1`) | `TransactionRepository.findMaxTranId` + `nextTranId` (max + 1, zero padded to 16) |
| `WRITE-TRANSACT-FILE` | `TransactionAddService.writeTransaction` → `TransactionRepository.save` |
| `DFHRESP(DUPKEY/DUPREC)` → `Tran ID already exist...` | duplicate check plus `DataIntegrityViolationException` handling |
| `SEND-TRNADD-SCREEN` with `WS-ERR-FLG = 'Y'` | `TransactionValidationException` → HTTP 400 via `TransactionExceptionHandler` |
| Map `COTRN2A` input fields (`ACTIDINI`, `CARDNINI`, `TTYPCDI`, …) | `AddTransactionRequest` |
| Map field `ERRMSGO` + generated `TRAN-ID` | `AddTransactionResponse` |
| Copybook `CVTRA05Y` (`TRAN-RECORD`, 350 bytes) | `entity.Transaction` → table `transact` |
| Copybook `CVACT03Y` (`CARD-XREF-RECORD`, 50 bytes) | `entity.CardXref` → table `ccxref` |

Screen-only paragraphs are intentionally not ported: `SEND-TRNADD-SCREEN`,
`RECEIVE-TRNADD-SCREEN`, `POPULATE-HEADER-INFO`, `RETURN-TO-PREV-SCREEN`,
`CLEAR-CURRENT-SCREEN` (PF4), `COPY-LAST-TRAN-DATA` (PF5) and `INITIALIZE-ALL-FIELDS`.

### Business rules preserved

Validation runs in the COBOL order and stops on the first error:

1. Key fields: `accountId` wins if present; it must be numeric and must resolve through the
   account index (`Account ID NOT found...`). Otherwise `cardNumber` must be numeric and must
   resolve through the card key (`Card Number NOT found...`). Neither supplied →
   `Account or Card Number must be entered...`.
2. Each detail field must be non-empty, with the COBOL per-field message.
3. `typeCode` and `categoryCode` numeric; `merchantId` numeric (checked last, as in the COBOL).
4. `amount` must match `[-+]` + 8 digits + `.` + 2 digits (`PIC +99999999.99`); the sign is
   mandatory, e.g. `-00000100.50`.
5. `origDate` / `procDate` must be `YYYY-MM-DD` in layout and a real calendar date.
6. New key = current max `tran_id` + 1, zero padded to 16 characters. An empty table yields
   `0000000000000001`, matching the COBOL `MOVE ZEROS TO TRAN-ID` on `ENDFILE`.

## Data store assumption

The VSAM KSDS files are replaced by PostgreSQL tables:

| VSAM | Table |
| --- | --- |
| `TRANSACT` | `transact` (PK `tran_id`) |
| `CCXREF` | `ccxref` (PK `xref_card_num`) |
| `CXACAIX` (alternate index on account id) | index `cxacaix` on `ccxref (xref_acct_id)` |

DDL: [`src/main/resources/schema.sql`](src/main/resources/schema.sql).

### Create and seed

```bash
createdb carddemo
psql carddemo -f src/main/resources/schema.sql
```

Seed `ccxref` from the fixed-width ASCII extract `app/data/ASCII/cardxref.txt`
(card number 1-16, customer id 17-25, account id 26-36):

```bash
psql carddemo -c "CREATE TEMP TABLE xref_raw (line text);
\\copy xref_raw FROM '../app/data/ASCII/cardxref.txt'
INSERT INTO ccxref (xref_card_num, xref_cust_id, xref_acct_id)
SELECT substr(line,1,16), substr(line,17,9)::bigint, substr(line,26,11)::bigint FROM xref_raw;"
```

`transact` may start empty (the service then generates `0000000000000001`) or be loaded from
`app/data/ASCII/dailytran.txt`, which follows the same `CVTRA05Y` layout: `tran_id` 1-16,
`tran_type_cd` 17-18, `tran_cat_cd` 19-22, `tran_source` 23-32, `tran_desc` 33-132,
`tran_amt` 133-143 (signed, 2 implied decimals), `tran_merchant_id` 144-152,
`tran_merchant_name` 153-202, `tran_merchant_city` 203-252, `tran_merchant_zip` 253-262,
`tran_card_num` 263-278, `tran_orig_ts` 279-304, `tran_proc_ts` 305-330.

## Run

```bash
export CARDDEMO_DB_URL=jdbc:postgresql://localhost:5432/carddemo
export CARDDEMO_DB_USER=carddemo
export CARDDEMO_DB_PASSWORD=carddemo
mvn spring-boot:run        # listens on port 8082
```

## Test

```bash
mvn test
```

`TransactionAddServiceTest` covers missing key fields, non-numeric account/card, xref not found,
every empty-field message, non-numeric type/category/merchant id, bad amount and date formats,
invalid calendar dates, the unconfirmed prompt, duplicate key, write failure, and the happy path
producing max + 1.
