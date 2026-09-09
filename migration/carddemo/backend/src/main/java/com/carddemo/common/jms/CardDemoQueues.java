package com.carddemo.common.jms;

/**
 * Queue names taken verbatim from the MQ add-on modules, so a stream session
 * never has to re-derive them from the COBOL.
 *
 * <p>The request queues are the triggered queues defined in
 * {@code app/app-vsam-mq/README.md}; the reply and error queues are the literals
 * moved into {@code REPLY-QUEUE-NAME} / {@code ERROR-QUEUE-NAME} by
 * {@code COACCT01} and {@code CODATE01}. On the mainframe the served program
 * learns its input queue from the trigger message ({@code MQTM-QNAME}); under
 * Artemis the equivalent is the destination on the {@code @JmsListener}.
 */
public final class CardDemoQueues {

    /** Triggered request queue for both MQ inquiry servers (S-10). */
    public static final String REQUEST = "CARDDEMO.REQUEST.QUEUE";

    /** Generic response queue defined alongside the request queue. */
    public static final String RESPONSE = "CARDDEMO.RESPONSE.QUEUE";

    /** COACCT01 reply-to queue: account inquiry replies. */
    public static final String REPLY_ACCT = "CARD.DEMO.REPLY.ACCT";

    /** CODATE01 reply-to queue: date inquiry replies. */
    public static final String REPLY_DATE = "CARD.DEMO.REPLY.DATE";

    /** Queue both MQ servers put to when they cannot answer a request. */
    public static final String ERROR = "CARD.DEMO.ERROR";

    private CardDemoQueues() {
    }
}
