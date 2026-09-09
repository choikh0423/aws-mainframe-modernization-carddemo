package com.carddemo.mqinquiry.message;

/**
 * MQ-ERR-DISPLAY, the message the inquiry servers put on the error queue before
 * they terminate (COACCT01.cbl:58-67, :501-507):
 *
 * <pre>
 * 05 MQ-ERROR-PARA           PIC X(25)
 * 05 FILLER                  PIC X(02)
 * 05 MQ-APPL-RETURN-MESSAGE  PIC X(25)
 * 05 FILLER                  PIC X(02)
 * 05 MQ-APPL-CONDITION-CODE  PIC 9(02)
 * 05 FILLER                  PIC X(02)
 * 05 MQ-APPL-REASON-CODE     PIC 9(05)
 * 05 FILLER                  PIC X(02)
 * 05 MQ-APPL-QUEUE-NAME      PIC X(48)
 * </pre>
 */
public record MqErrorReport(String paragraph, String returnMessage, int conditionCode, int reasonCode,
        String queueName) {

    private static final int PARAGRAPH_LENGTH = 25;
    private static final int RETURN_MESSAGE_LENGTH = 25;
    private static final int CONDITION_CODE_LENGTH = 2;
    private static final int REASON_CODE_LENGTH = 5;
    private static final int QUEUE_NAME_LENGTH = 48;
    private static final String FILLER = "  ";

    /** The 1000-byte buffer the report is put on the error queue as. */
    public String render() {
        return MqInquiryLayout.message(
                MqInquiryLayout.alphanumeric(paragraph, PARAGRAPH_LENGTH) + FILLER
                        + MqInquiryLayout.alphanumeric(returnMessage, RETURN_MESSAGE_LENGTH) + FILLER
                        + truncatedNumber(conditionCode, CONDITION_CODE_LENGTH) + FILLER
                        + truncatedNumber(reasonCode, REASON_CODE_LENGTH) + FILLER
                        + MqInquiryLayout.alphanumeric(queueName, QUEUE_NAME_LENGTH));
    }

    /** A numeric MOVE into a shorter PIC 9 field keeps the low-order digits. */
    private static String truncatedNumber(int value, int length) {
        String digits = MqInquiryLayout.unsigned(Math.abs((long) value), length);
        return digits.substring(digits.length() - length);
    }
}
