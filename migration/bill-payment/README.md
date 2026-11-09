# Bill Payment (CB00 / COBIL00C): COBOL CICS to Java, side by side

The CardDemo Bill Payment screen (Main Menu option 10) migrated to Spring Boot +
React, and the **original COBOL program running next to it** so the two can be
compared field by field.

```
migration/bill-payment/
  legacy-runtime/   real COBIL00C + COMEN01C, translated and compiled with GnuCOBOL,
                    running in a small CICS region (Python) with VSAM files in SQLite
    translate.py    EXEC CICS ... END-EXEC  ->  CALL 'CICSRT' (the COBOL logic is untouched)
    cicsrt.c        native bridge between the COBOL task and the region
    region/         bms.py (reads app/bms/*.bms), vsam.py, cics.py, parity.py, server.py
    web/            compare.html: 24x80 3270 screen | React screen | parity
    tests/          region tests + legacy-vs-Java parity suite
  backend/          Spring Boot 3.2 + H2 (port 8080)
  frontend/         React 18 Bill Payment screen (served at /app on the region)
  docs/             analysis + migration plan
  run.sh            build all three and start everything
```

Both sides load the same seed files from `app/data/ASCII`.

## Run it

Needs GnuCOBOL 3.1 (`apt-get install gnucobol3 libcob4-dev`), Java 17, Maven, Node 20, Python 3.

```bash
cd migration/bill-payment
./run.sh
# open http://localhost:8090/
```

On the compare page:

- **Left:** the real CICS screen. Type in the green fields; Enter, F3, F4, F5 and Esc (CLEAR) work.
- **Right:** the migrated React screen.
- **Drive both:** sends the same account ID, confirmation and key to both screens.
- **Live screen parity:** compares the fields, message, colour, cursor and next program after every key.
- **On file after this step:** shows the ACCTDAT balance and the newest 350-byte TRANSACT record on each side.
- **CICS trace:** lists every EXEC CICS command the COBOL program ran, with its RESP.
- **Run parity suite:** replays 12 scenarios on both sides.

## Tests

```bash
(cd backend && mvn -B test)                                  # 14 tests
(cd frontend && CI=true npx react-scripts test --watchAll=false)
(cd legacy-runtime && ./build.sh && JAVA_URL=http://localhost:8080 python3 -m unittest -v tests.test_region)
```

CI: `.github/workflows/bill-payment-ci.yml` runs all three. The parity job compiles the COBOL
and fails if any scenario differs.

## Parity rules

- Messages and labels are the COBOL/BMS strings verbatim, including double spaces (`Payment successful.  Your Transaction ID is ...`).
- New TRAN-ID is the highest TRANSACT key + 1. The TRANSACT record must match byte for byte, except the two timestamps, which only need the same `YYYY-MM-DD HH:MM:SS.ffffff` format.
- ACCTDAT must match byte for byte after a payment.
- Known difference: if you type `1` instead of `00000000001`, GnuCOBOL pads the value to `00000000001` when it is moved into `ACCT-ID PIC 9(11)`. IBM Enterprise COBOL keeps the raw bytes, so on z/OS the lookup returns NOTFND. The Java side follows z/OS, and the suite reports this case as `KNOWN`.
- The CICS region implements only what COBIL00C/COMEN01C need. It is a demo harness, not a CICS replacement.
