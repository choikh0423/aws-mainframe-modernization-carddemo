package com.carddemo.mqinquiry.message;

/**
 * REQUEST-MSG-COPY, the request layout both MQ inquiry servers redefine the
 * 1000-byte MQ buffer with (COACCT01.cbl:109-112, CODATE01.cbl:109-112):
 *
 * <pre>
 * 10 WS-FUNC    PIC X(04)
 * 10 WS-KEY     PIC 9(11)
 * 10 WS-FILLER  PIC X(985)
 * </pre>
 *
 * <p>The buffer reaches the copy through a group move (COACCT01.cbl:373), so
 * {@code WS-KEY} holds raw bytes: {@link #key()} keeps them as they arrived and
 * {@link #keyIsPositive()} answers the {@code WS-KEY > ZEROES} test the program
 * makes on them.
 */
public record InquiryRequestMessage(String func, String key, String filler) {

    private static final int FUNC_LENGTH = 4;
    private static final int KEY_LENGTH = 11;
    private static final int FILLER_LENGTH = 985;

    public static InquiryRequestMessage parse(String payload) {
        String buffer = MqInquiryLayout.message(payload);
        return new InquiryRequestMessage(
                buffer.substring(0, FUNC_LENGTH),
                buffer.substring(FUNC_LENGTH, FUNC_LENGTH + KEY_LENGTH),
                buffer.substring(FUNC_LENGTH + KEY_LENGTH, FUNC_LENGTH + KEY_LENGTH + FILLER_LENGTH));
    }

    /** Builds a request buffer the way a requesting program fills the copy. */
    public static String render(String func, long key) {
        return MqInquiryLayout.message(
                MqInquiryLayout.alphanumeric(func, FUNC_LENGTH) + MqInquiryLayout.unsigned(key, KEY_LENGTH));
    }

    /** {@code WS-KEY > ZEROES}; non-numeric key bytes do not pass. */
    public boolean keyIsPositive() {
        return keyAsNumber() > 0;
    }

    /**
     * The key as the numeric value {@code WS-KEY} carries, or 0 when its bytes
     * are not digits — which is also how such a key is echoed back into the
     * invalid-request reply.
     */
    public long keyAsNumber() {
        String digits = key.trim();
        if (digits.isEmpty() || !digits.chars().allMatch(Character::isDigit)) {
            return 0L;
        }
        return Long.parseLong(digits);
    }
}
