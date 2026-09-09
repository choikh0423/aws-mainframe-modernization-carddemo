package com.carddemo.batch.filereads;

/**
 * The four records CBACT01C derives from one account: the OUTFILE record, the ARRYFILE
 * record and the two VBRCFILE records (FR-A7, FR-A10, FR-A11).
 *
 * <p>The first two carry a {@code COMP-3} field and are therefore bytes; the variable
 * records are pure DISPLAY data and are kept as text because the program also prints
 * them (`CBACT01C.cbl:283-284`).
 */
public record AccountExtract(byte[] outRecord, byte[] arrayRecord, String vbrcRec1, String vbrcRec2) {
}
