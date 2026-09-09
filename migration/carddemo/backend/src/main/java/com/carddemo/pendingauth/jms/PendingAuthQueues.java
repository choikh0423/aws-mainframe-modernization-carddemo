package com.carddemo.pendingauth.jms;

/**
 * The CP00 authorization queues, verbatim from
 * {@code app/app-authorization-ims-db2-mq/README.md:278-279}.
 */
public final class PendingAuthQueues {

    /** Triggered input queue COPAUA0C reads with {@code MQGET}. */
    public static final String REQUEST = "AWS.M2.CARDDEMO.PAUTH.REQUEST";

    /** Default reply destination when the request carries no {@code JMSReplyTo}. */
    public static final String REPLY = "AWS.M2.CARDDEMO.PAUTH.REPLY";

    private PendingAuthQueues() {
    }
}
