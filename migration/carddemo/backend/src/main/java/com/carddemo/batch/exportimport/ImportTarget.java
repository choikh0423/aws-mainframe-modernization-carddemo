package com.carddemo.batch.exportimport;

/**
 * The output files CBIMPORT writes, one per record type plus the error file
 * (CBIMPORT.cbl:36-71, CBIMPORT.jcl:29-62).
 *
 * <p>The DD name is kept as the step-visible identity of each file; the file
 * name follows the legacy DSN ({@code AWS.M2.CARDDEMO.CUSTDATA.IMPORT} ->
 * {@code custdata.import.txt}). The contents are the estate's normalised record
 * layouts, so an output file can be diffed against the matching
 * {@code app/data/ASCII} unload or fed back through the DATALOAD job (S-18).
 *
 * <p>{@link #CARD} has no DD in the shipped CBIMPORT.jcl even though the program
 * opens and writes it - see FR-I-27. The program wins here: the file is
 * produced.
 */
public enum ImportTarget {

    CUSTOMER("CUSTOUT", "custdata.import.txt", 500, "customer"),
    ACCOUNT("ACCTOUT", "acctdata.import.txt", 300, "account"),
    XREF("XREFOUT", "cardxref.import.txt", 50, "xref"),
    TRANSACTION("TRNXOUT", "transact.import.txt", 350, "transaction"),
    CARD("CARDOUT", "carddata.import.txt", 150, "card"),
    ERROR("ERROUT", "import.errors.txt", 132, "error");

    private final String ddName;
    private final String fileName;
    private final int recordLength;
    private final String writeErrorLabel;

    ImportTarget(String ddName, String fileName, int recordLength, String writeErrorLabel) {
        this.ddName = ddName;
        this.fileName = fileName;
        this.recordLength = recordLength;
        this.writeErrorLabel = writeErrorLabel;
    }

    /** The DISPLAY the program issues when a write to this file fails. */
    public String writeErrorMessage(String fileStatus) {
        return "ERROR: Writing " + writeErrorLabel + " record, Status: " + fileStatus;
    }

    public String ddName() {
        return ddName;
    }

    public String fileName() {
        return fileName;
    }

    public int recordLength() {
        return recordLength;
    }
}
