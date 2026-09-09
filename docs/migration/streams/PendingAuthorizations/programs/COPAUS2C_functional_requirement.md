# COPAUS2C — Functional Requirement (fraud persistence, DB2)

Source: `app/app-authorization-ims-db2-mq/cbl/COPAUS2C.cbl`, table `ddl/AUTHFRDS.ddl`.
Migrated to `com.carddemo.pendingauth.service.AuthFraudService`, writing
`com.carddemo.common.domain.AuthFraudRecord` (table `auth_fraud`).

## Purpose
Called (`EXEC CICS LINK`) by COPAUS1C to record — or un-record — a fraud mark for one authorization
in DB2 `CARDDEMO.AUTHFRDS`.

## Interface (COMMAREA)
```
acct id 9(11) | cust id 9(9) | PAUTDTL1 image | action X(01) F|R
                                              | status X(01) S|F  (out)
                                              | message X(50)     (out)
```

## Requirements
* **FR-PA-F01** insert all 26 columns keyed `(CARD_NUM, AUTH_TS)`.
* **FR-PA-F02** timestamp reconstruction `YY-MM-DD HH.MI.SS.mmm000` from the original date and
  `999999999 - PA-AUTH-TIME-9C`.
* **FR-PA-F03** `ADD SUCCESS`.
* **FR-PA-F04** duplicate key → update `AUTH_FRAUD` and `FRAUD_RPT_DATE`, `UPDT SUCCESS`.
* **FR-PA-F05** error messages ` SYSTEM ERROR DB2: CODE:<+nnnnnn>, STATE: <+nnnnnnnnn>` and
  ` UPDT ERROR DB2: CODE:<+nnnnnn>, STATE: <+nnnnnnnnn>`.
* **FR-PA-F06** `PA-FRAUD-RPT-DATE` (`MM/DD/YY`) written back to the caller's record.

## Column mapping (COPAUS2C:113-139)
`CARD_NUM`←`PA-CARD-NUM`, `AUTH_TS`←derived, `AUTH_TYPE`, `CARD_EXPIRY_DATE`, `MESSAGE_TYPE`,
`MESSAGE_SOURCE`, `AUTH_ID_CODE`, `AUTH_RESP_CODE`, `AUTH_RESP_REASON`, `PROCESSING_CODE`,
`TRANSACTION_AMT`, `APPROVED_AMT`, `MERCHANT_CATAGORY_CODE`, `ACQR_COUNTRY_CODE`, `POS_ENTRY_MODE`,
`MERCHANT_ID`, `MERCHANT_NAME` (VARCHAR), `MERCHANT_CITY`, `MERCHANT_STATE`, `MERCHANT_ZIP`,
`TRANSACTION_ID`, `MATCH_STATUS`, `AUTH_FRAUD`←action, `FRAUD_RPT_DATE`←`CURRENT DATE`,
`ACCT_ID`, `CUST_ID`.

## Migration notes
`SQLCODE -803` (duplicate key) is a normal path: the migrated service looks the row up by its
composite id first and updates it when present, which produces the same two outcomes
(`ADD SUCCESS` / `UPDT SUCCESS`) without relying on a database error code. Any persistence exception
is mapped to the ` SYSTEM ERROR DB2: CODE:` / ` UPDT ERROR DB2: CODE:` form with the sqlcode and
sqlstate of the underlying `SQLException` (`0`/`00000` when the driver reports none), so the caller's
error handling and the screen text are unchanged.
