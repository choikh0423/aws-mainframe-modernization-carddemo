package com.carddemo.mqinquiry.service;

import com.carddemo.mqinquiry.message.MqErrorReport;

/**
 * What an inquiry server does with a request: either put a 1000-byte reply on
 * its reply queue (COACCT01.cbl:462-486) or put an error report on the error
 * queue and end the task without replying (COACCT01.cbl:437-445, :501-523).
 */
public record MqInquiryResult(String payload, boolean errorReport) {

    public static MqInquiryResult reply(String payload) {
        return new MqInquiryResult(payload, false);
    }

    public static MqInquiryResult error(MqErrorReport report) {
        return new MqInquiryResult(report.render(), true);
    }
}
