package com.carddemo.mqinquiry.service;

import com.carddemo.mqinquiry.message.MqInquiryLayout;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;

/**
 * CDRD / CODATE01: answers every request on its queue with the system date and
 * time (app/app-vsam-mq/cbl/CODATE01.cbl:339-361). The request is parsed into
 * REQUEST-MSG-COPY and then never looked at.
 */
@Service
public class DateInquiryService {

    private static final String DATE_LABEL = "SYSTEM DATE : ";
    private static final String TIME_LABEL = "SYSTEM TIME : ";

    /** FORMATTIME MMDDYYYY DATESEP('-') (CODATE01.cbl:347-350). */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM-dd-uuuu");

    /** FORMATTIME TIME(WS-TIME) TIMESEP (CODATE01.cbl:351-352). */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Clock clock;

    public DateInquiryService() {
        this(Clock.systemDefaultZone());
    }

    DateInquiryService(Clock clock) {
        this.clock = clock;
    }

    /** 4000-PROCESS-REQUEST-REPLY for CDRD. */
    public MqInquiryResult inquire() {
        // ASKTIME ABSTIME then one FORMATTIME: date and time are one reading.
        LocalDateTime now = LocalDateTime.now(clock);
        return MqInquiryResult.reply(MqInquiryLayout.message(
                DATE_LABEL + DATE_FORMAT.format(now) + TIME_LABEL + TIME_FORMAT.format(now)));
    }
}
