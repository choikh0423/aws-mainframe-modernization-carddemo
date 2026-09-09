package com.carddemo.batch.filereads;

/**
 * Boundary B-03: the JDK replacement for the Assembler date utility {@code COBDATFT}
 * (`app/asm/COBDATFT.asm`), called by CBACT01C to reformat the account reissue date.
 *
 * <p>The Assembler holds no business logic — it moves characters between the two
 * layouts of {@code CODATECN-REC} (`app/cpy/CODATECN.cpy`) and sets the error message
 * {@code INVALID INPUT} when the requested conversion is not one it supports. Its
 * exact contract, reproduced here (FR-A13):
 *
 * <ul>
 *   <li>in-type {@code 1} ({@code YYYYMMDD}) with out-type {@code 1} produces
 *       {@code YYYY-MM-DD};</li>
 *   <li>in-type {@code 2} ({@code YYYY-MM-DD}) with out-type {@code 2} produces
 *       {@code YYYYMMDD};</li>
 *   <li>in-type {@code 1} whose 5th character is {@code -}, a crossed type pair, or
 *       any other in-type, yields {@code INVALID INPUT} and no output date
 *       ({@code COBDATFT.asm:30-56}).</li>
 * </ul>
 *
 * <p>Neither branch validates that the characters are digits or that the date exists;
 * the Assembler copies bytes, and so does this class.
 */
public final class LegacyDateFormatter {

    public static final String TYPE_YYYYMMDD = "1";
    public static final String TYPE_YYYY_MM_DD = "2";
    public static final String INVALID_INPUT = "INVALID INPUT";

    /** The 20-byte {@code CODATECN-0UT-DATE} field, blank when the call failed. */
    private static final int OUT_DATE_LENGTH = 20;

    private LegacyDateFormatter() {
    }

    /**
     * Result of one {@code CALL 'COBDATFT'}: the 20-byte output date field and the
     * error message field, exactly as the Assembler leaves them.
     */
    public record Result(String outDate, String errorMessage) {

        public boolean isError() {
            return !errorMessage.isEmpty();
        }
    }

    public static Result convert(String inType, String outType, String inputDate) {
        String input = CobolPicture.text(inputDate, OUT_DATE_LENGTH);
        if (TYPE_YYYYMMDD.equals(inType)) {
            if (input.charAt(4) == '-' || TYPE_YYYY_MM_DD.equals(outType)) {
                return error();
            }
            String formatted = input.substring(0, 4) + "-" + input.substring(4, 6) + "-" + input.substring(6, 8);
            return new Result(CobolPicture.text(formatted, OUT_DATE_LENGTH), "");
        }
        if (TYPE_YYYY_MM_DD.equals(inType)) {
            if (TYPE_YYYYMMDD.equals(outType)) {
                return error();
            }
            String formatted = input.substring(0, 4) + input.substring(5, 7) + input.substring(8, 10);
            return new Result(CobolPicture.text(formatted, OUT_DATE_LENGTH), "");
        }
        return error();
    }

    private static Result error() {
        // The Assembler only sets the error message; the output field keeps its
        // previous (initially blank) content.
        return new Result(" ".repeat(OUT_DATE_LENGTH), INVALID_INPUT);
    }
}
