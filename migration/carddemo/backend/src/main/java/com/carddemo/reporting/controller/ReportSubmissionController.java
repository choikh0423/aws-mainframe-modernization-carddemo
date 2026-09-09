package com.carddemo.reporting.controller;

import com.carddemo.reporting.dto.ReportSubmitRequest;
import com.carddemo.reporting.dto.ReportSubmitResponse;
import com.carddemo.reporting.service.ReportSubmissionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * CR00 — Transaction Reports (CORPT00C). The screen has exactly one action, the
 * ENTER key, so the stream has exactly one endpoint:
 * {@code POST /api/reports/transactions} carrying the map fields as typed.
 *
 * <p>200 is a completed interaction: the job was submitted (FR-R36) or the
 * operator answered N and the screen was cleared (FR-R31). 400 carries an edit
 * failure with the legacy ERRMSG text and the cursor field; 502 carries the
 * submission failure (FR-R35). PF3 needs no call — the React screen navigates
 * to the menu route, as {@code XCTL COMEN01C} did (FR-R3).
 */
@RestController
public class ReportSubmissionController {

    private final ReportSubmissionService submissionService;

    public ReportSubmissionController(ReportSubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @PostMapping("/api/reports/transactions")
    public ReportSubmitResponse submit(@RequestBody ReportSubmitRequest request) {
        return submissionService.submit(request);
    }
}
