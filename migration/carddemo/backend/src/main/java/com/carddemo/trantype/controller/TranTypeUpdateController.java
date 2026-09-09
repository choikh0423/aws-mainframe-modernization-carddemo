package com.carddemo.trantype.controller;

import com.carddemo.trantype.dto.TranTypeUpdateRequest;
import com.carddemo.trantype.dto.TranTypeUpdateResponse;
import com.carddemo.trantype.service.TranTypeUpdateService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * CTTU — Transaction Type Update (COTRTUPC). One POST per keystroke, carrying
 * the COMMAREA both ways, exactly as {@link TranTypeListController} does for
 * CTLI.
 */
@RestController
public class TranTypeUpdateController {

    private final TranTypeUpdateService updateService;

    public TranTypeUpdateController(TranTypeUpdateService updateService) {
        this.updateService = updateService;
    }

    @PostMapping("/api/admin/transaction-types/update")
    public TranTypeUpdateResponse update(@RequestBody TranTypeUpdateRequest request) {
        return updateService.handle(request);
    }
}
