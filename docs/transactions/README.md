# Tesco | CardDemo CICS Transaction Index

> **Tesco | Mainframe Application Documentation** &nbsp;|&nbsp; Classification: **Tesco Internal**

One document per CICS transaction defined in the CardDemo CSD files and listed in the root [`README.md`](../../README.md#application-inventory). Each document follows [`docs/templates/TRANSACTION_DOC_TEMPLATE.md`](../templates/TRANSACTION_DOC_TEMPLATE.md). Values that still need confirmation are marked `TODO` (`grep -rn TODO docs/transactions`).

## Base application

CSD: [`app/csd/CARDDEMO.CSD`](../../app/csd/CARDDEMO.CSD)

| Tran ID | Program | BMS mapset | Function | Notes |
|:--|:--|:--|:--|:--|
| [`CC00`](CC00.md) | `COSGN00C` | `COSGN00` | Signon Screen | - |
| [`CM00`](CM00.md) | `COMEN01C` | `COMEN01` | Main Menu | - |
| [`CAVW`](CAVW.md) | `COACTVWC` | `COACTVW` | Account View | - |
| [`CAUP`](CAUP.md) | `COACTUPC` | `COACTUP` | Account Update | - |
| [`CCLI`](CCLI.md) | `COCRDLIC` | `COCRDLI` | Credit Card List | - |
| [`CCDL`](CCDL.md) | `COCRDSLC` | `COCRDSL` | Credit Card View | - |
| [`CCUP`](CCUP.md) | `COCRDUPC` | `COCRDUP` | Credit Card Update | - |
| [`CT00`](CT00.md) | `COTRN00C` | `COTRN00` | Transaction List | - |
| [`CT01`](CT01.md) | `COTRN01C` | `COTRN01` | Transaction View | - |
| [`CT02`](CT02.md) | `COTRN02C` | `COTRN02` | Transaction Add | - |
| [`CR00`](CR00.md) | `CORPT00C` | `CORPT00` | Transaction Reports | - |
| [`CB00`](CB00.md) | `COBIL00C` | `COBIL00` | Bill Payment | - |
| [`CA00`](CA00.md) | `COADM01C` | `COADM01` | Admin Menu | - |
| [`CU00`](CU00.md) | `COUSR00C` | `COUSR00` | List Users | - |
| [`CU01`](CU01.md) | `COUSR01C` | `COUSR01` | Add User | - |
| [`CU02`](CU02.md) | `COUSR02C` | `COUSR02` | Update User | - |
| [`CU03`](CU03.md) | `COUSR03C` | `COUSR03` | Delete User | - |

## IMS-DB2-MQ: Pending Authorizations

CSD: [`app/app-authorization-ims-db2-mq/csd/CRDDEMO2.csd`](../../app/app-authorization-ims-db2-mq/csd/CRDDEMO2.csd)

| Tran ID | Program | BMS mapset | Function | Notes |
|:--|:--|:--|:--|:--|
| [`CPVS`](CPVS.md) | `COPAUS0C` | `COPAU00` | Pending Authorization Summary | Read IMS and VSAM |
| [`CPVD`](CPVD.md) | `COPAUS1C` | `COPAU01` | Pending Authorization Details | Update IMS and Insert DB2 |
| [`CP00`](CP00.md) | `COPAUA0C` | - | Process Authorization Requests | MQ trigger, request and response; Insert and Update to IMS |

## Db2: Transaction Type Mgmt

CSD: [`app/app-transaction-type-db2/csd/CRDDEMOD.csd`](../../app/app-transaction-type-db2/csd/CRDDEMOD.csd)

| Tran ID | Program | BMS mapset | Function | Notes |
|:--|:--|:--|:--|:--|
| [`CTTU`](CTTU.md) | `COTRTUPC` | `COTRTUP` | Tran Type add/edit | Update and insert on Db2 |
| [`CTLI`](CTLI.md) | `COTRTLIC` | `COTRTLI` | Tran Type list/update/delete | Demonstrates cursor and delete in Db2 |

## MQ Integration

CSD: [`app/app-vsam-mq/csd/CRDDEMOM.csd`](../../app/app-vsam-mq/csd/CRDDEMOM.csd)

| Tran ID | Program | BMS mapset | Function | Notes |
|:--|:--|:--|:--|:--|
| [`CDRA`](CDRA.md) | `COACCT01` | - | Inquire account details via MQ | Demonstrates MQ request/response pattern |
| [`CDRD`](CDRD.md) | `CODATE01` | - | Inquire System Date via MQ | Demonstrates MQ request/response pattern |

## Not documented here

- `CDV1` (`COCRDSEC`, "DEVELOPER TRANSACTION - 1") is defined in `app/csd/CARDDEMO.CSD` but is not in the README inventory, so it has no document. TODO: confirm whether it should be documented or removed.
