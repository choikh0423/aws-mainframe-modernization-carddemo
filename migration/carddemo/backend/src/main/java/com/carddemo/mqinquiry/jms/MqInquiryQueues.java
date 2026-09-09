package com.carddemo.mqinquiry.jms;

/**
 * The queues the MQ inquiry servers use. The reply and error queues are the
 * literals the programs MOVE into their queue names (COACCT01.cbl:198, :294,
 * CODATE01.cbl:147, :243) and are already declared in
 * {@code com.carddemo.common.jms.CardDemoQueues}.
 *
 * <p>The request queues are the CICS trigger queues (MQTM-QNAME), so the
 * programs themselves name no literal; the defaults here are the queue
 * definitions in app/app-vsam-mq/README.md, and both are overridable.
 */
public final class MqInquiryQueues {

    public static final String ACCOUNT_REQUEST_DEFAULT = "CARDDEMO.REQUEST.QUEUE";

    public static final String DATE_REQUEST_DEFAULT = "CARDDEMO.DATE.REQUEST.QUEUE";

    /** Placeholder for the queue CDRA account requests arrive on. */
    public static final String ACCOUNT_REQUEST_PROPERTY =
            "${carddemo.mqinquiry.account-request-queue:" + ACCOUNT_REQUEST_DEFAULT + "}";

    /** Placeholder for the queue CDRD date requests arrive on. */
    public static final String DATE_REQUEST_PROPERTY =
            "${carddemo.mqinquiry.date-request-queue:" + DATE_REQUEST_DEFAULT + "}";

    private MqInquiryQueues() {
    }
}
