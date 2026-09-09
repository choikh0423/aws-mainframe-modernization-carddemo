package com.carddemo.pendingauth.controller;

import com.carddemo.pendingauth.dto.PendingAuthDetailResponse;
import com.carddemo.pendingauth.dto.PendingAuthListResponse;
import com.carddemo.pendingauth.dto.PendingAuthSelectionRequest;
import com.carddemo.pendingauth.dto.PendingAuthSelectionResponse;
import com.carddemo.pendingauth.service.PendingAuthDetailService;
import com.carddemo.pendingauth.service.PendingAuthListService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * CPVS (COPAUS0C) and CPVD (COPAUS1C).
 *
 * <pre>
 *   GET  /api/pending-authorizations                          ENTER / first display
 *   GET  /api/pending-authorizations?dir=next&amp;startKey=..   PF8
 *   GET  /api/pending-authorizations?dir=prev&amp;startKey=..   PF7
 *   POST /api/pending-authorizations/selection                the SEL0001I..SEL0005I evaluation
 *   GET  /api/pending-authorizations/detail                   CPVD on entry
 *   GET  /api/pending-authorizations/detail/next              CPVD PF8
 *   POST /api/pending-authorizations/detail/fraud             CPVD PF5
 * </pre>
 *
 * <p>Every response carries the legacy message line rather than an HTTP error:
 * the 3270 programs answer a bad account id or an empty selection by
 * redisplaying the map with a message, and the migrated screens do the same.
 */
@RestController
@RequestMapping("/api/pending-authorizations")
public class PendingAuthController {

    private final PendingAuthListService listService;
    private final PendingAuthDetailService detailService;

    public PendingAuthController(PendingAuthListService listService,
                                 PendingAuthDetailService detailService) {
        this.listService = listService;
        this.detailService = detailService;
    }

    @GetMapping
    public PendingAuthListResponse list(
            @RequestParam(name = "acctId", required = false) String acctId,
            @RequestParam(name = "dir", required = false) String dir,
            @RequestParam(name = "startKey", required = false) String startKey,
            @RequestParam(name = "pageNum", required = false, defaultValue = "0") int pageNum) {
        return listService.list(acctId, direction(dir), startKey, pageNum);
    }

    @PostMapping("/selection")
    public PendingAuthSelectionResponse select(@RequestBody PendingAuthSelectionRequest request) {
        return listService.select(request);
    }

    @GetMapping("/detail")
    public PendingAuthDetailResponse detail(
            @RequestParam(name = "acctId", required = false) String acctId,
            @RequestParam(name = "authKey", required = false) String authKey) {
        return detailService.view(acctId, authKey);
    }

    @GetMapping("/detail/next")
    public PendingAuthDetailResponse nextDetail(
            @RequestParam(name = "acctId", required = false) String acctId,
            @RequestParam(name = "authKey", required = false) String authKey) {
        return detailService.next(acctId, authKey);
    }

    @PostMapping("/detail/fraud")
    public PendingAuthDetailResponse markFraud(
            @RequestParam(name = "acctId", required = false) String acctId,
            @RequestParam(name = "authKey", required = false) String authKey) {
        return detailService.markFraud(acctId, authKey);
    }

    private PendingAuthListService.Direction direction(String dir) {
        if ("next".equalsIgnoreCase(dir)) {
            return PendingAuthListService.Direction.NEXT;
        }
        if ("prev".equalsIgnoreCase(dir)) {
            return PendingAuthListService.Direction.PREVIOUS;
        }
        return PendingAuthListService.Direction.FIRST;
    }
}
