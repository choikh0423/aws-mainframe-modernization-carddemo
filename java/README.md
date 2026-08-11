# CardDemo - Transaction Add (CT02) in Java

Spring Boot migration of the online **Add Transaction** flow: COBOL program
`app/cbl/COTRN02C.cbl`, CICS transaction `CT02`, BMS mapset `COTRN02`.

## Mapping from the mainframe artefacts

| Mainframe | Java |
| --- | --- |
| `TRAN-RECORD` (`app/cpy/CVTRA05Y.cpy`) | `Transaction` entity, table `transact` |
| `CARD-XREF-RECORD` (`app/cpy/CVACT03Y.cpy`) | `CardXref` entity, table `ccxref` |
| `CCXREF` read (by card number) | `CardXrefRepository.findById` |
| `CXACAIX` read (by account id) | `CardXrefRepository.findFirstByXrefAcctId` |
| `STARTBR`/`READPREV`/`ENDBR` on `TRANSACT` | `findFirstByOrderByTranIdDesc` |
| `WRITE` to `TRANSACT` | `TransactionRepository.saveAndFlush` |
| `CSUTLDTC` date validation | strict `LocalDate` parsing |
| ENTER key (`PROCESS-ENTER-KEY`) | `POST /api/transactions/add` |
| PF5 (`COPY-LAST-TRAN-DATA`) | `POST /api/transactions/copy-last` |

Screen fields keep their BMS lengths, so COBOL `IS NUMERIC` tests are
reproduced against the space padded field. Validation stops at the first
failing rule, matching `SEND-TRNADD-SCREEN` returning to CICS, and all
messages are byte-identical to the COBOL literals.

## Run

```bash
cd java
mvn spring-boot:run
```

```bash
curl -s -X POST localhost:8080/api/transactions/add -H 'Content-Type: application/json' -d '{
  "acctId":"00000000011","typeCd":"01","catCd":"0001","source":"POS",
  "description":"Coffee","amount":"-00000012.34",
  "origDate":"2024-01-15","procDate":"2024-01-16",
  "merchantId":"000000123","merchantName":"ACME","merchantCity":"NYC",
  "merchantZip":"10001","confirm":"Y"}'
```

## Test

```bash
cd java && mvn test
```
