package com.carddemo.trantype.controller;

import com.carddemo.trantype.dto.TranTypeListRequest;
import com.carddemo.trantype.dto.TranTypeListResponse;
import com.carddemo.trantype.service.TranTypeListService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * CTLI — Transaction Type List (COTRTLIC). The screen is pseudo-conversational,
 * so the endpoint is one POST per keystroke: the client sends the AID key, the
 * map fields and the COMMAREA it got back last time, and receives the next map
 * plus the new COMMAREA.
 */
@RestController
public class TranTypeListController {

    private final TranTypeListService listService;

    public TranTypeListController(TranTypeListService listService) {
        this.listService = listService;
    }

    @PostMapping("/api/admin/transaction-types/list")
    public TranTypeListResponse list(@RequestBody TranTypeListRequest request) {
        return listService.handle(request);
    }
}
