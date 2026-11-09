      *> Request block the translated EXEC CICS statements hand to CICSRT.
       01 CICS-ARGS.
          05 CICS-CMD                   PIC X(16).
          05 CICS-P1                    PIC X(08).
          05 CICS-P2                    PIC X(08).
          05 CICS-FLAGS                 PIC X(64).
          05 CICS-DSEP                  PIC X(01).
          05 CICS-TSEP                  PIC X(01).
          05 CICS-KEYLEN                PIC S9(8) COMP-5.
          05 CICS-L1                    PIC S9(8) COMP-5.
          05 CICS-L2                    PIC S9(8) COMP-5.
          05 CICS-L3                    PIC S9(8) COMP-5.
       01 CICS-NULL                     PIC X(01).
